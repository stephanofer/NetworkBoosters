package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class ExpirationEvaluator {

    public boolean expired(ActiveBooster booster, Instant now) {
        return !Objects.requireNonNull(booster, "booster").expiresAt().isAfter(Objects.requireNonNull(now, "now"));
    }

    public Duration remaining(ActiveBooster booster, Instant now) {
        if (expired(booster, now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, booster.expiresAt());
    }
}
