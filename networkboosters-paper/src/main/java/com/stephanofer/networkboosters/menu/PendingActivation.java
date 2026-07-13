package com.stephanofer.networkboosters.menu;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PendingActivation(
    UUID token,
    UUID playerId,
    BoosterId boosterId,
    long sourceRevision,
    int returnPage,
    BoosterMenuFilter returnFilter,
    BoosterMenuSort returnSort,
    Instant expiresAt
) {

    public PendingActivation {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boosterId, "boosterId");
        if (sourceRevision < 0) {
            throw new IllegalArgumentException("sourceRevision cannot be negative");
        }
        if (returnPage < 1) {
            throw new IllegalArgumentException("returnPage must be positive");
        }
        Objects.requireNonNull(returnFilter, "returnFilter");
        Objects.requireNonNull(returnSort, "returnSort");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean expired(Instant now) {
        return !this.expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }
}
