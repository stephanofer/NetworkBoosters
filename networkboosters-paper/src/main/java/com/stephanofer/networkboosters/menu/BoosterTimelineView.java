package com.stephanofer.networkboosters.menu;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record BoosterTimelineView(
    BoosterId boosterId,
    ActivationGroup group,
    BigDecimal multiplier,
    BoosterScope scope,
    boolean active,
    long position,
    Duration remainingUntilStart,
    Duration remainingUntilEnd,
    Duration duration
) {

    public BoosterTimelineView {
        Objects.requireNonNull(boosterId, "boosterId");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(multiplier, "multiplier");
        Objects.requireNonNull(scope, "scope");
        if (position < 0) {
            throw new IllegalArgumentException("position cannot be negative");
        }
        remainingUntilStart = nonNegative(remainingUntilStart);
        remainingUntilEnd = nonNegative(remainingUntilEnd);
        duration = nonNegative(duration);
    }

    private static Duration nonNegative(Duration value) {
        Objects.requireNonNull(value, "duration");
        return value.isNegative() ? Duration.ZERO : value;
    }
}
