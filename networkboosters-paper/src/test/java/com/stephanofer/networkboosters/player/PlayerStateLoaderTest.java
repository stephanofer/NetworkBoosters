package com.stephanofer.networkboosters.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class PlayerStateLoaderTest {

    @Test
    void loadsOnlyThroughTheInitializedReconciler() {
        PlayerStateLoader loader = new PlayerStateLoader();
        UUID playerId = UUID.randomUUID();

        assertThrows(CompletionException.class, () -> loader.load(playerId).join());

        loader.initializeReconciler(id -> CompletableFuture.completedFuture(snapshot(id, 4)));

        assertEquals(4, loader.load(playerId).join().revision());
        assertThrows(IllegalStateException.class, () -> loader.initializeReconciler(id -> CompletableFuture.completedFuture(snapshot(id, 5))));
    }

    private static PlayerBoostSnapshot snapshot(UUID playerId, long revision) {
        return new PlayerBoostSnapshot(playerId, revision, Map.of(), Map.of(), Map.of(), java.util.List.of());
    }
}
