package com.stephanofer.networkboosters.api.result;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import java.util.Objects;
import java.util.Optional;

public record DeactivationResult(
    DeactivationStatus status,
    Optional<ActiveBooster> deactivatedBooster,
    Optional<ActiveBooster> promotedBooster
) {

    public DeactivationResult {
        Objects.requireNonNull(status, "status");
        deactivatedBooster = Objects.requireNonNull(deactivatedBooster, "deactivatedBooster");
        promotedBooster = Objects.requireNonNull(promotedBooster, "promotedBooster");
        if (status == DeactivationStatus.DEACTIVATED || status == DeactivationStatus.EXPIRED) {
            if (deactivatedBooster.isEmpty()) {
                throw new IllegalArgumentException("deactivation result requires booster");
            }
        } else if (deactivatedBooster.isPresent()) {
            throw new IllegalArgumentException("inactive result cannot contain booster");
        }
    }
}
