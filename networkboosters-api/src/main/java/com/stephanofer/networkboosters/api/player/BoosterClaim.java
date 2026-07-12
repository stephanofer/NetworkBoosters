package com.stephanofer.networkboosters.api.player;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.source.ClaimSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BoosterClaim(
    UUID claimId,
    UUID playerId,
    BoosterId boosterId,
    long amount,
    ClaimSource source,
    SourceReference sourceReference,
    Instant createdAt,
    Optional<Instant> claimedAt,
    ClaimStatus status
) {

    public BoosterClaim {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boosterId, "boosterId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(source, "source");
        sourceReference = Objects.requireNonNullElse(sourceReference, SourceReference.none());
        Objects.requireNonNull(createdAt, "createdAt");
        claimedAt = Objects.requireNonNull(claimedAt, "claimedAt");
        Objects.requireNonNull(status, "status");
        if (status == ClaimStatus.PENDING && claimedAt.isPresent()) {
            throw new IllegalArgumentException("pending claims cannot have claimedAt");
        }
        if (status == ClaimStatus.CLAIMED && claimedAt.isEmpty()) {
            throw new IllegalArgumentException("claimed claims require claimedAt");
        }
    }
}
