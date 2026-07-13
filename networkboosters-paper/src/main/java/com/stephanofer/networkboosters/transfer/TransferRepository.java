package com.stephanofer.networkboosters.transfer;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.result.TransferStatus;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.api.source.TransferSource;
import com.stephanofer.networkboosters.persistence.JdbcUuid;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TransferRepository {

    private final String table;

    public TransferRepository(String table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    public Optional<Instant> latestSuccessfulTransferAt(Connection connection, UUID senderId, BoosterId boosterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT created_at FROM %s
            WHERE sender_uuid = ? AND booster_id = ? AND status = ?
            ORDER BY created_at DESC
            LIMIT 1
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, senderId);
            statement.setString(2, boosterId.value());
            statement.setString(3, TransferStatus.TRANSFERRED.name());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                Timestamp timestamp = result.getTimestamp("created_at");
                return timestamp == null ? Optional.empty() : Optional.of(timestamp.toInstant());
            }
        }
    }

    public void insert(Connection connection, StoredTransfer transfer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO %s (
                transfer_id, sender_uuid, recipient_uuid, booster_id, amount,
                source_type, actor_uuid, source_reference, source_server_id,
                created_at, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, transfer.transferId());
            JdbcUuid.set(statement, 2, transfer.senderId());
            JdbcUuid.set(statement, 3, transfer.recipientId());
            statement.setString(4, transfer.boosterId().value());
            statement.setLong(5, transfer.amount());
            statement.setString(6, transfer.source().name());
            JdbcUuid.setNullable(statement, 7, transfer.sourceReference().actorId().orElse(null));
            statement.setString(8, transfer.sourceReference().externalReference().orElse(null));
            statement.setString(9, transfer.sourceReference().serverId().orElse(null));
            statement.setTimestamp(10, Timestamp.from(transfer.createdAt()));
            statement.setString(11, transfer.status().name());
            statement.executeUpdate();
        }
    }

    public record StoredTransfer(
        UUID transferId,
        UUID senderId,
        UUID recipientId,
        BoosterId boosterId,
        long amount,
        TransferSource source,
        SourceReference sourceReference,
        Instant createdAt,
        TransferStatus status
    ) {

        public StoredTransfer {
            Objects.requireNonNull(transferId, "transferId");
            Objects.requireNonNull(senderId, "senderId");
            Objects.requireNonNull(recipientId, "recipientId");
            Objects.requireNonNull(boosterId, "boosterId");
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            Objects.requireNonNull(source, "source");
            sourceReference = Objects.requireNonNullElse(sourceReference, SourceReference.none());
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(status, "status");
        }
    }
}
