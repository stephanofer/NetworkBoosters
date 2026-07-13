package com.stephanofer.networkboosters.inventory;

import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.event.InventoryChangeCause;
import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.player.ClaimStatus;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.request.ClaimRequest;
import com.stephanofer.networkboosters.api.request.ClaimCreationRequest;
import com.stephanofer.networkboosters.api.request.InventoryGrantRequest;
import com.stephanofer.networkboosters.api.request.InventoryRevokeRequest;
import com.stephanofer.networkboosters.api.request.InventorySetRequest;
import com.stephanofer.networkboosters.api.result.ClaimResult;
import com.stephanofer.networkboosters.api.result.ClaimResultStatus;
import com.stephanofer.networkboosters.api.result.InventoryMutationResult;
import com.stephanofer.networkboosters.api.result.InventoryMutationStatus;
import com.stephanofer.networkboosters.api.source.ClaimSource;
import com.stephanofer.networkboosters.api.source.MutationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.capacity.InventoryCapacityResolver;
import com.stephanofer.networkboosters.capacity.ResolvedInventoryCapacity;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.persistence.AuditEntry;
import com.stephanofer.networkboosters.persistence.BoosterStorage;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import com.stephanofer.networkboosters.synchronization.PostCommitChange;
import com.stephanofer.networkboosters.synchronization.PostCommitMutation;
import com.stephanofer.networkboosters.synchronization.PostCommitSynchronizer;
import com.stephanofer.networkboosters.synchronization.BoosterChangeType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public final class InventoryMutationService {

    private static final String FORCE_PERMISSION = "networkboosters.admin.give.force";
    private static final ComponentLogger LOGGER = ComponentLogger.logger(InventoryMutationService.class);

    private final BoosterStorage storage;
    private final PlayerSnapshotCache snapshots;
    private final ConfigurationStore configurationStore;
    private final PlayerCapacityProvider capacityProvider;
    private final InventoryCapacityResolver capacityResolver;
    private final Server server;
    private final PostCommitSynchronizer postCommit;

    public InventoryMutationService(
        BoosterStorage storage,
        PlayerSnapshotCache snapshots,
        ConfigurationStore configurationStore,
        Server server,
        PostCommitSynchronizer postCommit
    ) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.configurationStore = Objects.requireNonNull(configurationStore, "configurationStore");
        this.server = Objects.requireNonNull(server, "server");
        this.postCommit = Objects.requireNonNull(postCommit, "postCommit");
        this.capacityResolver = new InventoryCapacityResolver();
        this.capacityProvider = new PlayerCapacityProvider(server, this.capacityResolver);
    }

    public CompletableFuture<InventoryMutationResult> grant(InventoryGrantRequest request) {
        Objects.requireNonNull(request, "request");
        if (!this.snapshots.isReady(request.playerId())) {
            return CompletableFuture.completedFuture(result(InventoryMutationStatus.PLAYER_NOT_READY, request.boosterId(), 0, 0));
        }
        if (this.definition(request.boosterId()).isEmpty()) {
            return CompletableFuture.completedFuture(result(InventoryMutationStatus.DEFINITION_NOT_FOUND, request.boosterId(), 0, 0));
        }
        if (request.force() && !this.canForce(request)) {
            return CompletableFuture.completedFuture(result(InventoryMutationStatus.PERMISSION_DENIED, request.boosterId(), 0, 0));
        }

        ResolvedInventoryCapacity capacity = this.resolveCapacity(request.playerId());
        return this.storage.write(connection -> this.grant(connection, request, capacity))
            .thenApply(this::publishInventoryResult)
            .exceptionally(failure -> {
                this.logInventoryFailure("grant", request.playerId(), request.boosterId(), request.amount(), request.source().name(), failure);
                return result(InventoryMutationStatus.SERVICE_UNAVAILABLE, request.boosterId(), 0, 0);
            });
    }

    public CompletableFuture<InventoryMutationResult> revoke(InventoryRevokeRequest request) {
        Objects.requireNonNull(request, "request");
        if (!this.snapshots.isReady(request.playerId())) {
            return CompletableFuture.completedFuture(result(InventoryMutationStatus.PLAYER_NOT_READY, request.boosterId(), 0, 0));
        }
        return this.storage.write(connection -> this.revoke(connection, request))
            .thenApply(this::publishInventoryResult)
            .exceptionally(failure -> {
                this.logInventoryFailure("revoke", request.playerId(), request.boosterId(), request.amount(), request.source().name(), failure);
                return result(InventoryMutationStatus.SERVICE_UNAVAILABLE, request.boosterId(), 0, 0);
            });
    }

    public CompletableFuture<InventoryMutationResult> setInventoryAmount(InventorySetRequest request) {
        Objects.requireNonNull(request, "request");
        if (!this.snapshots.isReady(request.playerId())) {
            return CompletableFuture.completedFuture(result(InventoryMutationStatus.PLAYER_NOT_READY, request.boosterId(), 0, 0));
        }
        if (request.force() && !this.canForce(request.source(), request.sourceReference())) {
            return CompletableFuture.completedFuture(result(InventoryMutationStatus.PERMISSION_DENIED, request.boosterId(), 0, 0));
        }

        ResolvedInventoryCapacity capacity = this.resolveCapacity(request.playerId());
        return this.storage.write(connection -> this.setInventoryAmount(connection, request, capacity))
            .thenApply(this::publishInventoryResult)
            .exceptionally(failure -> {
                this.logInventoryFailure("set", request.playerId(), request.boosterId(), request.amount(), request.source().name(), failure);
                return result(InventoryMutationStatus.SERVICE_UNAVAILABLE, request.boosterId(), 0, 0);
            });
    }

    public CompletableFuture<ClaimResult> claim(ClaimRequest request) {
        Objects.requireNonNull(request, "request");
        if (!this.snapshots.isReady(request.playerId())) {
            return CompletableFuture.completedFuture(new ClaimResult(ClaimResultStatus.PLAYER_NOT_READY, Optional.empty(), 0));
        }

        ResolvedInventoryCapacity capacity = this.resolveCapacity(request.playerId());
        return this.storage.write(connection -> this.claim(connection, request, capacity))
            .thenApply(this::publishClaimResult)
            .exceptionally(failure -> {
                LOGGER.error(
                    "NetworkBoosters claim failed player={} claim={}",
                    request.playerId(),
                    request.claimId(),
                    rootCause(failure)
                );
                return new ClaimResult(ClaimResultStatus.SERVICE_UNAVAILABLE, Optional.empty(), 0);
            });
    }

    public CompletableFuture<InventoryMutationResult> createClaim(ClaimCreationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!this.snapshots.isReady(request.playerId())) {
            return CompletableFuture.completedFuture(result(InventoryMutationStatus.PLAYER_NOT_READY, request.boosterId(), 0, 0));
        }
        if (this.definition(request.boosterId()).isEmpty()) {
            return CompletableFuture.completedFuture(result(InventoryMutationStatus.DEFINITION_NOT_FOUND, request.boosterId(), 0, 0));
        }
        return this.storage.write(connection -> this.createClaim(connection, request))
            .thenApply(this::publishInventoryResult)
            .exceptionally(failure -> {
                this.logInventoryFailure("create-claim", request.playerId(), request.boosterId(), request.amount(), request.source().name(), failure);
                return result(InventoryMutationStatus.SERVICE_UNAVAILABLE, request.boosterId(), 0, 0);
            });
    }

    private MutationOutcome<InventoryMutationResult> createClaim(Connection connection, ClaimCreationRequest request) throws SQLException {
        this.storage.revisions().revisionForUpdate(connection, request.playerId());
        long inventoryAmount = this.amount(connection, request.playerId(), request.boosterId());
        UUID claimId = UUID.randomUUID();
        BoosterClaim claim = this.storage.claims().insert(
            connection,
            claimId,
            request.playerId(),
            request.boosterId(),
            request.amount(),
            request.source(),
            request.sourceReference(),
            currentDatabaseTime(connection)
        );
        long revision = this.storage.revisions().increment(connection, request.playerId());
        this.audit(
            connection,
            UUID.randomUUID(),
            "CLAIM_CREATED",
            request.playerId(),
            request.boosterId(),
            request.amount(),
            inventoryAmount,
            inventoryAmount,
            Optional.of(claimId),
            request.sourceReference().actorId().isPresent() ? "PLAYER" : "CONSOLE",
            request.source().name(),
            request.sourceReference(),
            InventoryMutationStatus.CLAIM_CREATED.name()
        );
        PlayerBoostSnapshot snapshot = this.storage.playerStates().loadSnapshot(connection, request.playerId());
        if (snapshot.revision() != revision) {
            throw new SQLException("Snapshot revision mismatch after claim creation for " + request.playerId());
        }
        return new MutationOutcome<>(
            new InventoryMutationResult(InventoryMutationStatus.CLAIM_CREATED, request.boosterId(), inventoryAmount, inventoryAmount, Optional.of(claim)),
            Optional.of(snapshot)
        );
    }

    private MutationOutcome<InventoryMutationResult> grant(
        Connection connection,
        InventoryGrantRequest request,
        ResolvedInventoryCapacity capacity
    ) throws SQLException {
        Optional<String> externalReference = request.sourceReference().externalReference();
        MutationReceipt reservedReceipt = null;
        if (externalReference.isPresent()) {
            MutationReceipt receipt = this.storage.mutationReceipts().reserve(
                connection,
                "INVENTORY_GRANT",
                request.source().name(),
                externalReference.get(),
                request.playerId(),
                request.boosterId(),
                request.amount()
            );
            if (!receipt.result().equals("PENDING")) {
                InventoryMutationStatus status = receipt.matches(
                    request.playerId(),
                    request.boosterId(),
                    request.amount()
                )
                    ? InventoryMutationStatus.DUPLICATE_REQUEST
                    : InventoryMutationStatus.IDEMPOTENCY_CONFLICT;
                return new MutationOutcome<>(result(status, request.boosterId(), 0, 0), Optional.empty());
            }
            if (!receipt.matches(request.playerId(), request.boosterId(), request.amount())) {
                this.storage.mutationReceipts().complete(
                    connection,
                    receipt,
                    InventoryMutationStatus.IDEMPOTENCY_CONFLICT.name(),
                    Optional.empty()
                );
                return new MutationOutcome<>(result(InventoryMutationStatus.IDEMPOTENCY_CONFLICT, request.boosterId(), 0, 0), Optional.empty());
            }
            reservedReceipt = receipt;
        }

        this.storage.revisions().revisionForUpdate(connection, request.playerId());
        long previous = this.amount(connection, request.playerId(), request.boosterId());
        long total = this.storage.inventory().total(connection, request.playerId());
        if (overflows(previous, request.amount()) || overflows(total, request.amount())) {
            return this.completeRejectedGrantReceipt(
                connection,
                reservedReceipt,
                InventoryMutationStatus.INVENTORY_LIMIT_REACHED,
                request.boosterId(),
                previous
            );
        }

        InventoryMutationStatus status;
        BoosterClaim claim = null;
        UUID claimId = null;
        Optional<ClaimSource> claimSource = claimSource(request.source());
        if (request.force()) {
            this.storage.inventory().add(connection, request.playerId(), request.boosterId(), request.amount());
            status = InventoryMutationStatus.GRANTED_FORCED;
        } else if (this.capacityResolver.canReceive(total, request.amount(), capacity)) {
            this.storage.inventory().add(connection, request.playerId(), request.boosterId(), request.amount());
            status = InventoryMutationStatus.GRANTED;
        } else if (claimSource.isPresent()) {
            claimId = UUID.randomUUID();
            claim = this.storage.claims().insert(
                connection,
                claimId,
                request.playerId(),
                request.boosterId(),
                request.amount(),
                claimSource.orElseThrow(),
                request.sourceReference(),
                currentDatabaseTime(connection)
            );
            status = InventoryMutationStatus.CLAIM_CREATED;
        } else {
            return this.completeRejectedGrantReceipt(
                connection,
                reservedReceipt,
                InventoryMutationStatus.INVENTORY_LIMIT_REACHED,
                request.boosterId(),
                previous
            );
        }

        long next = status == InventoryMutationStatus.CLAIM_CREATED ? previous : Math.addExact(previous, request.amount());
        long revision = this.storage.revisions().increment(connection, request.playerId());
        this.audit(
            connection,
            UUID.randomUUID(),
            operation(status),
            request.playerId(),
            request.boosterId(),
            request.amount(),
            previous,
            next,
            Optional.ofNullable(claimId),
            actorType(request.source(), request.sourceReference()),
            request.source().name(),
            request.sourceReference(),
            status.name()
        );
        if (reservedReceipt != null) {
            this.storage.mutationReceipts().complete(connection, reservedReceipt, status.name(), Optional.ofNullable(claimId));
        }
        PlayerBoostSnapshot snapshot = this.storage.playerStates().loadSnapshot(connection, request.playerId());
        if (snapshot.revision() != revision) {
            throw new SQLException("Snapshot revision mismatch after grant for " + request.playerId());
        }
        return new MutationOutcome<>(
            new InventoryMutationResult(status, request.boosterId(), previous, next, Optional.ofNullable(claim)),
            Optional.of(snapshot)
        );
    }

    private MutationOutcome<InventoryMutationResult> completeRejectedGrantReceipt(
        Connection connection,
        MutationReceipt receipt,
        InventoryMutationStatus status,
        BoosterId boosterId,
        long amount
    ) throws SQLException {
        if (receipt != null) {
            this.storage.mutationReceipts().complete(connection, receipt, status.name(), Optional.empty());
        }
        return new MutationOutcome<>(result(status, boosterId, amount, amount), Optional.empty());
    }

    private MutationOutcome<InventoryMutationResult> revoke(Connection connection, InventoryRevokeRequest request) throws SQLException {
        this.storage.revisions().revisionForUpdate(connection, request.playerId());
        long previous = this.amount(connection, request.playerId(), request.boosterId());
        if (previous < request.amount()) {
            return new MutationOutcome<>(
                result(InventoryMutationStatus.INSUFFICIENT_AMOUNT, request.boosterId(), previous, previous),
                Optional.empty()
            );
        }
        long next = previous - request.amount();
        this.storage.inventory().set(connection, request.playerId(), request.boosterId(), next);
        long revision = this.storage.revisions().increment(connection, request.playerId());
        this.audit(
            connection,
            UUID.randomUUID(),
            "INVENTORY_REVOKE",
            request.playerId(),
            request.boosterId(),
            request.amount(),
            previous,
            next,
            Optional.empty(),
            actorType(request.source(), request.sourceReference()),
            request.source().name(),
            request.sourceReference(),
            InventoryMutationStatus.REVOKED.name()
        );
        PlayerBoostSnapshot snapshot = this.storage.playerStates().loadSnapshot(connection, request.playerId());
        if (snapshot.revision() != revision) {
            throw new SQLException("Snapshot revision mismatch after revoke for " + request.playerId());
        }
        return new MutationOutcome<>(
            new InventoryMutationResult(InventoryMutationStatus.REVOKED, request.boosterId(), previous, next, Optional.empty()),
            Optional.of(snapshot)
        );
    }

    private MutationOutcome<InventoryMutationResult> setInventoryAmount(
        Connection connection,
        InventorySetRequest request,
        ResolvedInventoryCapacity capacity
    ) throws SQLException {
        this.storage.revisions().revisionForUpdate(connection, request.playerId());
        long previous = this.amount(connection, request.playerId(), request.boosterId());
        long next = request.amount();
        if (previous == next) {
            return new MutationOutcome<>(
                new InventoryMutationResult(InventoryMutationStatus.UNCHANGED, request.boosterId(), previous, next, Optional.empty()),
                Optional.empty()
            );
        }
        if (next > previous && this.definition(request.boosterId()).isEmpty()) {
            return new MutationOutcome<>(
                result(InventoryMutationStatus.DEFINITION_NOT_FOUND, request.boosterId(), previous, previous),
                Optional.empty()
            );
        }
        long delta = next - previous;
        if (!request.force() && delta > 0) {
            long total = this.storage.inventory().total(connection, request.playerId());
            long totalWithoutCurrent = total - previous;
            if (overflows(totalWithoutCurrent, next) || !this.capacityResolver.canReceive(totalWithoutCurrent, next, capacity)) {
                return new MutationOutcome<>(
                    result(InventoryMutationStatus.INVENTORY_LIMIT_REACHED, request.boosterId(), previous, previous),
                    Optional.empty()
                );
            }
        }
        this.storage.inventory().set(connection, request.playerId(), request.boosterId(), next);
        long revision = this.storage.revisions().increment(connection, request.playerId());
        this.audit(
            connection,
            UUID.randomUUID(),
            request.force() ? "INVENTORY_SET_FORCED" : "INVENTORY_SET",
            request.playerId(),
            request.boosterId(),
            next,
            previous,
            next,
            Optional.empty(),
            actorType(request.source(), request.sourceReference()),
            request.source().name(),
            request.sourceReference(),
            InventoryMutationStatus.SET.name()
        );
        PlayerBoostSnapshot snapshot = this.storage.playerStates().loadSnapshot(connection, request.playerId());
        if (snapshot.revision() != revision) {
            throw new SQLException("Snapshot revision mismatch after set for " + request.playerId());
        }
        return new MutationOutcome<>(
            new InventoryMutationResult(InventoryMutationStatus.SET, request.boosterId(), previous, next, Optional.empty()),
            Optional.of(snapshot)
        );
    }

    private MutationOutcome<ClaimResult> claim(
        Connection connection,
        ClaimRequest request,
        ResolvedInventoryCapacity capacity
    ) throws SQLException {
        this.storage.revisions().revisionForUpdate(connection, request.playerId());
        Optional<BoosterClaim> optionalClaim = this.storage.claims().findForUpdate(connection, request.claimId());
        if (optionalClaim.isEmpty() || !optionalClaim.orElseThrow().playerId().equals(request.playerId())) {
            return new MutationOutcome<>(new ClaimResult(ClaimResultStatus.NOT_FOUND, Optional.empty(), 0), Optional.empty());
        }
        BoosterClaim pending = optionalClaim.orElseThrow();
        if (pending.status() == ClaimStatus.CLAIMED) {
            return new MutationOutcome<>(
                new ClaimResult(
                    ClaimResultStatus.ALREADY_CLAIMED,
                    Optional.empty(),
                    this.amount(connection, request.playerId(), pending.boosterId())
                ),
                Optional.empty()
            );
        }
        long previous = this.amount(connection, request.playerId(), pending.boosterId());
        long total = this.storage.inventory().total(connection, request.playerId());
        if (overflows(previous, pending.amount())
            || overflows(total, pending.amount())
            || !this.capacityResolver.canReceive(total, pending.amount(), capacity)) {
            return new MutationOutcome<>(
                new ClaimResult(ClaimResultStatus.INVENTORY_LIMIT_REACHED, Optional.empty(), previous),
                Optional.empty()
            );
        }
        Instant claimedAt = currentDatabaseTime(connection);
        this.storage.inventory().add(connection, request.playerId(), pending.boosterId(), pending.amount());
        this.storage.claims().markClaimed(connection, pending.claimId(), claimedAt);
        long next = Math.addExact(previous, pending.amount());
        long revision = this.storage.revisions().increment(connection, request.playerId());
        this.audit(
            connection,
            UUID.randomUUID(),
            "CLAIM_REDEEMED",
            request.playerId(),
            pending.boosterId(),
            pending.amount(),
            previous,
            next,
            Optional.of(pending.claimId()),
            "SYSTEM",
            pending.source().name(),
            pending.sourceReference(),
            ClaimResultStatus.CLAIMED.name()
        );
        PlayerBoostSnapshot snapshot = this.storage.playerStates().loadSnapshot(connection, request.playerId());
        if (snapshot.revision() != revision) {
            throw new SQLException("Snapshot revision mismatch after claim for " + request.playerId());
        }
        BoosterClaim claimed = new BoosterClaim(
            pending.claimId(),
            pending.playerId(),
            pending.boosterId(),
            pending.amount(),
            pending.source(),
            pending.sourceReference(),
            pending.createdAt(),
            Optional.of(claimedAt),
            ClaimStatus.CLAIMED
        );
        return new MutationOutcome<>(
            new ClaimResult(ClaimResultStatus.CLAIMED, Optional.of(claimed), next),
            Optional.of(snapshot)
        );
    }

    private InventoryMutationResult publishInventoryResult(MutationOutcome<InventoryMutationResult> outcome) {
        outcome.snapshot().ifPresent(snapshot -> {
            InventoryMutationResult result = outcome.result();
            PostCommitChange change = result.status() == InventoryMutationStatus.CLAIM_CREATED
                ? new PostCommitChange.ClaimCreated(snapshot, result.claim().orElseThrow())
                : new PostCommitChange.InventoryChanged(
                    snapshot,
                    result.boosterId(),
                    result.previousAmount(),
                    result.newAmount(),
                    cause(result.status()),
                    Optional.empty()
                );
            this.publishPostCommit(result.status().name(), java.util.List.of(change));
        });
        return outcome.result();
    }

    private ClaimResult publishClaimResult(MutationOutcome<ClaimResult> outcome) {
        outcome.snapshot().ifPresent(snapshot -> {
            ClaimResult result = outcome.result();
            result.claim().ifPresent(claim -> this.publishPostCommit(
                result.status().name(),
                java.util.List.of(
                    new PostCommitChange.InventoryChanged(
                        snapshot,
                        claim.boosterId(),
                        result.inventoryAmount() - claim.amount(),
                        result.inventoryAmount(),
                        InventoryChangeCause.CLAIM,
                        Optional.of(claim.claimId())
                    ),
                    new PostCommitChange.ClaimCompleted(snapshot, claim, result.inventoryAmount())
                )
            ));
        });
        return outcome.result();
    }

    private void publishPostCommit(String result, java.util.List<PostCommitChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        try {
            this.postCommit.publish(new PostCommitMutation<>(result, changes));
        } catch (RuntimeException exception) {
            LOGGER.error(
                "NetworkBoosters inventory post-commit publication failed result={} changes={}",
                result,
                changes.size(),
                exception
            );
        }
    }

    private void logInventoryFailure(String operation, UUID playerId, BoosterId boosterId, long amount, String source, Throwable failure) {
        LOGGER.error(
            "NetworkBoosters inventory mutation failed operation={} player={} booster={} amount={} source={}",
            operation,
            playerId,
            boosterId.value(),
            amount,
            source,
            rootCause(failure)
        );
    }

    private static Throwable rootCause(Throwable failure) {
        if (failure instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return failure;
    }

    private static InventoryChangeCause cause(InventoryMutationStatus status) {
        return switch (status) {
            case GRANTED, GRANTED_FORCED -> InventoryChangeCause.GRANT;
            case REVOKED -> InventoryChangeCause.REVOKE;
            case SET -> InventoryChangeCause.SET;
            default -> InventoryChangeCause.GRANT;
        };
    }

    private Optional<BoosterDefinition> definition(BoosterId boosterId) {
        return this.configurationStore.requireCurrent().definitions().find(boosterId);
    }

    private ResolvedInventoryCapacity resolveCapacity(UUID playerId) {
        return this.capacityProvider.resolve(playerId, this.configurationStore.requireCurrent().configuration().inventoryLimits());
    }

    private boolean canForce(InventoryGrantRequest request) {
        return this.canForce(request.source(), request.sourceReference());
    }

    private boolean canForce(MutationSource source, SourceReference reference) {
        if (source != MutationSource.ADMIN_COMMAND) {
            return false;
        }
        Optional<UUID> actorId = reference.actorId();
        if (actorId.isEmpty()) {
            return true;
        }
        Player actor = this.server.getPlayer(actorId.orElseThrow());
        return actor != null && actor.hasPermission(FORCE_PERMISSION);
    }

    private long amount(Connection connection, UUID playerId, BoosterId boosterId) throws SQLException {
        return this.storage.inventory().amount(connection, playerId, boosterId).orElse(0L);
    }

    private void audit(
        Connection connection,
        UUID operationId,
        String operationType,
        UUID playerId,
        BoosterId boosterId,
        long amount,
        long previous,
        long next,
        Optional<UUID> claimId,
        String actorType,
        String sourceType,
        SourceReference sourceReference,
        String result
    ) throws SQLException {
        this.storage.auditLog().insert(connection, new AuditEntry(
            operationId,
            operationType,
            actorType,
            sourceReference.actorId(),
            playerId,
            Optional.of(boosterId),
            Optional.of(amount),
            Optional.of("{\"amount\":" + previous + "}"),
            Optional.of("{\"amount\":" + next + "}"),
            claimId,
            Optional.empty(),
            Optional.empty(),
            sourceType,
            sourceReference.externalReference(),
            sourceReference.serverId(),
            result
        ));
    }

    private static String actorType(MutationSource source, SourceReference reference) {
        if (reference.actorId().isPresent()) {
            return "PLAYER";
        }
        return source == MutationSource.ADMIN_COMMAND ? "CONSOLE" : "SYSTEM";
    }

    static Optional<ClaimSource> claimSource(MutationSource source) {
        return switch (source) {
            case PURCHASE -> Optional.of(ClaimSource.PURCHASE);
            case COMPENSATION -> Optional.of(ClaimSource.COMPENSATION);
            case ADMIN_COMMAND, CRATE, BATTLE_PASS, EVENT, DAILY_REWARD, SYSTEM -> Optional.empty();
        };
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

    private static boolean overflows(long left, long right) {
        try {
            Math.addExact(left, right);
            return false;
        } catch (ArithmeticException exception) {
            return true;
        }
    }

    private static String operation(InventoryMutationStatus status) {
        return switch (status) {
            case GRANTED -> "INVENTORY_GRANT";
            case GRANTED_FORCED -> "INVENTORY_GRANT_FORCED";
            case CLAIM_CREATED -> "CLAIM_CREATED";
            default -> status.name();
        };
    }

    private static InventoryMutationResult result(InventoryMutationStatus status, BoosterId boosterId, long previous, long next) {
        return new InventoryMutationResult(status, boosterId, previous, next, Optional.empty());
    }

    private record MutationOutcome<T>(T result, Optional<PlayerBoostSnapshot> snapshot) {
        private MutationOutcome {
            Objects.requireNonNull(result, "result");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }
    }
}
