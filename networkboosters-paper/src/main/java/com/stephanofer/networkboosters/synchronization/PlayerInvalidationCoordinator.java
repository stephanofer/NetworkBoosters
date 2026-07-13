package com.stephanofer.networkboosters.synchronization;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.event.BoosterEventOrigin;
import com.stephanofer.networkboosters.api.event.InventoryChangeCause;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.event.BoosterEventDispatcher;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public final class PlayerInvalidationCoordinator implements AutoCloseable {

    private final PlayerSnapshotCache snapshots;
    private final BoosterEventDispatcher events;
    private final ComponentLogger logger;
    private final ConcurrentHashMap<UUID, RequestedChange> desired = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public PlayerInvalidationCoordinator(PlayerSnapshotCache snapshots, BoosterEventDispatcher events, ComponentLogger logger) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.events = Objects.requireNonNull(events, "events");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void accept(BoosterInvalidation invalidation, BoosterEventOrigin origin) {
        if (!this.accepting.get()) {
            return;
        }
        LinkedHashSet<UUID> affectedPlayers = new LinkedHashSet<>();
        for (BoosterInvalidation.PlayerChange change : invalidation.changes()) {
            if (!this.snapshots.isReady(change.playerId())) {
                continue;
            }
            PlayerBoostSnapshot current = this.snapshots.getCachedOrEmpty(change.playerId());
            if (change.revision() <= current.revision()) {
                continue;
            }
            this.desired.merge(
                change.playerId(),
                RequestedChange.of(change.revision(), change, invalidation.sourceServerId(), origin),
                RequestedChange::newest
            );
            affectedPlayers.add(change.playerId());
        }
        for (UUID playerId : affectedPlayers) {
            this.refresh(playerId);
        }
    }

    public void reconcile(UUID playerId, long revision, String sourceServerId) {
        if (!this.accepting.get() || !this.snapshots.isReady(playerId)) {
            return;
        }
        PlayerBoostSnapshot current = this.snapshots.getCachedOrEmpty(playerId);
        if (revision <= current.revision()) {
            return;
        }
        this.desired.merge(
            playerId,
            RequestedChange.of(
                revision,
                new BoosterInvalidation.PlayerChange(playerId, revision, BoosterChangeType.INVENTORY_CHANGED, Optional.empty()),
                sourceServerId,
                BoosterEventOrigin.RECONCILIATION
            ),
            RequestedChange::newest
        );
        this.refresh(playerId);
    }

    private void refresh(UUID playerId) {
        this.inFlight.computeIfAbsent(playerId, id -> this.refreshOnce(id).whenComplete((ignored, failure) -> {
            this.inFlight.remove(id);
            RequestedChange wanted = this.desired.get(id);
            if (this.accepting.get() && wanted != null && this.snapshots.isReady(id)
                && this.snapshots.getCachedOrEmpty(id).revision() < wanted.revision()) {
                this.refresh(id);
            }
        }));
    }

    private CompletableFuture<Void> refreshOnce(UUID playerId) {
        PlayerBoostSnapshot previous = this.snapshots.getCachedOrEmpty(playerId);
        return this.snapshots.refreshUnpublished(playerId).thenAccept(loaded -> {
            RequestedChange requested = this.desired.get(playerId);
            if (requested == null || loaded.revision() < requested.revision()) {
                return;
            }
            PlayerSnapshotCache.PublishResult result = this.snapshots.publishIfNewer(loaded);
            if (result == PlayerSnapshotCache.PublishResult.APPLIED) {
                this.desired.remove(playerId, requested);
                List<PostCommitChange> changes = this.describe(previous, loaded, requested);
                this.events.dispatch(changes, requested.origin(), requested.sourceServerId());
            } else if (!this.snapshots.isReady(playerId)
                || this.snapshots.getCachedOrEmpty(playerId).revision() >= requested.revision()) {
                this.desired.remove(playerId, requested);
            }
        }).exceptionally(failure -> {
            this.logger.warn("Failed to refresh NetworkBoosters snapshot for {} after invalidation", playerId, failure);
            return null;
        });
    }

    private List<PostCommitChange> describe(PlayerBoostSnapshot previous, PlayerBoostSnapshot current, RequestedChange requested) {
        ArrayList<PostCommitChange> changes = new ArrayList<>();
        for (ChangeMarker marker : requested.changes()) {
            switch (marker.type()) {
                case ACTIVATED -> marker.referenceId().flatMap(id -> activeById(current, id))
                    .ifPresent(active -> changes.add(new PostCommitChange.ActivationStarted(current, active, Optional.empty())));
                case EXTENDED -> marker.referenceId().flatMap(id -> activeById(current, id))
                    .ifPresent(active -> changes.add(new PostCommitChange.ActivationExtended(current, active)));
                case QUEUED -> marker.referenceId().flatMap(id -> queuedById(current, id))
                    .ifPresent(queued -> changes.add(new PostCommitChange.BoosterQueued(current, queued, false)));
                case DEACTIVATED -> marker.referenceId().flatMap(id -> activeById(previous, id))
                    .ifPresent(active -> changes.add(new PostCommitChange.ActivationDeactivated(current, active, Optional.empty())));
                case EXPIRED -> marker.referenceId().flatMap(id -> activeById(previous, id))
                    .ifPresent(active -> changes.add(new PostCommitChange.ActivationExpired(current, Optional.of(active), Optional.empty())));
                case CLAIMED -> changes.add(new PostCommitChange.StateChanged(current, marker.type(), marker.referenceId()));
                case TRANSFERRED -> marker.transfer().ifPresentOrElse(transfer -> {
                    // The sender is the deterministic reporter when both snapshots are locally relevant.
                    if (current.playerId().equals(transfer.senderId())) {
                        Optional<PlayerBoostSnapshot> recipient = this.snapshots.cached(transfer.recipientId())
                            .filter(snapshot -> snapshot.revision() >= transfer.recipientRevision());
                        changes.add(new PostCommitChange.TransferObserved(transfer, Optional.of(current), recipient));
                    }
                }, () -> changes.add(new PostCommitChange.StateChanged(current, marker.type(), marker.referenceId())));
                case INVENTORY_CHANGED -> changes.addAll(this.inventoryDiff(previous, current));
            }
        }
        if (changes.isEmpty()) {
            changes.add(new PostCommitChange.StateChanged(current, requested.changes().getFirst().type(), requested.changes().getFirst().referenceId()));
        }
        return List.copyOf(changes);
    }

    private List<PostCommitChange> inventoryDiff(PlayerBoostSnapshot previous, PlayerBoostSnapshot current) {
        ArrayList<PostCommitChange> changes = new ArrayList<>();
        ArrayList<BoosterId> ids = new ArrayList<>();
        ids.addAll(previous.inventory().keySet());
        for (BoosterId id : current.inventory().keySet()) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        ids.sort(Comparator.comparing(BoosterId::value));
        for (BoosterId id : ids) {
            long before = previous.ownedAmount(id);
            long after = current.ownedAmount(id);
            if (before != after) {
                changes.add(new PostCommitChange.InventoryChanged(
                    current,
                    id,
                    before,
                    after,
                    after > before ? InventoryChangeCause.GRANT : InventoryChangeCause.REVOKE,
                    Optional.empty()
                ));
            }
        }
        return List.copyOf(changes);
    }

    private static Optional<ActiveBooster> activeById(PlayerBoostSnapshot snapshot, UUID activationId) {
        return snapshot.activeBoosters().values().stream().filter(active -> active.activationId().equals(activationId)).findFirst();
    }

    private static Optional<QueuedBooster> queuedById(PlayerBoostSnapshot snapshot, UUID queueId) {
        return snapshot.queuedBoosters().values().stream().flatMap(List::stream).filter(queued -> queued.queueId().equals(queueId)).findFirst();
    }

    @Override
    public void close() {
        this.accepting.set(false);
        this.desired.clear();
        this.inFlight.clear();
    }

    private record RequestedChange(
        long revision,
        List<ChangeMarker> changes,
        String sourceServerId,
        BoosterEventOrigin origin
    ) {
        private RequestedChange {
            if (revision <= 0) {
                throw new IllegalArgumentException("revision must be positive");
            }
            changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
            if (changes.isEmpty()) {
                throw new IllegalArgumentException("changes cannot be empty");
            }
            Objects.requireNonNull(sourceServerId, "sourceServerId");
            Objects.requireNonNull(origin, "origin");
        }

        private static RequestedChange of(
            long revision,
            BoosterInvalidation.PlayerChange change,
            String sourceServerId,
            BoosterEventOrigin origin
        ) {
            return new RequestedChange(
                revision,
                List.of(new ChangeMarker(change.type(), change.referenceId(), change.transfer())),
                sourceServerId,
                origin
            );
        }

        private static RequestedChange newest(RequestedChange left, RequestedChange right) {
            if (right.revision() > left.revision()) {
                return right;
            }
            if (right.revision() < left.revision()) {
                return left;
            }
            LinkedHashSet<ChangeMarker> merged = new LinkedHashSet<>(left.changes());
            merged.addAll(right.changes());
            return new RequestedChange(left.revision(), List.copyOf(merged), right.sourceServerId(), right.origin());
        }
    }

    private record ChangeMarker(
        BoosterChangeType type,
        Optional<UUID> referenceId,
        Optional<BoosterInvalidation.TransferDetails> transfer
    ) {
        private ChangeMarker {
            Objects.requireNonNull(type, "type");
            referenceId = Objects.requireNonNull(referenceId, "referenceId");
            transfer = Objects.requireNonNull(transfer, "transfer");
        }
    }
}
