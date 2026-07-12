package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import java.time.Instant;
import java.util.Objects;

public record TimedQueuedBooster(QueuedBooster queuedBooster, Instant startsAt, Instant expiresAt) {

    public TimedQueuedBooster {
        Objects.requireNonNull(queuedBooster, "queuedBooster");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("expiresAt must be after startsAt");
        }
    }
}
