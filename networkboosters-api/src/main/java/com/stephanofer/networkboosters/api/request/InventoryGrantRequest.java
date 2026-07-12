package com.stephanofer.networkboosters.api.request;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.source.MutationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.util.Objects;
import java.util.UUID;

public record InventoryGrantRequest(
    UUID playerId,
    BoosterId boosterId,
    long amount,
    MutationSource source,
    SourceReference sourceReference,
    boolean force
) {

    public InventoryGrantRequest {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boosterId, "boosterId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(source, "source");
        sourceReference = Objects.requireNonNullElse(sourceReference, SourceReference.none());
    }
}
