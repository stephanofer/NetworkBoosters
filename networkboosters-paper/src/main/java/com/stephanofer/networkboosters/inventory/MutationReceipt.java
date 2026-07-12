package com.stephanofer.networkboosters.inventory;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MutationReceipt(
    UUID receiptId,
    String operationType,
    String sourceType,
    String externalReference,
    UUID playerId,
    BoosterId boosterId,
    long amount,
    String result,
    Optional<UUID> claimId
) {

    public MutationReceipt {
        Objects.requireNonNull(receiptId, "receiptId");
        operationType = requireNotBlank(operationType, "operationType");
        sourceType = requireNotBlank(sourceType, "sourceType");
        externalReference = requireNotBlank(externalReference, "externalReference");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boosterId, "boosterId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        result = requireNotBlank(result, "result");
        claimId = Objects.requireNonNull(claimId, "claimId");
    }

    public boolean matches(UUID playerId, BoosterId boosterId, long amount) {
        return this.playerId.equals(playerId) && this.boosterId.equals(boosterId) && this.amount == amount;
    }

    private static String requireNotBlank(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
