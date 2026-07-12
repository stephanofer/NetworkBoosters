package com.stephanofer.networkboosters.api.booster;

import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ActiveBooster(
    UUID activationId,
    UUID playerId,
    BoosterId boosterId,
    BoosterTarget target,
    BigDecimal multiplier,
    ActivationGroup activationGroup,
    ConflictPolicy conflictPolicy,
    BoosterScope scope,
    ActivationRequirements requirements,
    Instant activatedAt,
    Instant expiresAt,
    ActivationSource source,
    SourceReference sourceReference
) {

    public ActiveBooster {
        Objects.requireNonNull(activationId, "activationId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boosterId, "boosterId");
        Objects.requireNonNull(target, "target");
        multiplier = normalizePositiveMultiplier(multiplier);
        Objects.requireNonNull(activationGroup, "activationGroup");
        Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        Objects.requireNonNull(scope, "scope");
        requirements = Objects.requireNonNullElse(requirements, ActivationRequirements.NONE);
        Objects.requireNonNull(activatedAt, "activatedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(source, "source");
        sourceReference = Objects.requireNonNullElse(sourceReference, SourceReference.none());
        if (!expiresAt.isAfter(activatedAt)) {
            throw new IllegalArgumentException("expiresAt must be after activatedAt");
        }
    }

    public boolean isActiveAt(Instant now) {
        return expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }

    private static BigDecimal normalizePositiveMultiplier(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "multiplier").stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("multiplier must be positive");
        }
        return normalized;
    }
}
