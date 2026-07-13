package com.stephanofer.networkboosters.transfer;

import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.TransferPolicy;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.request.BoosterTransferRequest;
import com.stephanofer.networkboosters.api.result.TransferResult;
import com.stephanofer.networkboosters.api.result.TransferStatus;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.api.source.TransferSource;
import com.stephanofer.networkboosters.capacity.InventoryCapacityResolver;
import com.stephanofer.networkboosters.capacity.ResolvedInventoryCapacity;
import com.stephanofer.networkboosters.config.ConfigurationSnapshot;
import com.stephanofer.networkboosters.config.ConfigurationStore;
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
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class BoosterTransferService implements AutoCloseable {

    private final BoosterStorage storage;
    private final PlayerSnapshotCache snapshots;
    private final ConfigurationStore configurationStore;
    private final Server server;
    private final Plugin plugin;
    private final PostCommitSynchronizer postCommit;
    private final InventoryCapacityResolver capacityResolver = new InventoryCapacityResolver();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public BoosterTransferService(
        BoosterStorage storage,
        PlayerSnapshotCache snapshots,
        ConfigurationStore configurationStore,
        Server server,
        Plugin plugin,
        PostCommitSynchronizer postCommit
    ) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.configurationStore = Objects.requireNonNull(configurationStore, "configurationStore");
        this.server = Objects.requireNonNull(server, "server");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.postCommit = Objects.requireNonNull(postCommit, "postCommit");
    }

    public CompletableFuture<TransferResult> transfer(BoosterTransferRequest request) {
        Objects.requireNonNull(request, "request");
        if (!this.accepting.get()) {
            return CompletableFuture.completedFuture(this.result(request, TransferStatus.SERVICE_UNAVAILABLE));
        }
        return this.prepare(request)
            .thenCompose(prepared -> this.revalidateAndWrite(request, prepared))
            .exceptionally(ignored -> this.result(request, TransferStatus.SERVICE_UNAVAILABLE));
    }

    private CompletableFuture<PreparedTransfer> prepare(BoosterTransferRequest request) {
        if (Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(this.prepareNow(request));
        }
        CompletableFuture<PreparedTransfer> result = new CompletableFuture<>();
        this.server.getScheduler().runTask(this.plugin, () -> {
            try {
                result.complete(this.prepareNow(request));
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result;
    }

    private PreparedTransfer prepareNow(BoosterTransferRequest request) {
        if (!this.accepting.get()) {
            return PreparedTransfer.rejected(this.result(request, TransferStatus.SERVICE_UNAVAILABLE));
        }
        if (request.senderId().equals(request.recipientId())) {
            return PreparedTransfer.rejected(this.result(request, TransferStatus.SAME_PLAYER));
        }

        ConfigurationSnapshot configuration = this.configurationStore.requireCurrent();
        Optional<BoosterDefinition> optionalDefinition = configuration.definitions().find(request.boosterId());
        if (optionalDefinition.isEmpty() || !optionalDefinition.orElseThrow().enabled()) {
            return PreparedTransfer.rejected(this.result(request, TransferStatus.NOT_TRANSFERABLE));
        }
        BoosterDefinition definition = optionalDefinition.orElseThrow();
        TransferPolicy policy = definition.transferPolicy();
        if (!policy.enabled()) {
            return PreparedTransfer.rejected(this.result(request, TransferStatus.NOT_TRANSFERABLE));
        }
        if (request.amount() < policy.minimumAmount() || request.amount() > policy.maximumAmount()) {
            return PreparedTransfer.rejected(this.result(request, TransferStatus.INVALID_AMOUNT));
        }

        Player sender = this.server.getPlayer(request.senderId());
        if (sender == null || !sender.isOnline()) {
            return PreparedTransfer.rejected(this.result(request, TransferStatus.PLAYER_NOT_READY));
        }
        Player recipient = this.server.getPlayer(request.recipientId());
        if (recipient == null || !recipient.isOnline()) {
            return PreparedTransfer.rejected(this.result(request, TransferStatus.RECIPIENT_NOT_ONLINE));
        }
        if (!this.snapshots.isReady(request.senderId()) || !this.snapshots.isReady(request.recipientId())) {
            return PreparedTransfer.rejected(this.result(request, TransferStatus.PLAYER_NOT_READY));
        }
        if (!this.transferPermissionSatisfied(request, policy, sender)) {
            return PreparedTransfer.rejected(this.result(request, TransferStatus.PERMISSION_DENIED));
        }

        ResolvedInventoryCapacity recipientCapacity = this.capacityResolver.resolve(
            configuration.configuration().inventoryLimits().fallback(),
            configuration.configuration().inventoryLimits().tiers(),
            recipient::hasPermission
        );
        return PreparedTransfer.allowed(definition, recipientCapacity);
    }

    private CompletableFuture<TransferResult> revalidateAndWrite(BoosterTransferRequest request, PreparedTransfer prepared) {
        if (prepared.rejected().isPresent()) {
            return CompletableFuture.completedFuture(prepared.rejected().orElseThrow());
        }
        return this.prepare(request).thenCompose(confirmed -> {
            if (confirmed.rejected().isPresent()) {
                return CompletableFuture.completedFuture(confirmed.rejected().orElseThrow());
            }
            return this.storage.write(connection -> this.transfer(connection, request, confirmed))
                .thenApply(this::publishTransfer);
        });
    }

    private MutationOutcome transfer(Connection connection, BoosterTransferRequest request, PreparedTransfer prepared) throws SQLException {
        if (!this.accepting.get()) {
            return MutationOutcome.rejected(this.result(request, TransferStatus.SERVICE_UNAVAILABLE));
        }
        BoosterDefinition definition = prepared.definition().orElseThrow();
        TransferPolicy policy = definition.transferPolicy();
        Instant now = currentDatabaseTime(connection);

        this.lockPlayers(connection, request.senderId(), request.recipientId());

        Optional<Instant> latestTransfer = this.storage.transfers().latestSuccessfulTransferAt(
            connection,
            request.senderId(),
            request.boosterId()
        );
        if (latestTransfer.isPresent() && !policy.cooldown().isZero()) {
            Instant retryAt = plus(latestTransfer.orElseThrow(), policy.cooldown());
            if (retryAt.isAfter(now)) {
                return MutationOutcome.rejected(this.result(request, TransferStatus.COOLDOWN, Optional.empty(), Optional.of(retryAt)));
            }
        }

        long senderPrevious = this.storage.inventory().amountForUpdate(connection, request.senderId(), request.boosterId()).orElse(0L);
        if (senderPrevious < request.amount()) {
            return MutationOutcome.rejected(this.result(
                request,
                TransferStatus.INSUFFICIENT_AMOUNT,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.of(senderPrevious),
                OptionalLong.empty()
            ));
        }

        long recipientPrevious = this.storage.inventory().amountForUpdate(connection, request.recipientId(), request.boosterId()).orElse(0L);
        long recipientTotal = this.storage.inventory().total(connection, request.recipientId());
        if (overflows(recipientPrevious, request.amount())
            || overflows(recipientTotal, request.amount())
            || !this.capacityResolver.canReceive(recipientTotal, request.amount(), prepared.recipientCapacity().orElseThrow())) {
            return MutationOutcome.rejected(this.result(
                request,
                TransferStatus.RECIPIENT_LIMIT_REACHED,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                OptionalLong.of(recipientPrevious)
            ));
        }

        if (!this.storage.inventory().decrement(connection, request.senderId(), request.boosterId(), request.amount())) {
            return MutationOutcome.rejected(this.result(
                request,
                TransferStatus.INSUFFICIENT_AMOUNT,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.of(senderPrevious),
                OptionalLong.empty()
            ));
        }
        this.storage.inventory().add(connection, request.recipientId(), request.boosterId(), request.amount());

        long senderNext = senderPrevious - request.amount();
        long recipientNext = Math.addExact(recipientPrevious, request.amount());
        UUID transferId = UUID.randomUUID();
        this.storage.transfers().insert(connection, new TransferRepository.StoredTransfer(
            transferId,
            request.senderId(),
            request.recipientId(),
            request.boosterId(),
            request.amount(),
            request.source(),
            request.sourceReference(),
            now,
            TransferStatus.TRANSFERRED
        ));

        UUID operationId = UUID.randomUUID();
        this.auditTransfer(connection, operationId, transferId, request, "BOOSTER_TRANSFER_DEBIT", request.senderId(), senderPrevious, senderNext);
        this.auditTransfer(connection, operationId, transferId, request, "BOOSTER_TRANSFER_CREDIT", request.recipientId(), recipientPrevious, recipientNext);

        long senderRevision = this.storage.revisions().increment(connection, request.senderId());
        long recipientRevision = this.storage.revisions().increment(connection, request.recipientId());
        PlayerBoostSnapshot senderSnapshot = this.storage.playerStates().loadSnapshot(connection, request.senderId());
        PlayerBoostSnapshot recipientSnapshot = this.storage.playerStates().loadSnapshot(connection, request.recipientId());
        if (senderSnapshot.revision() != senderRevision) {
            throw new SQLException("Snapshot revision mismatch after transfer for sender " + request.senderId());
        }
        if (recipientSnapshot.revision() != recipientRevision) {
            throw new SQLException("Snapshot revision mismatch after transfer for recipient " + request.recipientId());
        }

        TransferResult result = this.result(
            request,
            TransferStatus.TRANSFERRED,
            Optional.of(transferId),
            Optional.empty(),
            OptionalLong.of(senderNext),
            OptionalLong.of(recipientNext)
        );
        return new MutationOutcome(result, Optional.of(senderSnapshot), Optional.of(recipientSnapshot));
    }

    private void lockPlayers(Connection connection, UUID senderId, UUID recipientId) throws SQLException {
        if (compareUuidBinary(senderId, recipientId) <= 0) {
            this.storage.revisions().revisionForUpdate(connection, senderId);
            this.storage.revisions().revisionForUpdate(connection, recipientId);
            return;
        }
        this.storage.revisions().revisionForUpdate(connection, recipientId);
        this.storage.revisions().revisionForUpdate(connection, senderId);
    }

    static int compareUuidBinary(UUID left, UUID right) {
        int most = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        if (most != 0) {
            return most;
        }
        return Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    private boolean transferPermissionSatisfied(BoosterTransferRequest request, TransferPolicy policy, Player sender) {
        if (request.source() != TransferSource.PLAYER_COMMAND && request.source() != TransferSource.PLAYER_MENU) {
            return true;
        }
        Optional<UUID> actorId = request.sourceReference().actorId();
        if (actorId.isEmpty() || !actorId.orElseThrow().equals(request.senderId())) {
            return false;
        }
        return policy.permission().map(sender::hasPermission).orElse(true);
    }

    private TransferResult publishTransfer(MutationOutcome outcome) {
        if (outcome.senderSnapshot().isPresent() && outcome.recipientSnapshot().isPresent()) {
            this.postCommit.publish(new PostCommitMutation<>(outcome.result(), java.util.List.of(new PostCommitChange.TransferCompleted(
                outcome.result(),
                outcome.senderSnapshot().orElseThrow(),
                outcome.recipientSnapshot().orElseThrow()
            ))));
        }
        return outcome.result();
    }

    private void auditTransfer(
        Connection connection,
        UUID operationId,
        UUID transferId,
        BoosterTransferRequest request,
        String operation,
        UUID affectedPlayerId,
        long previous,
        long next
    ) throws SQLException {
        SourceReference sourceReference = request.sourceReference();
        this.storage.auditLog().insert(connection, new AuditEntry(
            operationId,
            operation,
            actorType(request.source(), sourceReference),
            sourceReference.actorId(),
            affectedPlayerId,
            Optional.of(request.boosterId()),
            Optional.of(request.amount()),
            Optional.of("{\"amount\":" + previous + "}"),
            Optional.of("{\"amount\":" + next + "}"),
            Optional.empty(),
            Optional.empty(),
            Optional.of(transferId),
            request.source().name(),
            sourceReference.externalReference(),
            sourceReference.serverId(),
            TransferStatus.TRANSFERRED.name()
        ));
    }

    private static String actorType(TransferSource source, SourceReference reference) {
        if (reference.actorId().isPresent()) {
            return "PLAYER";
        }
        return source == TransferSource.ADMIN_COMMAND ? "CONSOLE" : "SYSTEM";
    }

    private TransferResult result(BoosterTransferRequest request, TransferStatus status) {
        return this.result(request, status, Optional.empty(), Optional.empty());
    }

    private TransferResult result(
        BoosterTransferRequest request,
        TransferStatus status,
        Optional<UUID> transferId,
        Optional<Instant> retryAt
    ) {
        return this.result(request, status, transferId, retryAt, OptionalLong.empty(), OptionalLong.empty());
    }

    private TransferResult result(
        BoosterTransferRequest request,
        TransferStatus status,
        Optional<UUID> transferId,
        Optional<Instant> retryAt,
        OptionalLong senderRemainingAmount,
        OptionalLong recipientResultingAmount
    ) {
        return new TransferResult(
            status,
            request.senderId(),
            request.recipientId(),
            request.boosterId(),
            request.amount(),
            transferId,
            retryAt,
            senderRemainingAmount,
            recipientResultingAmount
        );
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

    private static Instant plus(Instant start, java.time.Duration duration) throws SQLException {
        try {
            return start.plus(duration);
        } catch (ArithmeticException exception) {
            throw new SQLException("Instant overflow while calculating transfer cooldown", exception);
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

    @Override
    public void close() {
        this.accepting.set(false);
    }

    private record PreparedTransfer(
        Optional<TransferResult> rejected,
        Optional<BoosterDefinition> definition,
        Optional<ResolvedInventoryCapacity> recipientCapacity
    ) {

        private PreparedTransfer {
            rejected = Objects.requireNonNull(rejected, "rejected");
            definition = Objects.requireNonNull(definition, "definition");
            recipientCapacity = Objects.requireNonNull(recipientCapacity, "recipientCapacity");
        }

        private static PreparedTransfer rejected(TransferResult result) {
            return new PreparedTransfer(Optional.of(result), Optional.empty(), Optional.empty());
        }

        private static PreparedTransfer allowed(BoosterDefinition definition, ResolvedInventoryCapacity recipientCapacity) {
            return new PreparedTransfer(Optional.empty(), Optional.of(definition), Optional.of(recipientCapacity));
        }
    }

    private record MutationOutcome(
        TransferResult result,
        Optional<PlayerBoostSnapshot> senderSnapshot,
        Optional<PlayerBoostSnapshot> recipientSnapshot
    ) {

        private MutationOutcome {
            Objects.requireNonNull(result, "result");
            senderSnapshot = Objects.requireNonNull(senderSnapshot, "senderSnapshot");
            recipientSnapshot = Objects.requireNonNull(recipientSnapshot, "recipientSnapshot");
        }

        private static MutationOutcome rejected(TransferResult result) {
            return new MutationOutcome(result, Optional.empty(), Optional.empty());
        }
    }
}
