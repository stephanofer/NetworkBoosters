package com.stephanofer.networkboosters.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class AuditLogRepository {

    private final String table;

    public AuditLogRepository(String table) {
        this.table = table;
    }

    public void insert(Connection connection, AuditEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO %s (
                operation_id, operation_type, actor_type, actor_uuid, affected_player_uuid,
                booster_id, amount, previous_value, new_value, claim_id, activation_id, transfer_id,
                source_type, source_reference, source_server_id, result
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, entry.operationId());
            statement.setString(2, entry.operationType());
            statement.setString(3, entry.actorType());
            JdbcUuid.setNullable(statement, 4, entry.actorId().orElse(null));
            JdbcUuid.set(statement, 5, entry.affectedPlayerId());
            statement.setString(6, entry.boosterId().map(boosterId -> boosterId.value()).orElse(null));
            if (entry.amount().isPresent()) {
                statement.setLong(7, entry.amount().get());
            } else {
                statement.setObject(7, null);
            }
            statement.setString(8, entry.previousValueJson().orElse(null));
            statement.setString(9, entry.newValueJson().orElse(null));
            JdbcUuid.setNullable(statement, 10, entry.claimId().orElse(null));
            JdbcUuid.setNullable(statement, 11, entry.activationId().orElse(null));
            JdbcUuid.setNullable(statement, 12, entry.transferId().orElse(null));
            statement.setString(13, entry.sourceType());
            statement.setString(14, entry.sourceReference().orElse(null));
            statement.setString(15, entry.sourceServerId().orElse(null));
            statement.setString(16, entry.result());
            statement.executeUpdate();
        }
    }
}
