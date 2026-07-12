package com.stephanofer.networkboosters.capacity;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class InventoryCapacityResolver {

    public ResolvedInventoryCapacity resolve(
        long fallbackMaximum,
        List<InventoryCapacityRule> rules,
        Predicate<String> permissionChecker
    ) {
        if (fallbackMaximum < 0) {
            throw new IllegalArgumentException("fallbackMaximum cannot be negative");
        }
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(permissionChecker, "permissionChecker");

        Optional<InventoryCapacityRule> winningRule = rules.stream()
            .filter(rule -> rule.permission().map(permissionChecker::test).orElse(true))
            .max(Comparator
                .comparingLong(InventoryCapacityRule::maximum)
                .thenComparingInt(InventoryCapacityRule::priority)
                .thenComparing(InventoryCapacityRule::id));

        return winningRule
            .filter(rule -> rule.maximum() >= fallbackMaximum)
            .map(rule -> new ResolvedInventoryCapacity(rule.maximum(), Optional.of(rule.id()), rule.permission()))
            .orElseGet(() -> ResolvedInventoryCapacity.fallback(fallbackMaximum));
    }

    public boolean canReceive(long currentTotal, long amount, ResolvedInventoryCapacity capacity) {
        if (currentTotal < 0) {
            throw new IllegalArgumentException("currentTotal cannot be negative");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(capacity, "capacity");
        long result;
        try {
            result = Math.addExact(currentTotal, amount);
        } catch (ArithmeticException exception) {
            return false;
        }
        return result <= capacity.maximum();
    }
}
