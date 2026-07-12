package com.stephanofer.networkboosters.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PlayerSnapshotCacheTest {

    @Test
    void concurrentLoadsShareTheSameFutureAndPublishSnapshot() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();
        CompletableFuture<PlayerBoostSnapshot> backing = new CompletableFuture<>();
        PlayerSnapshotCache cache = new PlayerSnapshotCache(id -> {
            loads.incrementAndGet();
            return backing;
        }, () -> {
        });

        CompletableFuture<PlayerBoostSnapshot> first = cache.load(playerId);
        CompletableFuture<PlayerBoostSnapshot> second = cache.load(playerId);

        assertSame(first, second);
        assertEquals(1, loads.get());
        backing.complete(snapshot(playerId, 1));

        assertEquals(1, first.join().revision());
        assertTrue(cache.isReady(playerId));
        assertEquals(1, cache.cached(playerId).orElseThrow().revision());
    }

    @Test
    void olderRefreshCannotReplaceNewerSnapshot() {
        UUID playerId = UUID.randomUUID();
        AtomicReference<CompletableFuture<PlayerBoostSnapshot>> next = new AtomicReference<>();
        AtomicInteger loads = new AtomicInteger();
        PlayerSnapshotCache cache = new PlayerSnapshotCache(id -> {
            loads.incrementAndGet();
            return next.get();
        }, () -> {
        });

        next.set(CompletableFuture.completedFuture(snapshot(playerId, 7)));
        assertEquals(7, cache.refresh(playerId).join().revision());

        next.set(CompletableFuture.completedFuture(snapshot(playerId, 3)));
        assertEquals(7, cache.refresh(playerId).join().revision());
        assertEquals(2, loads.get());
        assertEquals(7, cache.cached(playerId).orElseThrow().revision());
    }

    @Test
    void completedLoadIsRemovedBeforeTheNextRefresh() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();
        PlayerSnapshotCache cache = new PlayerSnapshotCache(
            id -> CompletableFuture.completedFuture(snapshot(id, loads.incrementAndGet())),
            () -> {
            }
        );

        assertEquals(1, cache.refresh(playerId).join().revision());
        assertEquals(2, cache.refresh(playerId).join().revision());
    }

    @Test
    void failedLoadDoesNotBlockARecoveryRefresh() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();
        PlayerSnapshotCache cache = new PlayerSnapshotCache(id -> {
            if (loads.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("storage unavailable"));
            }
            return CompletableFuture.completedFuture(snapshot(id, 1));
        }, () -> {
        });

        assertThrows(CompletionException.class, () -> cache.refresh(playerId).join());
        assertEquals(1, cache.refresh(playerId).join().revision());
        assertEquals(2, loads.get());
    }

    @Test
    void unloadPreventsLateLoadFromReenteringCache() {
        UUID playerId = UUID.randomUUID();
        CompletableFuture<PlayerBoostSnapshot> backing = new CompletableFuture<>();
        PlayerSnapshotCache cache = new PlayerSnapshotCache(id -> backing, () -> {
        });

        CompletableFuture<PlayerBoostSnapshot> load = cache.load(playerId);
        cache.unload(playerId);
        backing.complete(snapshot(playerId, 1));

        assertEquals(1, load.join().revision());
        assertFalse(cache.isReady(playerId));
    }

    @Test
    void closeClearsStateAndClosesLoader() {
        UUID playerId = UUID.randomUUID();
        AtomicBoolean closed = new AtomicBoolean();
        PlayerSnapshotCache cache = new PlayerSnapshotCache(
            id -> CompletableFuture.completedFuture(snapshot(id, 1)),
            () -> closed.set(true)
        );

        cache.load(playerId).join();
        cache.close();

        assertTrue(closed.get());
        assertFalse(cache.isReady(playerId));
        assertThrows(CompletionException.class, () -> cache.load(playerId).join());
    }

    private static PlayerBoostSnapshot snapshot(UUID playerId, long revision) {
        return new PlayerBoostSnapshot(playerId, revision, Map.of(), Map.of(), Map.of(), java.util.List.of());
    }
}
