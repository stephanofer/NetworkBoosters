package com.stephanofer.networkboosters.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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

    public Map<UUID, Long> revisions(Connection connection, Collection<UUID> playerIds) throws SQLException {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        HashMap<UUID, Long> revisions = new HashMap<>();
        playerIds.forEach(playerId -> revisions.put(playerId, 0L));
        String placeholders = String.join(",", java.util.Collections.nCopies(playerIds.size(), "?"));
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT player_uuid, revision FROM " + this.table + " WHERE player_uuid IN (" + placeholders + ")"
        )) {
            int index = 1;
            for (UUID playerId : playerIds) {
                JdbcUuid.set(statement, index++, playerId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    UUID playerId = JdbcUuid.get(result, "player_uuid");
                    revisions.put(playerId, readRevision(result, "revision", playerId));
                }
            }
        }
        return Map.copyOf(revisions);
    }

    public long revisionForUpdate(Connection connection, UUID playerId) throws SQLException {
        this.ensureRow(connection, playerId);
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT revision FROM " + this.table + " WHERE player_uuid = ? FOR UPDATE"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Expected revision row for " + playerId + " after initialization");
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

    private void ensureRow(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + this.table + " (player_uuid, revision) VALUES (?, 0) " +
                "ON DUPLICATE KEY UPDATE player_uuid = player_uuid"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            statement.executeUpdate();
        }
    }

    private static long readRevision(ResultSet result, String column, UUID playerId) throws SQLException {
        BigDecimal value = result.getBigDecimal(column);
        if (value == null || value.signum() < 0 || value.compareTo(LONG_MAX) > 0) {
            throw new PersistenceException("Invalid revision for player " + playerId + ": " + value);
        }
        return value.longValueExact();
    }
}
