package com.stephanofer.networkboosters.synchronization;

import com.stephanofer.networkboosters.api.event.BoosterEventOrigin;
import com.stephanofer.networkboosters.event.BoosterEventDispatcher;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import java.util.Objects;

public final class RedisSynchronizationService implements PostCommitSynchronizer {

    private final PlayerSnapshotCache snapshots;
    private final BoosterEventDispatcher events;
    private final BoosterInvalidationPublisher publisher;
    private final String serverId;

    public RedisSynchronizationService(
        PlayerSnapshotCache snapshots,
        BoosterEventDispatcher events,
        BoosterInvalidationPublisher publisher,
        String serverId
    ) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.events = Objects.requireNonNull(events, "events");
        this.publisher = publisher;
        this.serverId = Objects.requireNonNull(serverId, "serverId");
    }

    @Override
    public void publish(PostCommitMutation<?> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        mutation.snapshots().forEach(this.snapshots::publish);
        this.events.dispatch(mutation, BoosterEventOrigin.LOCAL, this.serverId);
        if (this.publisher != null) {
            this.publisher.publish(mutation);
        }
    }
}
