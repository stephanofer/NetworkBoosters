package com.stephanofer.networkboosters.synchronization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.booster.BoosterScopeType;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.event.BoosterEventOrigin;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.event.BoosterEventDispatcher;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.Test;

final class PlayerInvalidationCoordinatorTest {

    @Test
    void appliesUnpublishedRemoteSnapshotAndRetainsChangesAtSameRevision() {
        UUID playerId = UUID.randomUUID();
        UUID activationId = UUID.randomUUID();
        PlayerBoostSnapshot previous = snapshot(playerId, 1, Map.of());
        ActiveBooster active = active(playerId, activationId);
        PlayerBoostSnapshot current = snapshot(playerId, 2, Map.of(new ActivationGroup("points"), active));
        PlayerSnapshotCache cache = mock(PlayerSnapshotCache.class);
        BoosterEventDispatcher events = mock(BoosterEventDispatcher.class);
        ComponentLogger logger = mock(ComponentLogger.class);
        when(cache.isReady(playerId)).thenReturn(true);
        when(cache.getCachedOrEmpty(playerId)).thenReturn(previous);
        when(cache.refreshUnpublished(playerId)).thenReturn(CompletableFuture.completedFuture(current));
        when(cache.publishIfNewer(current)).thenReturn(PlayerSnapshotCache.PublishResult.APPLIED);
        PlayerInvalidationCoordinator coordinator = new PlayerInvalidationCoordinator(cache, events, logger);
        BoosterInvalidation invalidation = new BoosterInvalidation(
            BoosterInvalidation.CURRENT_SCHEMA,
            UUID.randomUUID(),
            "skywars-01",
            Instant.now(),
            List.of(
                new BoosterInvalidation.PlayerChange(playerId, 2, BoosterChangeType.INVENTORY_CHANGED, Optional.empty()),
                new BoosterInvalidation.PlayerChange(playerId, 2, BoosterChangeType.ACTIVATED, Optional.of(activationId))
            )
        );

        coordinator.accept(invalidation, BoosterEventOrigin.REMOTE);

        verify(events).dispatch(
            org.mockito.ArgumentMatchers.<List<PostCommitChange>>argThat(changes ->
                changes.stream().anyMatch(PostCommitChange.ActivationStarted.class::isInstance)
            ),
            eq(BoosterEventOrigin.REMOTE),
            eq("skywars-01")
        );
    }

    @Test
    void emitsRemoteTransferWithAvailableParticipantSnapshots() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        PlayerBoostSnapshot senderPrevious = snapshot(senderId, 1, Map.of());
        PlayerBoostSnapshot senderCurrent = snapshot(senderId, 2, Map.of());
        PlayerBoostSnapshot recipientCurrent = snapshot(recipientId, 3, Map.of());
        PlayerSnapshotCache cache = mock(PlayerSnapshotCache.class);
        BoosterEventDispatcher events = mock(BoosterEventDispatcher.class);
        ComponentLogger logger = mock(ComponentLogger.class);
        when(cache.isReady(senderId)).thenReturn(true);
        when(cache.getCachedOrEmpty(senderId)).thenReturn(senderPrevious);
        when(cache.refreshUnpublished(senderId)).thenReturn(CompletableFuture.completedFuture(senderCurrent));
        when(cache.publishIfNewer(senderCurrent)).thenReturn(PlayerSnapshotCache.PublishResult.APPLIED);
        when(cache.cached(recipientId)).thenReturn(Optional.of(recipientCurrent));
        PlayerInvalidationCoordinator coordinator = new PlayerInvalidationCoordinator(cache, events, logger);
        BoosterInvalidation.TransferDetails transfer = new BoosterInvalidation.TransferDetails(
            transferId,
            senderId,
            recipientId,
            BoosterId.of("personal_points_x2"),
            3,
            2,
            3
        );

        coordinator.accept(new BoosterInvalidation(
            BoosterInvalidation.CURRENT_SCHEMA,
            UUID.randomUUID(),
            "lobby-01",
            Instant.now(),
            List.of(new BoosterInvalidation.PlayerChange(
                senderId,
                2,
                BoosterChangeType.TRANSFERRED,
                Optional.of(transferId),
                Optional.of(transfer)
            ))
        ), BoosterEventOrigin.REMOTE);

        verify(events).dispatch(
            org.mockito.ArgumentMatchers.<List<PostCommitChange>>argThat(changes -> {
                PostCommitChange.TransferObserved observed = (PostCommitChange.TransferObserved) changes.getFirst();
                return transferId.equals(observed.details().transferId())
                    && recipientCurrent.equals(observed.recipientSnapshot().orElseThrow());
            }),
            eq(BoosterEventOrigin.REMOTE),
            eq("lobby-01")
        );
    }

    private static PlayerBoostSnapshot snapshot(UUID playerId, long revision, Map<ActivationGroup, ActiveBooster> active) {
        return new PlayerBoostSnapshot(playerId, revision, Map.of(), active, Map.of(), List.of());
    }

    private static ActiveBooster active(UUID playerId, UUID activationId) {
        Instant now = Instant.parse("2026-07-12T18:30:20Z");
        return new ActiveBooster(
            activationId,
            playerId,
            BoosterId.of("personal_points_x2"),
            new BoosterTarget("network_points:points"),
            BigDecimal.valueOf(2),
            new ActivationGroup("points"),
            ConflictPolicy.QUEUE,
            new BoosterScope(BoosterScopeType.PERSONAL, Set.of("*"), Set.of("*")),
            ActivationRequirements.NONE,
            now,
            now.plusSeconds(60),
            ActivationSource.SYSTEM,
            SourceReference.none()
        );
    }
}
