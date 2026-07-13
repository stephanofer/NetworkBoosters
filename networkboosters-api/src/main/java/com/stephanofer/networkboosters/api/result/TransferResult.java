package com.stephanofer.networkboosters.api.result;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public record TransferResult(
    TransferStatus status,
    UUID senderId,
    UUID recipientId,
    BoosterId boosterId,
    long amount,
    Optional<UUID> transferId,
    Optional<Instant> retryAt,
    OptionalLong senderRemainingAmount,
    OptionalLong recipientResultingAmount
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
        retryAt = Objects.requireNonNull(retryAt, "retryAt");
        senderRemainingAmount = Objects.requireNonNull(senderRemainingAmount, "senderRemainingAmount");
        recipientResultingAmount = Objects.requireNonNull(recipientResultingAmount, "recipientResultingAmount");
        if (status == TransferStatus.TRANSFERRED) {
            if (transferId.isEmpty()) {
                throw new IllegalArgumentException("transferred result requires transferId");
            }
            if (senderRemainingAmount.isEmpty() || recipientResultingAmount.isEmpty()) {
                throw new IllegalArgumentException("transferred result requires resulting inventory amounts");
            }
        } else if (transferId.isPresent()) {
            throw new IllegalArgumentException("non-transferred result cannot contain transferId");
        }
        if (status == TransferStatus.COOLDOWN) {
            if (retryAt.isEmpty()) {
                throw new IllegalArgumentException("cooldown result requires retryAt");
            }
        } else if (retryAt.isPresent()) {
            throw new IllegalArgumentException("only cooldown result can contain retryAt");
        }
        if (senderRemainingAmount.isPresent() && senderRemainingAmount.getAsLong() < 0) {
            throw new IllegalArgumentException("senderRemainingAmount cannot be negative");
        }
        if (recipientResultingAmount.isPresent() && recipientResultingAmount.getAsLong() < 0) {
            throw new IllegalArgumentException("recipientResultingAmount cannot be negative");
        }
    }
}
