package com.stephanofer.networkboosters.api.booster;

import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record QueuedBooster(
    UUID queueId,
    UUID playerId,
    BoosterId boosterId,
    BoosterTarget target,
    BigDecimal multiplier,
    ActivationGroup activationGroup,
    ConflictPolicy conflictPolicy,
    BoosterScope scope,
    ActivationRequirements requirements,
    Duration duration,
    Instant queuedAt,
    ActivationSource source,
    SourceReference sourceReference,
    long position
) {

    public QueuedBooster {
        Objects.requireNonNull(queueId, "queueId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boosterId, "boosterId");
        Objects.requireNonNull(target, "target");
        multiplier = normalizePositiveMultiplier(multiplier);
        Objects.requireNonNull(activationGroup, "activationGroup");
        Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        Objects.requireNonNull(scope, "scope");
        requirements = Objects.requireNonNullElse(requirements, ActivationRequirements.NONE);
        duration = normalizeDuration(duration);
        Objects.requireNonNull(queuedAt, "queuedAt");
        Objects.requireNonNull(source, "source");
        sourceReference = Objects.requireNonNullElse(sourceReference, SourceReference.none());
        if (position < 0) {
            throw new IllegalArgumentException("position cannot be negative");
        }
    }

    private static BigDecimal normalizePositiveMultiplier(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "multiplier").stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("multiplier must be positive");
        }
        return normalized;
    }

    private static Duration normalizeDuration(Duration value) {
        Duration duration = Objects.requireNonNull(value, "duration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        return duration;
    }
}
