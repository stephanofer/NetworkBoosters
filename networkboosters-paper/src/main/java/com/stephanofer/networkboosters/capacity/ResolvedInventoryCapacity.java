package com.stephanofer.networkboosters.capacity;

import java.util.Objects;
import java.util.Optional;

public record ResolvedInventoryCapacity(
    long maximum,
    Optional<String> ruleId,
    Optional<String> permission
) {

    public ResolvedInventoryCapacity {
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum cannot be negative");
        }
        ruleId = Objects.requireNonNull(ruleId, "ruleId").map(String::trim).filter(value -> !value.isEmpty());
        permission = Objects.requireNonNull(permission, "permission").map(String::trim).filter(value -> !value.isEmpty());
    }

    public static ResolvedInventoryCapacity fallback(long maximum) {
        return new ResolvedInventoryCapacity(maximum, Optional.empty(), Optional.empty());
    }
}
