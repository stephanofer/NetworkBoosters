package com.stephanofer.networkboosters.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class PlayerRevisionRepository {

    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

    private final String table;

    public PlayerRevisionRepository(String table) {
        this.table = table;
    }

    public long revision(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT revision FROM " + this.table + " WHERE player_uuid = ?"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return 0;
                }
                return readRevision(result, "revision", playerId);
            }
        }
    }

    public long revisionForUpdate(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT revision FROM " + this.table + " WHERE player_uuid = ? FOR UPDATE"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return 0;
                }
                return readRevision(result, "revision", playerId);
            }
        }
    }

    public long increment(Connection connection, UUID playerId) throws SQLException {
        long current = this.revisionForUpdate(connection, playerId);
        if (current == Long.MAX_VALUE) {
            throw new SQLException("Player revision overflow for " + playerId);
        }

        long next = current + 1;
        if (current == 0) {
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + this.table + " (player_uuid, revision) VALUES (?, ?)"
            )) {
                JdbcUuid.set(statement, 1, playerId);
                statement.setLong(2, next);
                statement.executeUpdate();
            }
            return next;
        }

        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + this.table + " SET revision = ? WHERE player_uuid = ?"
        )) {
            statement.setLong(1, next);
            JdbcUuid.set(statement, 2, playerId);
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Expected to update one revision row for " + playerId + ", updated " + updated);
            }
        }
        return next;
    }

    private static long readRevision(ResultSet result, String column, UUID playerId) throws SQLException {
        BigDecimal value = result.getBigDecimal(column);
        if (value == null || value.signum() < 0 || value.compareTo(LONG_MAX) > 0) {
            throw new PersistenceException("Invalid revision for player " + playerId + ": " + value);
        }
        return value.longValueExact();
    }
}
