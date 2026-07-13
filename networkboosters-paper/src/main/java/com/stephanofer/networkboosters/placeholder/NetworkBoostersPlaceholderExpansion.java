package com.stephanofer.networkboosters.placeholder;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.calculation.BoostRequest;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.command.NetworkBoostersCommandRuntime;
import com.stephanofer.networkboosters.localization.DurationFormatter;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class NetworkBoostersPlaceholderExpansion extends PlaceholderExpansion {

    private final NetworkBoostersCommandRuntime runtime;
    private final DurationFormatter durations = new DurationFormatter();

    public NetworkBoostersPlaceholderExpansion(NetworkBoostersCommandRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "networkboosters";
    }

    @Override
    public @NotNull String getAuthor() {
        return "stephanofer";
    }

    @Override
    public @NotNull String getVersion() {
        return this.runtime.plugin().getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null || params.isBlank()) {
            return "";
        }
        Player player = offlinePlayer.getPlayer();
        PlayerBoostSnapshot snapshot = this.runtime.service().getCachedOrEmpty(offlinePlayer.getUniqueId());
        String normalized = params.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("ready")) {
            return String.valueOf(this.runtime.service().isReady(offlinePlayer.getUniqueId()));
        }
        if (!this.runtime.service().isReady(offlinePlayer.getUniqueId())) {
            return neutral(normalized);
        }
        if (normalized.equals("capacity")) {
            if (player == null || !player.isOnline()) {
                return snapshot.ownedTotal() + "/0";
            }
            return snapshot.ownedTotal() + "/" + this.runtime.capacity(player).maximum();
        }
        if (normalized.equals("owned_total")) {
            return String.valueOf(snapshot.ownedTotal());
        }
        if (normalized.startsWith("owned_")) {
            return owned(snapshot, normalized.substring("owned_".length()));
        }
        if (normalized.equals("active_count")) {
            Instant now = this.runtime.clock().instant();
            return String.valueOf(snapshot.activeBoosters().values().stream().filter(active -> active.isActiveAt(now)).count());
        }
        if (normalized.equals("active_ids")) {
            Instant now = this.runtime.clock().instant();
            return String.join(",", snapshot.activeBoosters().values().stream()
                .filter(active -> active.isActiveAt(now))
                .map(active -> active.boosterId().value())
                .toList());
        }
        if (normalized.startsWith("active_")) {
            String target = decodeTarget(normalized.substring("active_".length()));
            Instant now = this.runtime.clock().instant();
            return String.valueOf(snapshot.activeBoosters().values().stream().anyMatch(active -> active.isActiveAt(now) && active.target().key().equals(target)));
        }
        if (normalized.startsWith("multiplier_")) {
            return multiplier(offlinePlayer, normalized.substring("multiplier_".length()));
        }
        if (normalized.startsWith("time_remaining_")) {
            return timeRemaining(player, snapshot, normalized.substring("time_remaining_".length()));
        }
        if (normalized.startsWith("seconds_remaining_")) {
            return secondsRemaining(snapshot, normalized.substring("seconds_remaining_".length()));
        }
        if (normalized.startsWith("queue_size_")) {
            return queueSize(snapshot, normalized.substring("queue_size_".length()));
        }
        if (normalized.equals("claims_count")) {
            return String.valueOf(snapshot.pendingClaims().size());
        }
        return null;
    }

    private static String neutral(String normalized) {
        if (normalized.equals("capacity")) {
            return "0/0";
        }
        if (normalized.startsWith("multiplier_")) {
            return "1";
        }
        if (normalized.startsWith("active_")) {
            return "false";
        }
        if (normalized.startsWith("time_remaining_") || normalized.equals("active_ids")) {
            return "";
        }
        return "0";
    }

    private static String owned(PlayerBoostSnapshot snapshot, String rawBoosterId) {
        try {
            return String.valueOf(snapshot.ownedAmount(new BoosterId(rawBoosterId)));
        } catch (IllegalArgumentException exception) {
            return "0";
        }
    }

    private String multiplier(OfflinePlayer player, String rawTarget) {
        try {
            String target = decodeTarget(rawTarget);
            return this.runtime.service().calculate(BoostRequest.of(
                player.getUniqueId(),
                new BoosterTarget(target),
                BigDecimal.ONE,
                this.runtime.configurationStore().requireCurrent().configuration().gameId(),
                this.runtime.configurationStore().requireCurrent().configuration().serverId()
            )).multiplier().toPlainString();
        } catch (IllegalArgumentException exception) {
            return "1";
        }
    }

    private String timeRemaining(Player player, PlayerBoostSnapshot snapshot, String rawBoosterId) {
        long seconds = remainingSeconds(snapshot, rawBoosterId);
        if (seconds <= 0) {
            return "";
        }
        String language = player == null ? this.runtime.configurationStore().requireCurrent().localization().fallbackLanguage() : this.runtime.localization().language(player);
        return this.durations.format(Duration.ofSeconds(seconds), this.runtime.configurationStore().requireCurrent().localization(), language);
    }

    private String secondsRemaining(PlayerBoostSnapshot snapshot, String rawBoosterId) {
        return String.valueOf(remainingSeconds(snapshot, rawBoosterId));
    }

    private long remainingSeconds(PlayerBoostSnapshot snapshot, String rawBoosterId) {
        Instant now = this.runtime.clock().instant();
        return snapshot.activeBoosters().values().stream()
            .filter(active -> active.boosterId().value().equals(rawBoosterId))
            .filter(active -> active.isActiveAt(now))
            .map(ActiveBooster::expiresAt)
            .mapToLong(expiresAt -> Math.max(0, Duration.between(now, expiresAt).getSeconds()))
            .max()
            .orElse(0);
    }

    private static String queueSize(PlayerBoostSnapshot snapshot, String rawGroup) {
        try {
            return String.valueOf(snapshot.queuedBoosters().getOrDefault(new ActivationGroup(rawGroup), java.util.List.of()).size());
        } catch (IllegalArgumentException exception) {
            return "0";
        }
    }

    private static String decodeTarget(String raw) {
        return raw.replace("__", ":");
    }
}
