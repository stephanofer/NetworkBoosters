package com.stephanofer.networkboosters.player;

import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.persistence.BoosterStorage;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerStateLoader implements AutoCloseable {

    private final BoosterStorage storage;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public PlayerStateLoader(BoosterStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public CompletableFuture<PlayerBoostSnapshot> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!this.accepting.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player state loader is closed"));
        }
        return this.storage.loadSnapshot(playerId);
    }

    @Override
    public void close() {
        this.accepting.set(false);
    }
}
