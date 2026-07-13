package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.NetworkBoostersService;
import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.event.BoosterDeactivateEvent;
import com.stephanofer.networkboosters.api.event.BoosterExpireEvent;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.localization.DurationFormatter;
import com.stephanofer.networkboosters.localization.LocalizationService;
import com.stephanofer.networkboosters.localization.MessageArguments;
import com.stephanofer.networkboosters.localization.MessageKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ExpiryWarningCoordinator implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final ComponentLogger logger;
    private final ConfigurationStore configurationStore;
    private final NetworkBoostersService service;
    private final LocalizationService localization;
    private final Clock clock;
    private final Consumer<Player> menuUpdater;
    private final ExpiryWarningTracker tracker = new ExpiryWarningTracker();
    private final DurationFormatter durations = new DurationFormatter();

    private BukkitTask task;

    public ExpiryWarningCoordinator(
        Plugin plugin,
        ComponentLogger logger,
        ConfigurationStore configurationStore,
        NetworkBoostersService service,
        LocalizationService localization,
        Clock clock,
        Consumer<Player> menuUpdater
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.configurationStore = Objects.requireNonNull(configurationStore, "configurationStore");
        this.service = Objects.requireNonNull(service, "service");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.menuUpdater = Objects.requireNonNull(menuUpdater, "menuUpdater");
    }

    public void start() {
        if (this.task != null) {
            throw new IllegalStateException("Expiry warning coordinator is already started");
        }
        long intervalTicks = Math.max(1L, this.configurationStore.requireCurrent().configuration().activation().expiryCheckInterval().toMillis() / 50L);
        this.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 20L, intervalTicks);
    }

    private void tick() {
        List<Duration> thresholds = this.configurationStore.requireCurrent().configuration().activation().expiryWarnings();
        if (thresholds.isEmpty()) {
            return;
        }
        Instant now = this.clock.instant();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            this.tickPlayer(player, now, thresholds);
        }
    }

    private void tickPlayer(Player player, Instant now, List<Duration> thresholds) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || !this.service.isReady(playerId)) {
            this.tracker.clearPlayer(playerId);
            return;
        }
        PlayerBoostSnapshot snapshot = this.service.cached(playerId).orElse(null);
        if (snapshot == null) {
            this.tracker.clearPlayer(playerId);
            return;
        }
        HashSet<UUID> activeActivationIds = new HashSet<>();
        for (ActiveBooster active : snapshot.activeBoosters().values()) {
            activeActivationIds.add(active.activationId());
            Duration remaining = Duration.between(now, active.expiresAt());
            this.tracker.nextWarning(playerId, active.activationId(), remaining, thresholds)
                .ifPresent(threshold -> this.sendWarning(player, active, remaining));
        }
        this.tracker.retainActive(playerId, activeActivationIds);
    }

    private void sendWarning(Player player, ActiveBooster active, Duration remaining) {
        try {
            player.sendMessage(this.localization.message(player, MessageKey.ACTIVATION_EXPIRY_WARNING,
                MessageArguments.component("name", this.localization.boosterName(player, active.boosterId().value())),
                MessageArguments.text("remaining", this.format(player, remaining)),
                MessageArguments.text("multiplier", active.multiplier().toPlainString())));
            this.menuUpdater.accept(player);
        } catch (RuntimeException exception) {
            this.logger.warn("Failed to deliver NetworkBoosters expiry warning to {}", player.getUniqueId(), exception);
        }
    }

    @EventHandler
    public void onExpire(BoosterExpireEvent event) {
        Player player = this.plugin.getServer().getPlayer(event.playerId());
        if (player == null || !player.isOnline()) {
            this.tracker.clearPlayer(event.playerId());
            return;
        }
        event.expiredActiveBooster().ifPresent(active -> {
            this.tracker.clearActivation(event.playerId(), active.activationId());
            player.sendMessage(this.localization.message(player, MessageKey.ACTIVATION_EXPIRED,
                MessageArguments.component("name", this.localization.boosterName(player, active.boosterId().value()))));
        });
    }

    @EventHandler
    public void onDeactivate(BoosterDeactivateEvent event) {
        this.tracker.clearActivation(event.playerId(), event.deactivatedBooster().activationId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.tracker.clearPlayer(event.getPlayer().getUniqueId());
    }

    private String format(Player player, Duration duration) {
        return this.durations.format(duration, this.configurationStore.requireCurrent().localization(), this.localization.language(player));
    }

    @Override
    public void close() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.tracker.clear();
    }
}
