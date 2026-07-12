package com.stephanofer.networkboosters.api.request;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.api.source.TransferSource;
import java.util.Objects;
import java.util.UUID;

public record BoosterTransferRequest(
    UUID senderId,
    UUID recipientId,
    BoosterId boosterId,
    long amount,
    TransferSource source,
    SourceReference sourceReference
) {

    public BoosterTransferRequest {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(boosterId, "boosterId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(source, "source");
        sourceReference = Objects.requireNonNullElse(sourceReference, SourceReference.none());
    }
}
