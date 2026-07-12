package com.stephanofer.networkboosters.persistence;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.TransactionOptions;
import com.hera.craftkit.database.TransactionRetryEvent;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.inventory.ClaimRepository;
import com.stephanofer.networkboosters.inventory.InventoryRepository;
import com.stephanofer.networkboosters.inventory.MutationReceiptRepository;
import com.stephanofer.networkboosters.persistence.transaction.BoosterTransactionOptions;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class BoosterStorage {

    private final Database database;
    private final PlayerStateRepository playerStates;
    private final PlayerRevisionRepository revisions;
    private final AuditLogRepository auditLog;
    private final InventoryRepository inventory;
    private final ClaimRepository claims;
    private final MutationReceiptRepository mutationReceipts;
    private final Consumer<TransactionRetryEvent> retryListener;

    public BoosterStorage(Database database, Consumer<TransactionRetryEvent> retryListener) {
        this.database = Objects.requireNonNull(database, "database");
        this.retryListener = Objects.requireNonNull(retryListener, "retryListener");
        SnapshotJsonCodec json = new SnapshotJsonCodec();
        PlayerSnapshotMapper mapper = new PlayerSnapshotMapper(json);
        this.revisions = new PlayerRevisionRepository(database.table("player_revision"));
        this.inventory = new InventoryRepository(database.table("inventory"));
        this.claims = new ClaimRepository(database.table("claims"), mapper);
        this.mutationReceipts = new MutationReceiptRepository(database.table("mutation_receipts"));
        this.playerStates = new PlayerStateRepository(
            database.table("inventory"),
            database.table("activations"),
            database.table("queue"),
            database.table("claims"),
            this.revisions,
            mapper
        );
        this.auditLog = new AuditLogRepository(database.table("audit_log"));
    }

    public CompletableFuture<PlayerBoostSnapshot> loadSnapshot(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return this.database.transaction(BoosterTransactionOptions.consistentRead(), connection ->
            this.playerStates.loadSnapshot(connection, playerId));
    }

    public <T> CompletableFuture<T> write(TransactionalOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        TransactionOptions options = BoosterTransactionOptions.retryingWrite(this.retryListener);
        return this.database.transaction(options, operation::execute);
    }

    public PlayerStateRepository playerStates() {
        return this.playerStates;
    }

    public PlayerRevisionRepository revisions() {
        return this.revisions;
    }

    public AuditLogRepository auditLog() {
        return this.auditLog;
    }

    public InventoryRepository inventory() {
        return this.inventory;
    }

    public ClaimRepository claims() {
        return this.claims;
    }

    public MutationReceiptRepository mutationReceipts() {
        return this.mutationReceipts;
    }

    @FunctionalInterface
    public interface TransactionalOperation<T> {
        T execute(Connection connection) throws SQLException;
    }
}
