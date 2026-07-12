package com.stephanofer.networkboosters.api.calculation;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record AppliedBoost(
    UUID activationId,
    BoosterId boosterId,
    ActivationGroup activationGroup,
    BigDecimal multiplier
) {

    public AppliedBoost {
        Objects.requireNonNull(activationId, "activationId");
        Objects.requireNonNull(boosterId, "boosterId");
        Objects.requireNonNull(activationGroup, "activationGroup");
        multiplier = Objects.requireNonNull(multiplier, "multiplier").stripTrailingZeros();
        if (multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("multiplier must be positive");
        }
    }
}
