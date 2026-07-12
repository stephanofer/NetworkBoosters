package com.stephanofer.networkboosters.api.result;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import java.util.Objects;
import java.util.Optional;

public record ActivationResult(
    ActivationStatus status,
    Optional<ActiveBooster> activeBooster,
    Optional<QueuedBooster> queuedBooster,
    long remainingInventoryAmount
) {

    public ActivationResult {
        Objects.requireNonNull(status, "status");
        activeBooster = Objects.requireNonNull(activeBooster, "activeBooster");
        queuedBooster = Objects.requireNonNull(queuedBooster, "queuedBooster");
        if (remainingInventoryAmount < 0) {
            throw new IllegalArgumentException("remainingInventoryAmount cannot be negative");
        }
        switch (status) {
            case ACTIVATED, EXTENDED, REPLACED -> {
                requirePresent(activeBooster, "activeBooster");
                requireEmpty(queuedBooster, "queuedBooster");
            }
            case QUEUED, QUEUE_MERGED -> {
                requireEmpty(activeBooster, "activeBooster");
                requirePresent(queuedBooster, "queuedBooster");
            }
            default -> {
                requireEmpty(activeBooster, "activeBooster");
                requireEmpty(queuedBooster, "queuedBooster");
            }
        }
    }

    public static ActivationResult rejected(ActivationStatus status, long remainingInventoryAmount) {
        return new ActivationResult(status, Optional.empty(), Optional.empty(), remainingInventoryAmount);
    }

    private static void requirePresent(Optional<?> value, String label) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " is required for this status");
        }
    }

    private static void requireEmpty(Optional<?> value, String label) {
        if (value.isPresent()) {
            throw new IllegalArgumentException(label + " must be empty for this status");
        }
    }
}
