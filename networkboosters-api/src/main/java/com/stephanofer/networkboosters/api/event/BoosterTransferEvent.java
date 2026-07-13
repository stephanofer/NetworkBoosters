package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BoosterTransferEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID transferId;
    private final UUID senderId;
    private final UUID recipientId;
    private final BoosterId boosterId;
    private final long amount;
    private final long senderRevision;
    private final long recipientRevision;
    private final Optional<PlayerBoostSnapshot> senderSnapshot;
    private final Optional<PlayerBoostSnapshot> recipientSnapshot;
    private final BoosterEventOrigin origin;
    private final String sourceServerId;

    public BoosterTransferEvent(
        UUID transferId,
        UUID senderId,
        UUID recipientId,
        BoosterId boosterId,
        long amount,
        long senderRevision,
        long recipientRevision,
        Optional<PlayerBoostSnapshot> senderSnapshot,
        Optional<PlayerBoostSnapshot> recipientSnapshot,
        BoosterEventOrigin origin,
        String sourceServerId
    ) {
        this.transferId = Objects.requireNonNull(transferId, "transferId");
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId");
        this.boosterId = Objects.requireNonNull(boosterId, "boosterId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (senderRevision < 0 || recipientRevision < 0) {
            throw new IllegalArgumentException("revisions cannot be negative");
        }
        this.amount = amount;
        this.senderRevision = senderRevision;
        this.recipientRevision = recipientRevision;
        this.senderSnapshot = Objects.requireNonNull(senderSnapshot, "senderSnapshot");
        this.recipientSnapshot = Objects.requireNonNull(recipientSnapshot, "recipientSnapshot");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.sourceServerId = Objects.requireNonNull(sourceServerId, "sourceServerId");
    }

    public UUID transferId() {
        return this.transferId;
    }

    public UUID senderId() {
        return this.senderId;
    }

    public UUID recipientId() {
        return this.recipientId;
    }

    public BoosterId boosterId() {
        return this.boosterId;
    }

    public long amount() {
        return this.amount;
    }

    public long senderRevision() {
        return this.senderRevision;
    }

    public long recipientRevision() {
        return this.recipientRevision;
    }

    public Optional<PlayerBoostSnapshot> senderSnapshot() {
        return this.senderSnapshot;
    }

    public Optional<PlayerBoostSnapshot> recipientSnapshot() {
        return this.recipientSnapshot;
    }

    public BoosterEventOrigin origin() {
        return this.origin;
    }

    public String sourceServerId() {
        return this.sourceServerId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
