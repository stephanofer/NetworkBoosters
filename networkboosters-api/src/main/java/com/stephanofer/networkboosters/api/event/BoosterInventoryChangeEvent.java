package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.event.HandlerList;

public final class BoosterInventoryChangeEvent extends AbstractPlayerBoostersEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BoosterId boosterId;
    private final long previousAmount;
    private final long newAmount;
    private final long delta;
    private final InventoryChangeCause cause;
    private final Optional<UUID> referenceId;

    public BoosterInventoryChangeEvent(
        PlayerBoostSnapshot snapshot,
        BoosterEventOrigin origin,
        String sourceServerId,
        BoosterId boosterId,
        long previousAmount,
        long newAmount,
        InventoryChangeCause cause,
        Optional<UUID> referenceId
    ) {
        super(snapshot.playerId(), snapshot.revision(), origin, sourceServerId, snapshot);
        if (previousAmount < 0 || newAmount < 0) {
            throw new IllegalArgumentException("amounts cannot be negative");
        }
        this.boosterId = Objects.requireNonNull(boosterId, "boosterId");
        this.previousAmount = previousAmount;
        this.newAmount = newAmount;
        this.delta = newAmount - previousAmount;
        this.cause = Objects.requireNonNull(cause, "cause");
        this.referenceId = Objects.requireNonNull(referenceId, "referenceId");
    }

    public BoosterId boosterId() {
        return this.boosterId;
    }

    public long previousAmount() {
        return this.previousAmount;
    }

    public long newAmount() {
        return this.newAmount;
    }

    public long delta() {
        return this.delta;
    }

    public InventoryChangeCause cause() {
        return this.cause;
    }

    public Optional<UUID> referenceId() {
        return this.referenceId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
