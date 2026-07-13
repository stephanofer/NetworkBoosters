package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.event.HandlerList;

public final class BoosterExpireEvent extends AbstractPlayerBoostersEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Optional<ActiveBooster> expiredActiveBooster;
    private final Optional<QueuedBooster> expiredQueuedBooster;

    public BoosterExpireEvent(
        PlayerBoostSnapshot snapshot,
        BoosterEventOrigin origin,
        String sourceServerId,
        Optional<ActiveBooster> expiredActiveBooster,
        Optional<QueuedBooster> expiredQueuedBooster
    ) {
        super(snapshot.playerId(), snapshot.revision(), origin, sourceServerId, snapshot);
        this.expiredActiveBooster = Objects.requireNonNull(expiredActiveBooster, "expiredActiveBooster");
        this.expiredQueuedBooster = Objects.requireNonNull(expiredQueuedBooster, "expiredQueuedBooster");
        if (expiredActiveBooster.isEmpty() && expiredQueuedBooster.isEmpty()) {
            throw new IllegalArgumentException("an expired active or queued booster is required");
        }
    }

    public Optional<ActiveBooster> expiredActiveBooster() {
        return this.expiredActiveBooster;
    }

    public Optional<QueuedBooster> expiredQueuedBooster() {
        return this.expiredQueuedBooster;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
