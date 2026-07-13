package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import org.bukkit.event.HandlerList;

public final class BoosterQueueEvent extends AbstractPlayerBoostersEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final QueuedBooster queuedBooster;
    private final boolean merged;

    public BoosterQueueEvent(PlayerBoostSnapshot snapshot, BoosterEventOrigin origin, String sourceServerId, QueuedBooster queuedBooster, boolean merged) {
        super(snapshot.playerId(), snapshot.revision(), origin, sourceServerId, snapshot);
        this.queuedBooster = Objects.requireNonNull(queuedBooster, "queuedBooster");
        this.merged = merged;
    }

    public QueuedBooster queuedBooster() {
        return this.queuedBooster;
    }

    public boolean merged() {
        return this.merged;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
