package com.stephanofer.networkboosters.api.result;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.player.BoosterClaim;
import java.util.Objects;
import java.util.Optional;

public record InventoryMutationResult(
    InventoryMutationStatus status,
    BoosterId boosterId,
    long previousAmount,
    long newAmount,
    Optional<BoosterClaim> claim
) {

    public InventoryMutationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(boosterId, "boosterId");
        if (previousAmount < 0 || newAmount < 0) {
            throw new IllegalArgumentException("inventory amounts cannot be negative");
        }
        claim = Objects.requireNonNull(claim, "claim");
        if (status == InventoryMutationStatus.CLAIM_CREATED && claim.isEmpty()) {
            throw new IllegalArgumentException("claim-created result requires claim");
        }
        if (status != InventoryMutationStatus.CLAIM_CREATED && claim.isPresent()) {
            throw new IllegalArgumentException("only claim-created result can contain claim");
        }
        if (status == InventoryMutationStatus.UNCHANGED && previousAmount != newAmount) {
            throw new IllegalArgumentException("unchanged result requires equal amounts");
        }
    }
}
