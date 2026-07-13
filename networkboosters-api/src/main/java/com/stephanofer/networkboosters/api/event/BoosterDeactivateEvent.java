package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.event.HandlerList;

public final class BoosterDeactivateEvent extends AbstractPlayerBoostersEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ActiveBooster deactivatedBooster;
    private final Optional<ActiveBooster> promotedBooster;

    public BoosterDeactivateEvent(
        PlayerBoostSnapshot snapshot,
        BoosterEventOrigin origin,
        String sourceServerId,
        ActiveBooster deactivatedBooster,
        Optional<ActiveBooster> promotedBooster
    ) {
        super(snapshot.playerId(), snapshot.revision(), origin, sourceServerId, snapshot);
        this.deactivatedBooster = Objects.requireNonNull(deactivatedBooster, "deactivatedBooster");
        this.promotedBooster = Objects.requireNonNull(promotedBooster, "promotedBooster");
    }

    public ActiveBooster deactivatedBooster() {
        return this.deactivatedBooster;
    }

    public Optional<ActiveBooster> promotedBooster() {
        return this.promotedBooster;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
