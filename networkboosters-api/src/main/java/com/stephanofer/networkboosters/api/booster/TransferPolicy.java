package com.stephanofer.networkboosters.api.booster;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record TransferPolicy(
    boolean enabled,
    long minimumAmount,
    long maximumAmount,
    Duration cooldown,
    Optional<String> permission
) {

    public static final TransferPolicy DISABLED = new TransferPolicy(false, 1, 1, Duration.ZERO, Optional.empty());

    public TransferPolicy {
        if (minimumAmount <= 0) {
            throw new IllegalArgumentException("minimumAmount must be positive");
        }
        if (maximumAmount < minimumAmount) {
            throw new IllegalArgumentException("maximumAmount cannot be less than minimumAmount");
        }
        cooldown = Objects.requireNonNull(cooldown, "cooldown");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown cannot be negative");
        }
        permission = normalizePermission(permission);
    }

    private static Optional<String> normalizePermission(Optional<String> rawPermission) {
        Optional<String> optional = Objects.requireNonNull(rawPermission, "permission");
        return optional.map(String::trim).filter(value -> !value.isEmpty());
    }
}
