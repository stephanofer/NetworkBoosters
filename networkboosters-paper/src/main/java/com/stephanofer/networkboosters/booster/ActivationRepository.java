package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.persistence.JdbcUuid;
import com.stephanofer.networkboosters.persistence.PlayerSnapshotMapper;
import com.stephanofer.networkboosters.persistence.SnapshotJsonCodec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ActivationRepository {

    private final String table;
    private final PlayerSnapshotMapper mapper;
    private final SnapshotJsonCodec json;

    public ActivationRepository(String table, PlayerSnapshotMapper mapper, SnapshotJsonCodec json) {
        this.table = Objects.requireNonNull(table, "table");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.json = Objects.requireNonNull(json, "json");
    }

    public Optional<ActiveBooster> findActiveForUpdate(
        Connection connection,
        UUID playerId,
        ActivationGroup group
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT activation_id, player_uuid, booster_id, target_key, multiplier, activation_group,
                   conflict_policy, scope_type, game_scopes, server_scopes, requirement_mode,
                   requirement_permissions, activated_at, expires_at, source_type, actor_uuid,
                   source_reference, source_server_id
            FROM %s
            WHERE player_uuid = ? AND activation_group = ? AND status = 'ACTIVE'
            FOR UPDATE
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, playerId);
            statement.setString(2, group.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(this.mapper.activeBooster(result, playerId));
            }
        }
    }

    public Optional<ActiveBooster> findByIdForUpdate(Connection connection, UUID activationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT activation_id, player_uuid, booster_id, target_key, multiplier, activation_group,
                   conflict_policy, scope_type, game_scopes, server_scopes, requirement_mode,
                   requirement_permissions, activated_at, expires_at, source_type, actor_uuid,
                   source_reference, source_server_id, status
            FROM %s
            WHERE activation_id = ?
            FOR UPDATE
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, activationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !"ACTIVE".equals(result.getString("status"))) {
                    return Optional.empty();
                }
                return Optional.of(this.mapper.activeBooster(result, JdbcUuid.get(result, "player_uuid")));
            }
        }
    }

    public Optional<StoredActivationStatus> findStatusById(Connection connection, UUID activationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT player_uuid, status FROM " + this.table + " WHERE activation_id = ?"
        )) {
            JdbcUuid.set(statement, 1, activationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredActivationStatus(JdbcUuid.get(result, "player_uuid"), result.getString("status")));
            }
        }
    }

    public void insertActive(Connection connection, ActiveBooster booster) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO %s (
                activation_id, player_uuid, booster_id, target_key, multiplier, activation_group,
                conflict_policy, scope_type, game_scopes, server_scopes, requirement_mode,
                requirement_permissions, activated_at, expires_at, status, source_type,
                actor_uuid, source_reference, source_server_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, booster.activationId());
            JdbcUuid.set(statement, 2, booster.playerId());
            statement.setString(3, booster.boosterId().value());
            statement.setString(4, booster.target().key());
            statement.setBigDecimal(5, booster.multiplier());
            statement.setString(6, booster.activationGroup().value());
            statement.setString(7, booster.conflictPolicy().name());
            statement.setString(8, booster.scope().type().name());
            statement.setString(9, this.json.writeStringArray(booster.scope().gameIds()));
            statement.setString(10, this.json.writeStringArray(booster.scope().serverIds()));
            statement.setString(11, booster.requirements().mode().name());
            statement.setString(12, this.json.writeStringArray(booster.requirements().permissions()));
            statement.setTimestamp(13, Timestamp.from(booster.activatedAt()));
            statement.setTimestamp(14, Timestamp.from(booster.expiresAt()));
            statement.setString(15, booster.source().name());
            JdbcUuid.setNullable(statement, 16, booster.sourceReference().actorId().orElse(null));
            statement.setString(17, booster.sourceReference().externalReference().orElse(null));
            statement.setString(18, booster.sourceReference().serverId().orElse(null));
            statement.executeUpdate();
        }
    }

    public void extend(Connection connection, UUID activationId, Instant expiresAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + this.table + " SET expires_at = ? WHERE activation_id = ? AND status = 'ACTIVE'"
        )) {
            statement.setTimestamp(1, Timestamp.from(expiresAt));
            JdbcUuid.set(statement, 2, activationId);
            expectOne(statement.executeUpdate(), "extend activation " + activationId);
        }
    }

    public void markExpired(Connection connection, UUID activationId) throws SQLException {
        this.mark(connection, activationId, "EXPIRED");
    }

    public void markDeactivated(Connection connection, UUID activationId) throws SQLException {
        this.mark(connection, activationId, "DEACTIVATED");
    }

    public List<ExpiredActivationCandidate> findExpiredCandidates(Connection connection, int limit) throws SQLException {
        return this.findExpiredCandidates(connection, null, limit, null);
    }

    public List<ExpiredActivationCandidate> findExpiredCandidatesForPlayer(
        Connection connection,
        UUID playerId,
        Instant now
    ) throws SQLException {
        return this.findExpiredCandidates(connection, now, Integer.MAX_VALUE, playerId);
    }

    private List<ExpiredActivationCandidate> findExpiredCandidates(
        Connection connection,
        Instant now,
        int limit,
        UUID playerId
    ) throws SQLException {
        ArrayList<ExpiredActivationCandidate> candidates = new ArrayList<>();
        String playerPredicate = playerId == null ? "" : " AND player_uuid = ?";
        String timestampExpression = now == null ? "CURRENT_TIMESTAMP(3)" : "?";
        try (PreparedStatement statement = connection.prepareStatement(("""
            SELECT activation_id, player_uuid, activation_group
            FROM %s
            WHERE status = 'ACTIVE' AND expires_at <= %s%s
            ORDER BY expires_at, activation_id
            LIMIT ?
            """).formatted(this.table, timestampExpression, playerPredicate))) {
            int parameter = 1;
            if (now != null) {
                statement.setTimestamp(parameter++, Timestamp.from(now));
            }
            if (playerId != null) {
                JdbcUuid.set(statement, parameter++, playerId);
            }
            statement.setInt(parameter, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    candidates.add(new ExpiredActivationCandidate(
                        JdbcUuid.get(result, "activation_id"),
                        JdbcUuid.get(result, "player_uuid"),
                        ActivationGroup.of(result.getString("activation_group"))
                    ));
                }
            }
        }
        return candidates;
    }

    private void mark(Connection connection, UUID activationId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + this.table + " SET status = ? WHERE activation_id = ? AND status = 'ACTIVE'"
        )) {
            statement.setString(1, status);
            JdbcUuid.set(statement, 2, activationId);
            expectOne(statement.executeUpdate(), "mark activation " + activationId + " as " + status);
        }
    }

    private static void expectOne(int updated, String operation) throws SQLException {
        if (updated != 1) {
            throw new SQLException("Expected to " + operation + ", updated " + updated);
        }
    }

    public record ExpiredActivationCandidate(UUID activationId, UUID playerId, ActivationGroup group) {

        public ExpiredActivationCandidate {
            Objects.requireNonNull(activationId, "activationId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(group, "group");
        }
    }

    public record StoredActivationStatus(UUID playerId, String status) {

        public StoredActivationStatus {
            Objects.requireNonNull(playerId, "playerId");
            status = Objects.requireNonNull(status, "status");
        }
    }
}
