package com.stephanofer.networkboosters.api.result;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import java.util.Objects;
import java.util.Optional;

public record DeactivationResult(DeactivationStatus status, Optional<ActiveBooster> deactivatedBooster) {

    public DeactivationResult {
        Objects.requireNonNull(status, "status");
        deactivatedBooster = Objects.requireNonNull(deactivatedBooster, "deactivatedBooster");
        if (status == DeactivationStatus.DEACTIVATED) {
            if (deactivatedBooster.isEmpty()) {
                throw new IllegalArgumentException("deactivated result requires booster");
            }
        } else if (deactivatedBooster.isPresent()) {
            throw new IllegalArgumentException("non-deactivated result cannot contain booster");
        }
    }
}
