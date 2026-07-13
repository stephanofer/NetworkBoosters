package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.request.ActivationRequest;
import com.stephanofer.networkboosters.api.request.DeactivationRequest;
import com.stephanofer.networkboosters.api.result.ActivationResult;
import com.stephanofer.networkboosters.api.result.ActivationStatus;
import com.stephanofer.networkboosters.api.result.DeactivationResult;
import com.stephanofer.networkboosters.api.result.DeactivationStatus;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.DeactivationReason;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.config.ConfigurationSnapshot;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import com.stephanofer.networkboosters.event.BoosterEventDispatcher;
import com.stephanofer.networkboosters.persistence.AuditEntry;
import com.stephanofer.networkboosters.persistence.BoosterStorage;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import com.stephanofer.networkboosters.synchronization.PostCommitChange;
import com.stephanofer.networkboosters.synchronization.PostCommitMutation;
import com.stephanofer.networkboosters.synchronization.PostCommitSynchronizer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ActivationMutationService implements AutoCloseable {

    private final BoosterStorage storage;
    private final PlayerSnapshotCache snapshots;
    private final ConfigurationStore configurationStore;
    private final PlayerPermissionProvider permissions;
    private final BoosterEventDispatcher events;
    private final PostCommitSynchronizer postCommit;
    private final ActivationDecisionEngine decisions;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public ActivationMutationService(
        BoosterStorage storage,
        PlayerSnapshotCache snapshots,
        ConfigurationStore configurationStore,
        PlayerPermissionProvider permissions,
        BoosterEventDispatcher events,
        PostCommitSynchronizer postCommit
    ) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.configurationStore = Objects.requireNonNull(configurationStore, "configurationStore");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.events = Objects.requireNonNull(events, "events");
        this.postCommit = Objects.requireNonNull(postCommit, "postCommit");
        this.decisions = new ActivationDecisionEngine();
    }

    public CompletableFuture<ActivationResult> activate(ActivationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!this.accepting.get()) {
            return CompletableFuture.completedFuture(ActivationResult.rejected(ActivationStatus.SERVICE_UNAVAILABLE, 0));
        }
        if (!this.snapshots.isReady(request.playerId())) {
            return CompletableFuture.completedFuture(ActivationResult.rejected(ActivationStatus.PLAYER_NOT_READY, 0));
        }

        ConfigurationSnapshot configuration = this.configurationStore.requireCurrent();
        Optional<BoosterDefinition> optionalDefinition = configuration.definitions().find(request.boosterId());
        if (optionalDefinition.isEmpty()) {
            return CompletableFuture.completedFuture(ActivationResult.rejected(ActivationStatus.DEFINITION_NOT_FOUND, 0));
        }
        BoosterDefinition definition = optionalDefinition.orElseThrow();
        if (!definition.enabled()) {
            long amount = this.snapshots.getCachedOrEmpty(request.playerId()).ownedAmount(request.boosterId());
            return CompletableFuture.completedFuture(ActivationResult.rejected(ActivationStatus.DEFINITION_DISABLED, amount));
        }
        NetworkBoostersConfiguration.Activation activationConfig = configuration.configuration().activation();
        return this.permissions.satisfies(request.playerId(), definition.requirements())
            .thenCompose(requirementsSatisfied -> {
                if (!requirementsSatisfied) {
                    long amount = this.snapshots.getCachedOrEmpty(request.playerId()).ownedAmount(request.boosterId());
                    return CompletableFuture.completedFuture(new MutationOutcome<>(
                        ActivationResult.rejected(ActivationStatus.PERMISSION_DENIED, amount),
                        Optional.empty(),
                        List.of()
                    ));
                }
                return this.events.callPreActivate(request, definition, this.snapshots.getCachedOrEmpty(request.playerId()))
                    .thenCompose(allowed -> {
                        if (!allowed) {
                            long amount = this.snapshots.getCachedOrEmpty(request.playerId()).ownedAmount(request.boosterId());
                            return CompletableFuture.completedFuture(new MutationOutcome<>(
                                ActivationResult.rejected(ActivationStatus.PRE_ACTIVATION_CANCELLED, amount),
                                Optional.empty(),
                                List.of()
                            ));
                        }
                        return this.storage.write(connection -> this.activate(
                            connection,
                            request,
                            definition,
                            activationConfig,
                            true
                        ));
                    });
            })
            .thenApply(this::publishActivation)
            .exceptionally(ignored -> ActivationResult.rejected(ActivationStatus.SERVICE_UNAVAILABLE, 0));
    }

    public CompletableFuture<DeactivationResult> deactivate(DeactivationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!this.accepting.get()) {
            return CompletableFuture.completedFuture(rejectedDeactivation(DeactivationStatus.SERVICE_UNAVAILABLE));
        }
        return this.storage.write(connection -> this.deactivate(connection, request))
            .thenApply(this::publishDeactivation)
            .exceptionally(ignored -> rejectedDeactivation(DeactivationStatus.SERVICE_UNAVAILABLE));
    }

    public CompletableFuture<Integer> expireDueActivations(int limit) {
        if (!this.accepting.get() || limit < 1) {
            return CompletableFuture.completedFuture(0);
        }
        return this.storage.findExpiredActivationCandidates(limit)
            .thenCompose(this::expireCandidates)
            .thenApply(outcomes -> {
                outcomes.forEach(outcome -> this.postCommit.publish(new PostCommitMutation<>(null, outcome.changes())));
                return outcomes.size();
            });
    }

    public CompletableFuture<PlayerBoostSnapshot> reconcilePlayerState(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!this.accepting.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Activation mutations are closed"));
        }
        return this.storage.write(connection -> this.reconcilePlayerState(connection, playerId))
            .thenApply(outcome -> {
                this.postCommit.publish(new PostCommitMutation<>(outcome.result(), outcome.changes()));
                return outcome.result();
            });
    }

    private MutationOutcome<ActivationResult> activate(
        Connection connection,
        ActivationRequest request,
        BoosterDefinition definition,
        NetworkBoostersConfiguration.Activation activationConfig,
        boolean requirementsSatisfied
    ) throws SQLException {
        this.storage.revisions().revisionForUpdate(connection, request.playerId());
        Instant now = currentDatabaseTime(connection);
        Optional<BoosterDefinition> currentDefinition = this.configurationStore.requireCurrent().definitions().find(request.boosterId());
        if (currentDefinition.isEmpty()) {
            return MutationOutcome.unchanged(ActivationResult.rejected(ActivationStatus.DEFINITION_NOT_FOUND, 0));
        }
        BoosterDefinition current = currentDefinition.orElseThrow();
        if (!current.enabled()) {
            long amount = this.amountForUpdate(connection, request.playerId(), request.boosterId());
            return MutationOutcome.unchanged(ActivationResult.rejected(ActivationStatus.DEFINITION_DISABLED, amount));
        }
        if (!current.equals(definition)) {
            long amount = this.amountForUpdate(connection, request.playerId(), request.boosterId());
            return MutationOutcome.unchanged(ActivationResult.rejected(ActivationStatus.DEFINITION_CHANGED, amount));
        }
        long ownedAmount = this.amountForUpdate(connection, request.playerId(), request.boosterId());
        Optional<ActiveBooster> active = this.storage.activations().findActiveForUpdate(
            connection,
            request.playerId(),
            definition.activationGroup()
        );
        List<QueuedBooster> queue = this.storage.queue().findGroupForUpdate(connection, request.playerId(), definition.activationGroup());
        ActivationDecision decision = this.decisions.decide(new ActivationDecisionInput(
            definition,
            ownedAmount,
            active,
            queue,
            requirementsSatisfied,
            now,
            activationConfig.maximumTotalDuration(),
            activationConfig.maximumQueuedEntries()
        ));

        ReconciledGroup reconciled = this.materializeTimeline(connection, request.playerId(), active, decision.timeline());
        if (!decision.consumesInventory()) {
            Optional<PlayerBoostSnapshot> snapshot = this.finishIfReconciled(connection, request.playerId(), reconciled);
            long remaining = this.storage.inventory().amount(connection, request.playerId(), request.boosterId()).orElse(0L);
            return new MutationOutcome<>(
                ActivationResult.rejected(decision.status(), remaining),
                snapshot,
                snapshot.map(value -> this.timelineChanges(value, reconciled)).orElse(List.of())
            );
        }

        long previous = this.amountForUpdate(connection, request.playerId(), request.boosterId());
        if (previous <= 0 || !this.storage.inventory().decrementOne(connection, request.playerId(), request.boosterId())) {
            Optional<PlayerBoostSnapshot> snapshot = this.finishIfReconciled(connection, request.playerId(), reconciled);
            return new MutationOutcome<>(
                ActivationResult.rejected(ActivationStatus.NOT_OWNED, previous),
                snapshot,
                snapshot.map(value -> this.timelineChanges(value, reconciled)).orElse(List.of())
            );
        }
        long remaining = previous - 1;

        ActivationMutation mutation = this.applyActivationDecision(connection, request, definition, decision, reconciled, now);
        this.auditInventoryConsumption(connection, request, definition.id(), previous, remaining, mutation.auditTargetId(), decision.status().name());
        PlayerBoostSnapshot snapshot = this.finishMutation(connection, request.playerId());
        ArrayList<PostCommitChange> changes = new ArrayList<>(this.timelineChanges(snapshot, reconciled));
        changes.add(new PostCommitChange.InventoryChanged(
            snapshot,
            definition.id(),
            previous,
            remaining,
            com.stephanofer.networkboosters.api.event.InventoryChangeCause.ACTIVATION_CONSUMPTION,
            mutation.auditTargetId()
        ));
        changes.add(mutation.toChange(snapshot, decision.status()));
        return new MutationOutcome<>(mutation.toResult(decision.status(), remaining), Optional.of(snapshot), changes);
    }

    private MutationOutcome<DeactivationResult> deactivate(Connection connection, DeactivationRequest request) throws SQLException {
        Optional<ActivationRepository.StoredActivationStatus> status = this.storage.activations().findStatusById(
            connection,
            request.activationId()
        );
        if (status.isEmpty()) {
            return MutationOutcome.unchanged(rejectedDeactivation(DeactivationStatus.NOT_FOUND));
        }
        UUID playerId = status.orElseThrow().playerId();
        if (!this.snapshots.isReady(playerId)) {
            return MutationOutcome.unchanged(rejectedDeactivation(DeactivationStatus.PLAYER_NOT_READY));
        }
        if (!"ACTIVE".equals(status.orElseThrow().status())) {
            return MutationOutcome.unchanged(rejectedDeactivation(DeactivationStatus.ALREADY_INACTIVE));
        }

        this.storage.revisions().revisionForUpdate(connection, playerId);
        Optional<ActiveBooster> active = this.storage.activations().findByIdForUpdate(connection, request.activationId());
        if (active.isEmpty()) {
            return MutationOutcome.unchanged(rejectedDeactivation(DeactivationStatus.ALREADY_INACTIVE));
        }
        ActiveBooster deactivated = active.orElseThrow();
        Instant now = currentDatabaseTime(connection);
        List<QueuedBooster> queue = this.storage.queue().findGroupForUpdate(connection, playerId, deactivated.activationGroup());
        Optional<ActiveBooster> promoted;
        DeactivationStatus resultStatus;
        if (!deactivated.isActiveAt(now)) {
            ReconciledGroup reconciled = this.materializeTimeline(
                connection,
                playerId,
                Optional.of(deactivated),
                new QueueTimeline().advance(Optional.of(deactivated), queue, now)
            );
            promoted = reconciled.currentActive();
            resultStatus = DeactivationStatus.EXPIRED;
            PlayerBoostSnapshot snapshot = this.finishMutation(connection, playerId);
            return new MutationOutcome<>(
                new DeactivationResult(resultStatus, Optional.of(deactivated), promoted),
                Optional.of(snapshot),
                this.timelineChanges(snapshot, reconciled)
            );
        } else {
            this.storage.activations().markDeactivated(connection, deactivated.activationId());
            promoted = this.promoteNextFromNow(connection, queue, now);
            resultStatus = DeactivationStatus.DEACTIVATED;
            this.audit(connection, "BOOSTER_DEACTIVATED", playerId, deactivated.boosterId(), 0, Optional.of(deactivated.activationId()),
                Optional.empty(), actorType(request.sourceReference()), request.reason().name(), request.sourceReference(), DeactivationStatus.DEACTIVATED.name());
        }
        PlayerBoostSnapshot snapshot = this.finishMutation(connection, playerId);
        return new MutationOutcome<>(
            new DeactivationResult(resultStatus, Optional.of(deactivated), promoted),
            Optional.of(snapshot),
            List.of(new PostCommitChange.ActivationDeactivated(snapshot, deactivated, promoted))
        );
    }

    private CompletableFuture<List<MutationOutcome<Void>>> expireCandidates(
        List<ActivationRepository.ExpiredActivationCandidate> candidates
    ) {
        CompletableFuture<List<MutationOutcome<Void>>> chain = CompletableFuture.completedFuture(new ArrayList<>());
        for (ActivationRepository.ExpiredActivationCandidate candidate : candidates) {
            chain = chain.thenCompose(snapshots -> this.storage.write(connection -> this.expireCandidate(connection, candidate))
                .thenApply(outcome -> {
                    outcome.ifPresent(snapshots::add);
                    return snapshots;
                }));
        }
        return chain.thenApply(List::copyOf);
    }

    private Optional<MutationOutcome<Void>> expireCandidate(
        Connection connection,
        ActivationRepository.ExpiredActivationCandidate candidate
    ) throws SQLException {
        this.storage.revisions().revisionForUpdate(connection, candidate.playerId());
        Instant now = currentDatabaseTime(connection);
        Optional<ActiveBooster> active = this.storage.activations().findActiveForUpdate(
            connection,
            candidate.playerId(),
            candidate.group()
        );
        if (active.isEmpty() || !active.orElseThrow().activationId().equals(candidate.activationId()) || active.orElseThrow().isActiveAt(now)) {
            return Optional.empty();
        }
        List<QueuedBooster> queue = this.storage.queue().findGroupForUpdate(connection, candidate.playerId(), candidate.group());
        ReconciledGroup reconciled = this.materializeTimeline(
            connection,
            candidate.playerId(),
            active,
            new QueueTimeline().advance(active, queue, now)
        );
        if (!reconciled.changed()) {
            return Optional.empty();
        }
        PlayerBoostSnapshot snapshot = this.finishMutation(connection, candidate.playerId());
        return Optional.of(new MutationOutcome<>(null, Optional.of(snapshot), this.timelineChanges(snapshot, reconciled)));
    }

    private MutationOutcome<PlayerBoostSnapshot> reconcilePlayerState(Connection connection, UUID playerId) throws SQLException {
        Instant now = currentDatabaseTime(connection);
        List<ActivationRepository.ExpiredActivationCandidate> candidates = this.storage.activations()
            .findExpiredCandidatesForPlayer(connection, playerId, now);
        if (candidates.isEmpty()) {
            return MutationOutcome.unchanged(this.storage.playerStates().loadSnapshot(connection, playerId));
        }

        this.storage.revisions().revisionForUpdate(connection, playerId);
        boolean changed = false;
        ArrayList<TimelineChange> changes = new ArrayList<>();
        for (ActivationRepository.ExpiredActivationCandidate candidate : candidates) {
            Optional<ActiveBooster> active = this.storage.activations().findActiveForUpdate(connection, playerId, candidate.group());
            if (active.isEmpty() || !active.orElseThrow().activationId().equals(candidate.activationId()) || active.orElseThrow().isActiveAt(now)) {
                continue;
            }
            List<QueuedBooster> queue = this.storage.queue().findGroupForUpdate(connection, playerId, candidate.group());
            ReconciledGroup reconciled = this.materializeTimeline(
                connection,
                playerId,
                active,
                new QueueTimeline().advance(active, queue, now)
            );
            changed |= reconciled.changed();
            changes.addAll(reconciled.changes());
        }
        if (!changed) {
            return MutationOutcome.unchanged(this.storage.playerStates().loadSnapshot(connection, playerId));
        }
        PlayerBoostSnapshot snapshot = this.finishMutation(connection, playerId);
        return new MutationOutcome<>(snapshot, Optional.of(snapshot), this.timelineChanges(snapshot, new ReconciledGroup(
            Optional.empty(),
            List.of(),
            true,
            changes
        )));
    }

    private ReconciledGroup materializeTimeline(
        Connection connection,
        UUID playerId,
        Optional<ActiveBooster> active,
        QueueTimelineResult timeline
    ) throws SQLException {
        boolean changed = false;
        Optional<ActiveBooster> current = active.filter(booster -> !timeline.activeExpired());
        ArrayList<TimelineChange> changes = new ArrayList<>();
        if (timeline.activeExpired() && active.isPresent()) {
            ActiveBooster expired = active.orElseThrow();
            this.storage.activations().markExpired(connection, expired.activationId());
            this.audit(
                connection,
                "BOOSTER_EXPIRED",
                playerId,
                expired.boosterId(),
                0,
                Optional.of(expired.activationId()),
                Optional.empty(),
                "SYSTEM",
                DeactivationReason.EXPIRED.name(),
                SourceReference.none(),
                "EXPIRED"
            );
            changed = true;
            current = Optional.empty();
            changes.add(TimelineChange.expiredActive(expired));
        }
        ArrayList<UUID> consumedQueueIds = new ArrayList<>();
        for (TimedQueuedBooster expired : timeline.expiredQueuedBoosters()) {
            consumedQueueIds.add(expired.queuedBooster().queueId());
            this.audit(connection, "BOOSTER_QUEUE_EXPIRED", playerId, expired.queuedBooster().boosterId(), 0, Optional.empty(),
                Optional.of(expired.queuedBooster().queueId()), "SYSTEM", DeactivationReason.EXPIRED.name(), SourceReference.none(), "EXPIRED");
            changes.add(TimelineChange.expiredQueued(expired.queuedBooster()));
        }
        if (timeline.promotedBooster().isPresent()) {
            TimedQueuedBooster promoted = timeline.promotedBooster().orElseThrow();
            consumedQueueIds.add(promoted.queuedBooster().queueId());
            ActiveBooster promotedActive = activeFromQueued(promoted, UUID.randomUUID());
            this.storage.activations().insertActive(connection, promotedActive);
            this.audit(connection, "BOOSTER_PROMOTED", playerId, promoted.queuedBooster().boosterId(), 0,
                Optional.of(promotedActive.activationId()), Optional.of(promoted.queuedBooster().queueId()), "SYSTEM",
                ActivationSource.SYSTEM.name(), promoted.queuedBooster().sourceReference(), "PROMOTED");
            current = Optional.of(promotedActive);
            changes.add(TimelineChange.promoted(promotedActive, promoted.queuedBooster()));
        }
        if (!consumedQueueIds.isEmpty()) {
            this.storage.queue().deleteAll(connection, consumedQueueIds);
            changed = true;
        }
        return new ReconciledGroup(current, timeline.remainingQueue(), changed, changes);
    }

    private ActivationMutation applyActivationDecision(
        Connection connection,
        ActivationRequest request,
        BoosterDefinition definition,
        ActivationDecision decision,
        ReconciledGroup reconciled,
        Instant now
    ) throws SQLException {
        return switch (decision.type()) {
            case ACTIVATE_NEW -> {
                ActiveBooster active = activeFromDefinition(request, definition, UUID.randomUUID(), now, plus(now, definition.duration()));
                this.storage.activations().insertActive(connection, active);
                this.auditActivation(connection, "BOOSTER_ACTIVATED", request, definition.id(), active.activationId(), Optional.empty(), decision.status().name());
                yield ActivationMutation.active(active);
            }
            case EXTEND_ACTIVE -> {
                ActiveBooster current = reconciled.currentActive().orElseThrow();
                Instant expiresAt = plus(now, decision.resultingActiveRemaining().orElseThrow());
                this.storage.activations().extend(connection, current.activationId(), expiresAt);
                ActiveBooster extended = replaceExpiry(current, expiresAt);
                this.auditActivation(connection, "BOOSTER_EXTENDED", request, definition.id(), extended.activationId(), Optional.empty(), decision.status().name());
                yield ActivationMutation.active(extended);
            }
            case REPLACE_ACTIVE -> {
                ActiveBooster replaced = reconciled.currentActive().orElseThrow();
                this.storage.activations().markDeactivated(connection, replaced.activationId());
                ActiveBooster replacement = activeFromDefinition(request, definition, UUID.randomUUID(), now, plus(now, definition.duration()));
                this.storage.activations().insertActive(connection, replacement);
                this.auditReplacement(connection, request, replaced, replacement, decision.status().name());
                yield ActivationMutation.active(replacement);
            }
            case QUEUE_NEW -> {
                QueuedBooster queued = queuedFromDefinition(
                    request,
                    definition,
                    UUID.randomUUID(),
                    now,
                    BoosterQueueRepository.nextPosition(reconciled.queue())
                );
                this.storage.queue().insert(connection, queued);
                this.auditActivation(connection, "BOOSTER_QUEUED", request, definition.id(), Optional.empty(), Optional.of(queued.queueId()), decision.status().name());
                yield ActivationMutation.queued(queued);
            }
            case MERGE_LAST_QUEUE_ENTRY -> {
                QueuedBooster previous = reconciled.queue().get(reconciled.queue().size() - 1);
                QueuedBooster merged = replaceDuration(previous, decision.resultingQueuedDuration().orElseThrow());
                this.storage.queue().updateDuration(connection, previous.queueId(), merged.duration());
                this.auditActivation(connection, "BOOSTER_QUEUE_MERGED", request, definition.id(), Optional.empty(), Optional.of(merged.queueId()), decision.status().name());
                yield ActivationMutation.queued(merged);
            }
            case REJECTED -> throw new SQLException("Rejected activation decision cannot be applied");
        };
    }

    private Optional<ActiveBooster> promoteNextFromNow(Connection connection, List<QueuedBooster> queue, Instant now) throws SQLException {
        if (queue.isEmpty()) {
            return Optional.empty();
        }
        QueuedBooster next = queue.get(0);
        this.storage.queue().delete(connection, next.queueId());
        ActiveBooster promoted = activeFromQueued(new TimedQueuedBooster(next, now, plus(now, next.duration())), UUID.randomUUID());
        this.storage.activations().insertActive(connection, promoted);
        this.audit(connection, "BOOSTER_PROMOTED", next.playerId(), next.boosterId(), 0, Optional.of(promoted.activationId()),
            Optional.of(next.queueId()), "SYSTEM", ActivationSource.SYSTEM.name(), next.sourceReference(), "PROMOTED");
        return Optional.of(promoted);
    }

    private Optional<PlayerBoostSnapshot> finishIfReconciled(
        Connection connection,
        UUID playerId,
        ReconciledGroup reconciled
    ) throws SQLException {
        if (!reconciled.changed()) {
            return Optional.empty();
        }
        return Optional.of(this.finishMutation(connection, playerId));
    }

    private PlayerBoostSnapshot finishMutation(Connection connection, UUID playerId) throws SQLException {
        long revision = this.storage.revisions().increment(connection, playerId);
        PlayerBoostSnapshot snapshot = this.storage.playerStates().loadSnapshot(connection, playerId);
        if (snapshot.revision() != revision) {
            throw new SQLException("Snapshot revision mismatch after activation mutation for " + playerId);
        }
        return snapshot;
    }

    private ActivationResult publishActivation(MutationOutcome<ActivationResult> outcome) {
        this.postCommit.publish(new PostCommitMutation<>(outcome.result(), outcome.changes()));
        return outcome.result();
    }

    private DeactivationResult publishDeactivation(MutationOutcome<DeactivationResult> outcome) {
        this.postCommit.publish(new PostCommitMutation<>(outcome.result(), outcome.changes()));
        return outcome.result();
    }

    private List<PostCommitChange> timelineChanges(PlayerBoostSnapshot snapshot, ReconciledGroup reconciled) {
        ArrayList<PostCommitChange> changes = new ArrayList<>();
        for (TimelineChange change : reconciled.changes()) {
            switch (change.kind()) {
                case EXPIRED_ACTIVE -> changes.add(new PostCommitChange.ActivationExpired(snapshot, change.active(), Optional.empty()));
                case EXPIRED_QUEUED -> changes.add(new PostCommitChange.ActivationExpired(snapshot, Optional.empty(), change.queued()));
                case PROMOTED -> changes.add(new PostCommitChange.ActivationStarted(snapshot, change.active().orElseThrow(), change.queued()));
            }
        }
        return List.copyOf(changes);
    }

    private long amountForUpdate(Connection connection, UUID playerId, BoosterId boosterId) throws SQLException {
        return this.storage.inventory().amountForUpdate(connection, playerId, boosterId).orElse(0L);
    }

    private void auditActivation(
        Connection connection,
        String operation,
        ActivationRequest request,
        BoosterId boosterId,
        UUID activationId,
        Optional<UUID> queueId,
        String result
    ) throws SQLException {
        this.audit(connection, operation, request.playerId(), boosterId, 1, Optional.of(activationId), queueId,
            actorType(request.source(), request.sourceReference()), request.source().name(), request.sourceReference(), result);
    }

    private void auditActivation(
        Connection connection,
        String operation,
        ActivationRequest request,
        BoosterId boosterId,
        Optional<UUID> activationId,
        Optional<UUID> queueId,
        String result
    ) throws SQLException {
        this.audit(connection, operation, request.playerId(), boosterId, 1, activationId, queueId,
            actorType(request.source(), request.sourceReference()), request.source().name(), request.sourceReference(), result);
    }

    private void auditInventoryConsumption(
        Connection connection,
        ActivationRequest request,
        BoosterId boosterId,
        long previous,
        long remaining,
        Optional<UUID> targetId,
        String result
    ) throws SQLException {
        this.storage.auditLog().insert(connection, new AuditEntry(
            UUID.randomUUID(),
            "BOOSTER_INVENTORY_CONSUMED",
            actorType(request.source(), request.sourceReference()),
            request.sourceReference().actorId(),
            request.playerId(),
            Optional.of(boosterId),
            Optional.of(1L),
            Optional.of("{\"amount\":" + previous + "}"),
            Optional.of("{\"amount\":" + remaining + "}"),
            Optional.empty(),
            targetId,
            Optional.empty(),
            request.source().name(),
            request.sourceReference().externalReference(),
            request.sourceReference().serverId(),
            result
        ));
    }

    private void auditReplacement(
        Connection connection,
        ActivationRequest request,
        ActiveBooster replaced,
        ActiveBooster replacement,
        String result
    ) throws SQLException {
        this.storage.auditLog().insert(connection, new AuditEntry(
            UUID.randomUUID(),
            "BOOSTER_REPLACED",
            actorType(request.source(), request.sourceReference()),
            request.sourceReference().actorId(),
            request.playerId(),
            Optional.of(replacement.boosterId()),
            Optional.of(1L),
            Optional.of("{\"activationId\":\"" + replaced.activationId() + "\",\"expiresAt\":\"" + replaced.expiresAt() + "\"}"),
            Optional.of("{\"activationId\":\"" + replacement.activationId() + "\",\"expiresAt\":\"" + replacement.expiresAt() + "\"}"),
            Optional.empty(),
            Optional.of(replacement.activationId()),
            Optional.empty(),
            request.source().name(),
            request.sourceReference().externalReference(),
            request.sourceReference().serverId(),
            result
        ));
    }

    private void audit(
        Connection connection,
        String operation,
        UUID playerId,
        BoosterId boosterId,
        long amount,
        Optional<UUID> activationId,
        Optional<UUID> queueId,
        String actorType,
        String sourceType,
        SourceReference sourceReference,
        String result
    ) throws SQLException {
        this.storage.auditLog().insert(connection, new AuditEntry(
            UUID.randomUUID(),
            operation,
            actorType,
            sourceReference.actorId(),
            playerId,
            Optional.of(boosterId),
            amount <= 0 ? Optional.empty() : Optional.of(amount),
            Optional.empty(),
            queueId.map(id -> "{\"queueId\":\"" + id + "\"}"),
            Optional.empty(),
            activationId,
            Optional.empty(),
            sourceType,
            sourceReference.externalReference(),
            sourceReference.serverId(),
            result
        ));
    }

    private static ActiveBooster activeFromDefinition(
        ActivationRequest request,
        BoosterDefinition definition,
        UUID activationId,
        Instant activatedAt,
        Instant expiresAt
    ) {
        return new ActiveBooster(
            activationId,
            request.playerId(),
            definition.id(),
            definition.target(),
            definition.multiplier(),
            definition.activationGroup(),
            definition.conflictPolicy(),
            definition.scope(),
            definition.requirements(),
            activatedAt,
            expiresAt,
            request.source(),
            request.sourceReference()
        );
    }

    private static QueuedBooster queuedFromDefinition(
        ActivationRequest request,
        BoosterDefinition definition,
        UUID queueId,
        Instant queuedAt,
        long position
    ) {
        return new QueuedBooster(
            queueId,
            request.playerId(),
            definition.id(),
            definition.target(),
            definition.multiplier(),
            definition.activationGroup(),
            definition.conflictPolicy(),
            definition.scope(),
            definition.requirements(),
            definition.duration(),
            queuedAt,
            request.source(),
            request.sourceReference(),
            position
        );
    }

    private static ActiveBooster activeFromQueued(TimedQueuedBooster timed, UUID activationId) {
        QueuedBooster queued = timed.queuedBooster();
        return new ActiveBooster(
            activationId,
            queued.playerId(),
            queued.boosterId(),
            queued.target(),
            queued.multiplier(),
            queued.activationGroup(),
            queued.conflictPolicy(),
            queued.scope(),
            queued.requirements(),
            timed.startsAt(),
            timed.expiresAt(),
            queued.source(),
            queued.sourceReference()
        );
    }

    private static ActiveBooster replaceExpiry(ActiveBooster active, Instant expiresAt) {
        return new ActiveBooster(
            active.activationId(),
            active.playerId(),
            active.boosterId(),
            active.target(),
            active.multiplier(),
            active.activationGroup(),
            active.conflictPolicy(),
            active.scope(),
            active.requirements(),
            active.activatedAt(),
            expiresAt,
            active.source(),
            active.sourceReference()
        );
    }

    private static QueuedBooster replaceDuration(QueuedBooster queued, Duration duration) {
        return new QueuedBooster(
            queued.queueId(),
            queued.playerId(),
            queued.boosterId(),
            queued.target(),
            queued.multiplier(),
            queued.activationGroup(),
            queued.conflictPolicy(),
            queued.scope(),
            queued.requirements(),
            duration,
            queued.queuedAt(),
            queued.source(),
            queued.sourceReference(),
            queued.position()
        );
    }

    private static Instant plus(Instant start, Duration duration) throws SQLException {
        try {
            return start.plus(duration);
        } catch (ArithmeticException exception) {
            throw new SQLException("Instant overflow while calculating booster timeline", exception);
        }
    }

    private static Instant currentDatabaseTime(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP(3)")) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                Timestamp timestamp = result.getTimestamp(1);
                if (timestamp == null) {
                    throw new SQLException("CURRENT_TIMESTAMP(3) returned null");
                }
                return timestamp.toInstant();
            }
        }
    }

    private static String actorType(ActivationSource source, SourceReference reference) {
        if (reference.actorId().isPresent()) {
            return "PLAYER";
        }
        return source == ActivationSource.ADMIN_COMMAND ? "CONSOLE" : "SYSTEM";
    }

    private static String actorType(SourceReference reference) {
        return reference.actorId().isPresent() ? "PLAYER" : "CONSOLE";
    }

    private static DeactivationResult rejectedDeactivation(DeactivationStatus status) {
        return new DeactivationResult(status, Optional.empty(), Optional.empty());
    }

    @Override
    public void close() {
        this.accepting.set(false);
    }

    private record MutationOutcome<T>(T result, Optional<PlayerBoostSnapshot> snapshot, List<PostCommitChange> changes) {
        private MutationOutcome {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        }

        private static <T> MutationOutcome<T> unchanged(T result) {
            return new MutationOutcome<>(result, Optional.empty(), List.of());
        }
    }

    private record ReconciledGroup(Optional<ActiveBooster> currentActive, List<QueuedBooster> queue, boolean changed, List<TimelineChange> changes) {
        private ReconciledGroup {
            currentActive = Objects.requireNonNull(currentActive, "currentActive");
            queue = List.copyOf(Objects.requireNonNull(queue, "queue"));
            changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        }
    }

    private record ActivationMutation(Optional<ActiveBooster> active, Optional<QueuedBooster> queued) {
        private ActivationMutation {
            active = Objects.requireNonNull(active, "active");
            queued = Objects.requireNonNull(queued, "queued");
        }

        private static ActivationMutation active(ActiveBooster active) {
            return new ActivationMutation(Optional.of(active), Optional.empty());
        }

        private static ActivationMutation queued(QueuedBooster queued) {
            return new ActivationMutation(Optional.empty(), Optional.of(queued));
        }

        private ActivationResult toResult(ActivationStatus status, long remainingInventoryAmount) {
            return new ActivationResult(status, this.active, this.queued, remainingInventoryAmount);
        }

        private PostCommitChange toChange(PlayerBoostSnapshot snapshot, ActivationStatus status) {
            return switch (status) {
                case ACTIVATED, REPLACED -> new PostCommitChange.ActivationStarted(snapshot, this.active.orElseThrow(), Optional.empty());
                case EXTENDED -> new PostCommitChange.ActivationExtended(snapshot, this.active.orElseThrow());
                case QUEUED -> new PostCommitChange.BoosterQueued(snapshot, this.queued.orElseThrow(), false);
                case QUEUE_MERGED -> new PostCommitChange.BoosterQueued(snapshot, this.queued.orElseThrow(), true);
                default -> throw new IllegalArgumentException("Unsupported successful activation status: " + status);
            };
        }

        private Optional<UUID> auditTargetId() {
            return this.active.map(ActiveBooster::activationId).or(() -> this.queued.map(QueuedBooster::queueId));
        }
    }

    private record TimelineChange(Kind kind, Optional<ActiveBooster> active, Optional<QueuedBooster> queued) {
        private TimelineChange {
            Objects.requireNonNull(kind, "kind");
            active = Objects.requireNonNull(active, "active");
            queued = Objects.requireNonNull(queued, "queued");
        }

        private static TimelineChange expiredActive(ActiveBooster active) {
            return new TimelineChange(Kind.EXPIRED_ACTIVE, Optional.of(active), Optional.empty());
        }

        private static TimelineChange expiredQueued(QueuedBooster queued) {
            return new TimelineChange(Kind.EXPIRED_QUEUED, Optional.empty(), Optional.of(queued));
        }

        private static TimelineChange promoted(ActiveBooster active, QueuedBooster queued) {
            return new TimelineChange(Kind.PROMOTED, Optional.of(active), Optional.of(queued));
        }

        private enum Kind {
            EXPIRED_ACTIVE,
            EXPIRED_QUEUED,
            PROMOTED
        }
    }
}
