package com.stephanofer.networkboosters.api.result;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TransferResult(
    TransferStatus status,
    UUID senderId,
    UUID recipientId,
    BoosterId boosterId,
    long amount,
    Optional<UUID> transferId
) {

    public TransferResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(boosterId, "boosterId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        transferId = Objects.requireNonNull(transferId, "transferId");
        if (status == TransferStatus.TRANSFERRED) {
            if (transferId.isEmpty()) {
                throw new IllegalArgumentException("transferred result requires transferId");
            }
        } else if (transferId.isPresent()) {
            throw new IllegalArgumentException("non-transferred result cannot contain transferId");
        }
    }
}
