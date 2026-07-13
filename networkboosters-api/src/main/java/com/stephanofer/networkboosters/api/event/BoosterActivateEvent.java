package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.event.HandlerList;

public final class BoosterActivateEvent extends AbstractPlayerBoostersEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ActiveBooster activeBooster;
    private final Optional<QueuedBooster> consumedQueueEntry;

    public BoosterActivateEvent(
        PlayerBoostSnapshot snapshot,
        BoosterEventOrigin origin,
        String sourceServerId,
        ActiveBooster activeBooster,
        Optional<QueuedBooster> consumedQueueEntry
    ) {
        super(snapshot.playerId(), snapshot.revision(), origin, sourceServerId, snapshot);
        this.activeBooster = Objects.requireNonNull(activeBooster, "activeBooster");
        this.consumedQueueEntry = Objects.requireNonNull(consumedQueueEntry, "consumedQueueEntry");
    }

    public ActiveBooster activeBooster() {
        return this.activeBooster;
    }

    public Optional<QueuedBooster> consumedQueueEntry() {
        return this.consumedQueueEntry;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
