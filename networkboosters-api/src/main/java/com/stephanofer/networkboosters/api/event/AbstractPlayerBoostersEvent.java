package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.Event;

public abstract class AbstractPlayerBoostersEvent extends Event {

    private final UUID playerId;
    private final long revision;
    private final BoosterEventOrigin origin;
    private final String sourceServerId;
    private final PlayerBoostSnapshot snapshot;

    protected AbstractPlayerBoostersEvent(
        UUID playerId,
        long revision,
        BoosterEventOrigin origin,
        String sourceServerId,
        PlayerBoostSnapshot snapshot
    ) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
        this.revision = revision;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.sourceServerId = Objects.requireNonNull(sourceServerId, "sourceServerId");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (!playerId.equals(snapshot.playerId())) {
            throw new IllegalArgumentException("snapshot playerId must match event playerId");
        }
        if (snapshot.revision() != revision) {
            throw new IllegalArgumentException("snapshot revision must match event revision");
        }
    }

    public UUID playerId() {
        return this.playerId;
    }

    public long revision() {
        return this.revision;
    }

    public BoosterEventOrigin origin() {
        return this.origin;
    }

    public String sourceServerId() {
        return this.sourceServerId;
    }

    public PlayerBoostSnapshot snapshot() {
        return this.snapshot;
    }
}
