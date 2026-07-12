package com.stephanofer.networkboosters.api.result;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.util.Objects;
import java.util.Optional;

public record InventoryMutationResult(
    InventoryMutationStatus status,
    BoosterId boosterId,
    long previousAmount,
    long newAmount,
    Optional<Long> claimAmount
) {

    public InventoryMutationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(boosterId, "boosterId");
        if (previousAmount < 0 || newAmount < 0) {
            throw new IllegalArgumentException("inventory amounts cannot be negative");
        }
        claimAmount = Objects.requireNonNull(claimAmount, "claimAmount");
        claimAmount.ifPresent(amount -> {
            if (amount <= 0) {
                throw new IllegalArgumentException("claimAmount must be positive");
            }
        });
        if (status == InventoryMutationStatus.CLAIM_CREATED && claimAmount.isEmpty()) {
            throw new IllegalArgumentException("claim-created result requires claimAmount");
        }
        if (status != InventoryMutationStatus.CLAIM_CREATED && claimAmount.isPresent()) {
            throw new IllegalArgumentException("only claim-created result can contain claimAmount");
        }
    }
}
