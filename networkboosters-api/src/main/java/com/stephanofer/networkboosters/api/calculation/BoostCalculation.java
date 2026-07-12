package com.stephanofer.networkboosters.api.calculation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record BoostCalculation(
    BigDecimal baseAmount,
    BigDecimal multiplier,
    BigDecimal finalAmount,
    List<AppliedBoost> appliedBoosts,
    boolean capped
) {

    public static BoostCalculation neutral(BigDecimal baseAmount) {
        BigDecimal base = Objects.requireNonNull(baseAmount, "baseAmount");
        return new BoostCalculation(base, BigDecimal.ONE, base, List.of(), false);
    }

    public BoostCalculation {
        Objects.requireNonNull(baseAmount, "baseAmount");
        multiplier = Objects.requireNonNull(multiplier, "multiplier").stripTrailingZeros();
        Objects.requireNonNull(finalAmount, "finalAmount");
        appliedBoosts = List.copyOf(Objects.requireNonNull(appliedBoosts, "appliedBoosts"));
        if (multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("multiplier must be positive");
        }
    }

    public boolean boosted() {
        return !appliedBoosts.isEmpty();
    }
}
