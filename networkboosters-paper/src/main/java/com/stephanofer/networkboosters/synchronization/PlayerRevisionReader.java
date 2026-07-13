package com.stephanofer.networkboosters.synchronization;

import com.stephanofer.networkboosters.persistence.BoosterStorage;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerRevisionReader {

    private final BoosterStorage storage;

    public PlayerRevisionReader(BoosterStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public CompletableFuture<Map<UUID, Long>> revisions(Collection<UUID> playerIds) {
        return this.storage.revisions(playerIds);
    }
}
