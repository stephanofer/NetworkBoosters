package com.stephanofer.networkboosters.capacity;

import java.util.Objects;
import java.util.Optional;

public record InventoryCapacityRule(
    String id,
    Optional<String> permission,
    long maximum,
    int priority
) {

    public InventoryCapacityRule {
        id = Objects.requireNonNull(id, "id").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id cannot be empty");
        }
        permission = Objects.requireNonNull(permission, "permission").map(String::trim).filter(value -> !value.isEmpty());
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum cannot be negative");
        }
    }
}
