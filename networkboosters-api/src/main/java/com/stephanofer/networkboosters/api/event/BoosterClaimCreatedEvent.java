package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import org.bukkit.event.HandlerList;

public final class BoosterClaimCreatedEvent extends AbstractPlayerBoostersEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final BoosterClaim claim;

    public BoosterClaimCreatedEvent(
        PlayerBoostSnapshot snapshot,
        BoosterEventOrigin origin,
        String sourceServerId,
        BoosterClaim claim
    ) {
        super(snapshot.playerId(), snapshot.revision(), origin, sourceServerId, snapshot);
        this.claim = Objects.requireNonNull(claim, "claim");
    }

    public BoosterClaim claim() {
        return this.claim;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
