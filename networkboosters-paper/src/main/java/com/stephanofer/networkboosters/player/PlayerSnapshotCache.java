package com.stephanofer.networkboosters.player;

import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class PlayerSnapshotCache implements AutoCloseable {

    private final Function<UUID, CompletableFuture<PlayerBoostSnapshot>> loader;
    private final Runnable closeLoader;
    private final ConcurrentHashMap<UUID, PlayerBoostSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<PlayerBoostSnapshot>> inFlightLoads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lifecycleEpochs = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public PlayerSnapshotCache(PlayerStateLoader loader) {
        Objects.requireNonNull(loader, "loader");
        this.loader = loader::load;
        this.closeLoader = loader::close;
    }

    PlayerSnapshotCache(Function<UUID, CompletableFuture<PlayerBoostSnapshot>> loader, Runnable closeLoader) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.closeLoader = Objects.requireNonNull(closeLoader, "closeLoader");
    }

    public Optional<PlayerBoostSnapshot> cached(UUID playerId) {
        return Optional.ofNullable(this.snapshots.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public PlayerBoostSnapshot getCachedOrEmpty(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerBoostSnapshot snapshot = this.snapshots.get(playerId);
        return snapshot == null ? PlayerBoostSnapshot.empty(playerId) : snapshot;
    }

    public boolean isReady(UUID playerId) {
        return this.snapshots.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    public Set<UUID> loadedPlayers() {
        return Set.copyOf(this.snapshots.keySet());
    }

    public PublishResult publishIfNewer(PlayerBoostSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        UUID playerId = snapshot.playerId();
        if (!this.accepting.get()) {
            return PublishResult.CLOSED;
        }
        final boolean[] applied = {false};
        this.snapshots.compute(playerId, (ignored, current) -> {
            if (!this.accepting.get()) {
                return current;
            }
            if (current == null) {
                return current;
            }
            if (snapshot.revision() > current.revision()) {
                applied[0] = true;
                return snapshot;
            }
            return current;
        });
        return applied[0] ? PublishResult.APPLIED : PublishResult.IGNORED;
    }

    public void publish(PlayerBoostSnapshot snapshot) {
        this.publishIfNewer(snapshot);
    }

    public CompletableFuture<PlayerBoostSnapshot> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerBoostSnapshot snapshot = this.snapshots.get(playerId);
        if (snapshot != null) {
            return CompletableFuture.completedFuture(snapshot);
        }
        return this.loadFromStorage(playerId);
    }

    public CompletableFuture<PlayerBoostSnapshot> refresh(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return this.loadFromStorage(playerId);
    }

    /**
     * Loads the latest durable state without changing this cache.
     * Synchronization callers must decide whether the result is still current before publishing it.
     */
    public CompletableFuture<PlayerBoostSnapshot> refreshUnpublished(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!this.accepting.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player snapshot cache is closed"));
        }
        long capturedEpoch = this.epoch(playerId);
        return this.loader.apply(playerId).thenApply(loaded -> {
            if (!playerId.equals(loaded.playerId())) {
                throw new IllegalStateException("Loaded snapshot belongs to " + loaded.playerId() + " but expected " + playerId);
            }
            if (!this.accepting.get() || this.epoch(playerId) != capturedEpoch) {
                throw new IllegalStateException("Player snapshot load completed after its lifecycle ended");
            }
            return loaded;
        });
    }

    public void unload(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        this.lifecycleEpochs.merge(playerId, 1L, Long::sum);
        this.snapshots.remove(playerId);
        this.inFlightLoads.remove(playerId);
    }

    private CompletableFuture<PlayerBoostSnapshot> loadFromStorage(UUID playerId) {
        if (!this.accepting.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player snapshot cache is closed"));
        }
        long capturedEpoch = this.epoch(playerId);
        CompletableFuture<PlayerBoostSnapshot> future = this.inFlightLoads.computeIfAbsent(playerId, id ->
            this.loader.apply(id).thenApply(snapshot -> this.publishIfCurrent(id, capturedEpoch, snapshot))
        );
        future.whenComplete((ignored, failure) -> this.inFlightLoads.remove(playerId, future));
        return future;
    }

    private PlayerBoostSnapshot publishIfCurrent(UUID playerId, long capturedEpoch, PlayerBoostSnapshot loaded) {
        if (!playerId.equals(loaded.playerId())) {
            throw new IllegalStateException("Loaded snapshot belongs to " + loaded.playerId() + " but expected " + playerId);
        }
        PlayerBoostSnapshot selected = this.snapshots.compute(playerId, (ignored, current) -> {
            if (!this.accepting.get() || this.epoch(playerId) != capturedEpoch) {
                return current;
            }
            if (current == null || loaded.revision() > current.revision()) {
                return loaded;
            }
            return current;
        });
        return selected == null ? loaded : selected;
    }

    private long epoch(UUID playerId) {
        return this.lifecycleEpochs.getOrDefault(playerId, 0L);
    }

    @Override
    public void close() {
        this.accepting.set(false);
        this.closeLoader.run();
        this.lifecycleEpochs.clear();
        this.inFlightLoads.clear();
        this.snapshots.clear();
    }

    public enum PublishResult {
        APPLIED,
        IGNORED,
        CLOSED
    }
}
