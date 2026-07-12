package com.stephanofer.networkboosters.api.booster;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record BoosterDefinition(
    BoosterId id,
    BoosterTarget target,
    BigDecimal multiplier,
    Duration duration,
    BoosterScope scope,
    ActivationGroup activationGroup,
    ConflictPolicy conflictPolicy,
    ActivationRequirements requirements,
    TransferPolicy transferPolicy,
    boolean enabled,
    int displayOrder,
    BoosterCategory category
) {

    public BoosterDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        multiplier = normalizeMultiplier(multiplier);
        duration = normalizeDuration(duration);
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(activationGroup, "activationGroup");
        Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        requirements = Objects.requireNonNullElse(requirements, ActivationRequirements.NONE);
        Objects.requireNonNull(transferPolicy, "transferPolicy");
        Objects.requireNonNull(category, "category");
    }

    private static BigDecimal normalizeMultiplier(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "multiplier").stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("multiplier must be greater than or equal to 1");
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
