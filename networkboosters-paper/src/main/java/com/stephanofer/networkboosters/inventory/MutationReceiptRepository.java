package com.stephanofer.networkboosters.inventory;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.persistence.JdbcUuid;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MutationReceiptRepository {

    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

    private final String table;

    public MutationReceiptRepository(String table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    public Optional<MutationReceipt> findForUpdate(
        Connection connection,
        String operationType,
        String sourceType,
        String externalReference
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT receipt_id, operation_type, source_type, external_reference, player_uuid,
                   booster_id, amount, result, claim_id
            FROM %s
            WHERE operation_type = ? AND source_type = ? AND external_reference = ?
            FOR UPDATE
            """.formatted(this.table))) {
            statement.setString(1, operationType);
            statement.setString(2, sourceType);
            statement.setString(3, externalReference);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MutationReceipt(
                    JdbcUuid.get(result, "receipt_id"),
                    result.getString("operation_type"),
                    result.getString("source_type"),
                    result.getString("external_reference"),
                    JdbcUuid.get(result, "player_uuid"),
                    BoosterId.of(result.getString("booster_id")),
                    unsignedLong(result, "amount"),
                    result.getString("result"),
                    Optional.ofNullable(JdbcUuid.getNullable(result, "claim_id"))
                ));
            }
        }
    }

    public MutationReceipt reserve(
        Connection connection,
        String operationType,
        String sourceType,
        String externalReference,
        UUID playerId,
        BoosterId boosterId,
        long amount
    ) throws SQLException {
        UUID receiptId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO %s (
                receipt_id, operation_type, source_type, external_reference, player_uuid,
                booster_id, amount, result
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
            ON DUPLICATE KEY UPDATE receipt_id = receipt_id
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, receiptId);
            statement.setString(2, operationType);
            statement.setString(3, sourceType);
            statement.setString(4, externalReference);
            JdbcUuid.set(statement, 5, playerId);
            statement.setString(6, boosterId.value());
            statement.setLong(7, amount);
            statement.executeUpdate();
        }
        return this.findForUpdate(connection, operationType, sourceType, externalReference)
            .orElseThrow(() -> new SQLException("Reserved mutation receipt was not readable"));
    }

    public void complete(Connection connection, MutationReceipt receipt, String result, Optional<UUID> claimId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + this.table + " SET result = ?, claim_id = ? WHERE receipt_id = ? AND result = 'PENDING'"
        )) {
            statement.setString(1, result);
            JdbcUuid.setNullable(statement, 2, claimId.orElse(null));
            JdbcUuid.set(statement, 3, receipt.receiptId());
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Expected to complete one mutation receipt " + receipt.receiptId() + ", updated " + updated);
            }
        }
    }

    private static long unsignedLong(ResultSet result, String column) throws SQLException {
        BigDecimal value = result.getBigDecimal(column);
        if (value == null || value.signum() <= 0 || value.compareTo(LONG_MAX) > 0) {
            throw new SQLException("Invalid positive unsigned long in " + column + ": " + value);
        }
        return value.longValueExact();
    }
}
