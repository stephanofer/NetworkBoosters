package com.stephanofer.networkboosters.api.request;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.source.ClaimSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.util.Objects;
import java.util.UUID;

public record ClaimCreationRequest(
    UUID playerId,
    BoosterId boosterId,
    long amount,
    ClaimSource source,
    SourceReference sourceReference
) {

    public ClaimCreationRequest {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boosterId, "boosterId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(source, "source");
        sourceReference = Objects.requireNonNullElse(sourceReference, SourceReference.none());
    }
}
