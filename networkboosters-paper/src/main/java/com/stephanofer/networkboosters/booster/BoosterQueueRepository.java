package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.persistence.JdbcUuid;
import com.stephanofer.networkboosters.persistence.PlayerSnapshotMapper;
import com.stephanofer.networkboosters.persistence.SnapshotJsonCodec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class BoosterQueueRepository {

    private final String table;
    private final PlayerSnapshotMapper mapper;
    private final SnapshotJsonCodec json;

    public BoosterQueueRepository(String table, PlayerSnapshotMapper mapper, SnapshotJsonCodec json) {
        this.table = Objects.requireNonNull(table, "table");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.json = Objects.requireNonNull(json, "json");
    }

    public List<QueuedBooster> findGroupForUpdate(
        Connection connection,
        UUID playerId,
        ActivationGroup group
    ) throws SQLException {
        ArrayList<QueuedBooster> queue = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT queue_id, player_uuid, activation_group, position, booster_id, target_key,
                   multiplier, conflict_policy, scope_type, game_scopes, server_scopes,
                   requirement_mode, requirement_permissions, duration_millis, queued_at,
                   source_type, actor_uuid, source_reference, source_server_id
            FROM %s
            WHERE player_uuid = ? AND activation_group = ?
            ORDER BY position
            FOR UPDATE
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, playerId);
            statement.setString(2, group.value());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    queue.add(this.mapper.queuedBooster(result, playerId));
                }
            }
        }
        return queue;
    }

    public void insert(Connection connection, QueuedBooster booster) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO %s (
                queue_id, player_uuid, activation_group, position, booster_id, target_key,
                multiplier, conflict_policy, scope_type, game_scopes, server_scopes,
                requirement_mode, requirement_permissions, duration_millis, queued_at,
                source_type, actor_uuid, source_reference, source_server_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, booster.queueId());
            JdbcUuid.set(statement, 2, booster.playerId());
            statement.setString(3, booster.activationGroup().value());
            statement.setLong(4, booster.position());
            statement.setString(5, booster.boosterId().value());
            statement.setString(6, booster.target().key());
            statement.setBigDecimal(7, booster.multiplier());
            statement.setString(8, booster.conflictPolicy().name());
            statement.setString(9, booster.scope().type().name());
            statement.setString(10, this.json.writeStringArray(booster.scope().gameIds()));
            statement.setString(11, this.json.writeStringArray(booster.scope().serverIds()));
            statement.setString(12, booster.requirements().mode().name());
            statement.setString(13, this.json.writeStringArray(booster.requirements().permissions()));
            statement.setLong(14, durationMillis(booster.duration()));
            statement.setTimestamp(15, Timestamp.from(booster.queuedAt()));
            statement.setString(16, booster.source().name());
            JdbcUuid.setNullable(statement, 17, booster.sourceReference().actorId().orElse(null));
            statement.setString(18, booster.sourceReference().externalReference().orElse(null));
            statement.setString(19, booster.sourceReference().serverId().orElse(null));
            statement.executeUpdate();
        }
    }

    public void updateDuration(Connection connection, UUID queueId, Duration duration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + this.table + " SET duration_millis = ? WHERE queue_id = ?"
        )) {
            statement.setLong(1, durationMillis(duration));
            JdbcUuid.set(statement, 2, queueId);
            expectOne(statement.executeUpdate(), "update queue duration " + queueId);
        }
    }

    public void delete(Connection connection, UUID queueId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + this.table + " WHERE queue_id = ?")) {
            JdbcUuid.set(statement, 1, queueId);
            expectOne(statement.executeUpdate(), "delete queue entry " + queueId);
        }
    }

    public void deleteAll(Connection connection, Collection<UUID> queueIds) throws SQLException {
        for (UUID queueId : queueIds) {
            this.delete(connection, queueId);
        }
    }

    public static long nextPosition(List<QueuedBooster> queue) {
        Objects.requireNonNull(queue, "queue");
        if (queue.isEmpty()) {
            return 0;
        }
        return Math.addExact(queue.get(queue.size() - 1).position(), 1);
    }

    private static long durationMillis(Duration duration) throws SQLException {
        try {
            return Objects.requireNonNull(duration, "duration").toMillis();
        } catch (ArithmeticException exception) {
            throw new SQLException("Duration cannot be represented as milliseconds", exception);
        }
    }

    private static void expectOne(int updated, String operation) throws SQLException {
        if (updated != 1) {
            throw new SQLException("Expected to " + operation + ", updated " + updated);
        }
    }
}
