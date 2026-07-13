package com.stephanofer.networkboosters.booster;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ExpiryWarningTracker {

    private final Map<UUID, Map<UUID, Set<Duration>>> deliveredWarnings = new HashMap<>();

    public synchronized Optional<Duration> nextWarning(
        UUID playerId,
        UUID activationId,
        Duration remaining,
        List<Duration> thresholds
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(activationId, "activationId");
        Objects.requireNonNull(remaining, "remaining");
        Objects.requireNonNull(thresholds, "thresholds");
        if (remaining.isZero() || remaining.isNegative() || thresholds.isEmpty()) {
            return Optional.empty();
        }

        Set<Duration> delivered = this.deliveredWarnings
            .computeIfAbsent(playerId, ignored -> new HashMap<>())
            .computeIfAbsent(activationId, ignored -> new HashSet<>());

        Optional<Duration> selected = thresholds.stream()
            .filter(threshold -> remaining.compareTo(threshold) <= 0)
            .filter(threshold -> !delivered.contains(threshold))
            .min(Duration::compareTo);
        selected.ifPresent(threshold -> thresholds.stream()
            .filter(candidate -> candidate.compareTo(threshold) >= 0)
            .forEach(delivered::add));
        return selected;
    }

    public synchronized void retainActive(UUID playerId, Set<UUID> activeActivationIds) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(activeActivationIds, "activeActivationIds");
        Map<UUID, Set<Duration>> playerWarnings = this.deliveredWarnings.get(playerId);
        if (playerWarnings == null) {
            return;
        }
        playerWarnings.keySet().removeIf(activationId -> !activeActivationIds.contains(activationId));
        if (playerWarnings.isEmpty()) {
            this.deliveredWarnings.remove(playerId);
        }
    }

    public synchronized void clearActivation(UUID playerId, UUID activationId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(activationId, "activationId");
        Map<UUID, Set<Duration>> playerWarnings = this.deliveredWarnings.get(playerId);
        if (playerWarnings == null) {
            return;
        }
        playerWarnings.remove(activationId);
        if (playerWarnings.isEmpty()) {
            this.deliveredWarnings.remove(playerId);
        }
    }

    public synchronized void clearPlayer(UUID playerId) {
        this.deliveredWarnings.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public synchronized void clear() {
        this.deliveredWarnings.clear();
    }
}
