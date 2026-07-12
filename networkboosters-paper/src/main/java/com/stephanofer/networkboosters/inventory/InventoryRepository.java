package com.stephanofer.networkboosters.inventory;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.persistence.JdbcUuid;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

public final class InventoryRepository {

    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

    private final String table;

    public InventoryRepository(String table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    public long total(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COALESCE(SUM(amount), 0) AS total FROM " + this.table + " WHERE player_uuid = ?"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return unsignedLong(result, "total");
            }
        }
    }

    public OptionalLong amount(Connection connection, UUID playerId, BoosterId boosterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT amount FROM " + this.table + " WHERE player_uuid = ? AND booster_id = ?"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            statement.setString(2, boosterId.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return OptionalLong.empty();
                }
                return OptionalLong.of(unsignedLong(result, "amount"));
            }
        }
    }

    public OptionalLong amountForUpdate(Connection connection, UUID playerId, BoosterId boosterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT amount FROM " + this.table + " WHERE player_uuid = ? AND booster_id = ? FOR UPDATE"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            statement.setString(2, boosterId.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return OptionalLong.empty();
                }
                return OptionalLong.of(unsignedLong(result, "amount"));
            }
        }
    }

    public boolean decrementOne(Connection connection, UUID playerId, BoosterId boosterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + this.table + " SET amount = amount - 1 WHERE player_uuid = ? AND booster_id = ? AND amount >= 1"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            statement.setString(2, boosterId.value());
            if (statement.executeUpdate() != 1) {
                return false;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM " + this.table + " WHERE player_uuid = ? AND booster_id = ? AND amount = 0"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            statement.setString(2, boosterId.value());
            statement.executeUpdate();
        }
        return true;
    }

    public void add(Connection connection, UUID playerId, BoosterId boosterId, long amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO %s (player_uuid, booster_id, amount)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, playerId);
            statement.setString(2, boosterId.value());
            statement.setLong(3, amount);
            statement.executeUpdate();
        }
    }

    public void set(Connection connection, UUID playerId, BoosterId boosterId, long amount) throws SQLException {
        if (amount == 0) {
            this.delete(connection, playerId, boosterId);
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO %s (player_uuid, booster_id, amount)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE amount = VALUES(amount)
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, playerId);
            statement.setString(2, boosterId.value());
            statement.setLong(3, amount);
            statement.executeUpdate();
        }
    }

    public void delete(Connection connection, UUID playerId, BoosterId boosterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM " + this.table + " WHERE player_uuid = ? AND booster_id = ?"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            statement.setString(2, boosterId.value());
            statement.executeUpdate();
        }
    }

    private static long unsignedLong(ResultSet result, String column) throws SQLException {
        BigDecimal value = result.getBigDecimal(column);
        if (value == null || value.signum() < 0 || value.compareTo(LONG_MAX) > 0) {
            throw new SQLException("Invalid unsigned long in " + column + ": " + value);
        }
        return value.longValueExact();
    }
}
