package com.stephanofer.networkboosters.player;

import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class PlayerStateLoader implements AutoCloseable {

    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicReference<Function<UUID, CompletableFuture<PlayerBoostSnapshot>>> reconciler = new AtomicReference<>();

    public CompletableFuture<PlayerBoostSnapshot> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!this.accepting.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player state loader is closed"));
        }
        Function<UUID, CompletableFuture<PlayerBoostSnapshot>> currentReconciler = this.reconciler.get();
        if (currentReconciler == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player state reconciler has not been initialized"));
        }
        return currentReconciler.apply(playerId);
    }

    public void initializeReconciler(Function<UUID, CompletableFuture<PlayerBoostSnapshot>> reconciler) {
        if (!this.reconciler.compareAndSet(null, Objects.requireNonNull(reconciler, "reconciler"))) {
            throw new IllegalStateException("Player state reconciler has already been initialized");
        }
    }

    @Override
    public void close() {
        this.accepting.set(false);
    }
}
