package com.stephanofer.networkboosters.menu;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PendingTransfer(
    UUID token,
    UUID senderId,
    UUID recipientId,
    BoosterId boosterId,
    long amount,
    long senderRevision,
    long recipientRevision,
    int returnPage,
    BoosterMenuFilter returnFilter,
    BoosterMenuSort returnSort,
    Instant expiresAt
) {

    public PendingTransfer {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(boosterId, "boosterId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (senderRevision < 0 || recipientRevision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
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

    public PendingTransfer withAmount(long newAmount) {
        return new PendingTransfer(token, senderId, recipientId, boosterId, newAmount, senderRevision, recipientRevision, returnPage, returnFilter, returnSort, expiresAt);
    }
}
