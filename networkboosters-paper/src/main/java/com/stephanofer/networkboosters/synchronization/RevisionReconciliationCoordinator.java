package com.stephanofer.networkboosters.synchronization;

import com.hera.craftkit.redis.RedisOperationalState;
import com.hera.craftkit.redis.RedisOperationalStatus;
import com.hera.craftkit.redis.RedisStatusRegistration;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class RevisionReconciliationCoordinator implements AutoCloseable {

    private final Plugin plugin;
    private final PlayerSnapshotCache snapshots;
    private final PlayerRevisionReader revisions;
    private final PlayerInvalidationCoordinator invalidations;
    private final ConfigurationStore configurationStore;
    private final ComponentLogger logger;
    private final String serverId;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Duration interval;
    private BukkitTask task;
    private RedisStatusRegistration statusRegistration;

    public RevisionReconciliationCoordinator(
        Plugin plugin,
        PlayerSnapshotCache snapshots,
        PlayerRevisionReader revisions,
        PlayerInvalidationCoordinator invalidations,
        ConfigurationStore configurationStore,
        ComponentLogger logger,
        String serverId
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.invalidations = Objects.requireNonNull(invalidations, "invalidations");
        this.configurationStore = Objects.requireNonNull(configurationStore, "configurationStore");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.interval = configurationStore.requireCurrent().configuration().redis().reconciliationInterval();
    }

    public void start() {
        this.reschedule(this.interval);
    }

    public void observe(com.hera.craftkit.redis.RedisClient redis) {
        if (redis == null) {
            this.reschedule(this.configurationStore.requireCurrent().configuration().redis().degradedReconciliationInterval());
            return;
        }
        this.statusRegistration = redis.observeOperationalStatus(this::onRedisStatus);
    }

    private void onRedisStatus(RedisOperationalStatus status) {
        Duration next = status.state() == RedisOperationalState.OPERATIONAL
            ? this.configurationStore.requireCurrent().configuration().redis().reconciliationInterval()
            : this.configurationStore.requireCurrent().configuration().redis().degradedReconciliationInterval();
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            this.reschedule(next);
            if (status.state() == RedisOperationalState.OPERATIONAL) {
                this.tick();
            }
        });
    }

    private void reschedule(Duration interval) {
        this.interval = interval;
        if (this.task != null) {
            this.task.cancel();
        }
        long ticks = Math.max(1L, interval.toMillis() / 50L);
        this.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, ticks, ticks);
    }

    private void tick() {
        if (!this.running.compareAndSet(false, true)) {
            return;
        }
        var players = this.snapshots.loadedPlayers();
        if (players.isEmpty()) {
            this.running.set(false);
            return;
        }
        this.revisions.revisions(players).whenComplete((persisted, failure) -> {
            this.running.set(false);
            if (failure != null) {
                this.logger.warn("Failed to reconcile NetworkBoosters player revisions", failure);
                return;
            }
            for (Map.Entry<UUID, Long> entry : persisted.entrySet()) {
                this.invalidations.reconcile(entry.getKey(), entry.getValue(), this.serverId);
            }
        });
    }

    @Override
    public void close() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        if (this.statusRegistration != null) {
            this.statusRegistration.close();
            this.statusRegistration = null;
        }
    }
}
