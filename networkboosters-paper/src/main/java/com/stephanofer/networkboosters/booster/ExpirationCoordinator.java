package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.config.ConfigurationStore;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ExpirationCoordinator implements AutoCloseable {

    private final Plugin plugin;
    private final ComponentLogger logger;
    private final ConfigurationStore configurationStore;
    private final ActivationMutationService activations;
    private final AtomicBoolean running = new AtomicBoolean();
    private BukkitTask task;

    public ExpirationCoordinator(
        Plugin plugin,
        ComponentLogger logger,
        ConfigurationStore configurationStore,
        ActivationMutationService activations
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.configurationStore = Objects.requireNonNull(configurationStore, "configurationStore");
        this.activations = Objects.requireNonNull(activations, "activations");
    }

    public void start() {
        if (this.task != null) {
            throw new IllegalStateException("Expiration coordinator is already started");
        }
        long intervalTicks = Math.max(1L, this.configurationStore.requireCurrent().configuration().activation().expiryCheckInterval().toMillis() / 50L);
        this.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 1L, intervalTicks);
    }

    private void tick() {
        if (!this.running.compareAndSet(false, true)) {
            return;
        }
        int batchSize = this.configurationStore.requireCurrent().configuration().activation().expiryBatchSize();
        this.activations.expireDueActivations(batchSize).whenComplete((processed, failure) -> {
            this.running.set(false);
            if (failure != null) {
                this.logger.warn("Failed to process NetworkBoosters expirations", failure);
            }
        });
    }

    @Override
    public void close() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }
}
