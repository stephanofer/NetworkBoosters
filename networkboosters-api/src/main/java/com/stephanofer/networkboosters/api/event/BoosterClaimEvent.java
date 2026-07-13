package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import org.bukkit.event.HandlerList;

public final class BoosterClaimEvent extends AbstractPlayerBoostersEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BoosterClaim claim;
    private final long inventoryAmount;

    public BoosterClaimEvent(
        PlayerBoostSnapshot snapshot,
        BoosterEventOrigin origin,
        String sourceServerId,
        BoosterClaim claim,
        long inventoryAmount
    ) {
        super(snapshot.playerId(), snapshot.revision(), origin, sourceServerId, snapshot);
        this.claim = Objects.requireNonNull(claim, "claim");
        if (inventoryAmount < 0) {
            throw new IllegalArgumentException("inventoryAmount cannot be negative");
        }
        this.inventoryAmount = inventoryAmount;
    }

    public BoosterClaim claim() {
        return this.claim;
    }

    public long inventoryAmount() {
        return this.inventoryAmount;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
