package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import org.bukkit.event.HandlerList;

public final class BoosterExtendEvent extends AbstractPlayerBoostersEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ActiveBooster activeBooster;

    public BoosterExtendEvent(PlayerBoostSnapshot snapshot, BoosterEventOrigin origin, String sourceServerId, ActiveBooster activeBooster) {
        super(snapshot.playerId(), snapshot.revision(), origin, sourceServerId, snapshot);
        this.activeBooster = Objects.requireNonNull(activeBooster, "activeBooster");
    }

    public ActiveBooster activeBooster() {
        return this.activeBooster;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
