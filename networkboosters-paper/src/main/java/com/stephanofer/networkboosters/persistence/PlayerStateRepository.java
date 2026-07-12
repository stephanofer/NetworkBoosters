package com.stephanofer.networkboosters.persistence;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerStateRepository {

    private final String inventoryTable;
    private final String activationsTable;
    private final String queueTable;
    private final String claimsTable;
    private final PlayerRevisionRepository revisions;
    private final PlayerSnapshotMapper mapper;

    public PlayerStateRepository(
        String inventoryTable,
        String activationsTable,
        String queueTable,
        String claimsTable,
        PlayerRevisionRepository revisions,
        PlayerSnapshotMapper mapper
    ) {
        this.inventoryTable = inventoryTable;
        this.activationsTable = activationsTable;
        this.queueTable = queueTable;
        this.claimsTable = claimsTable;
        this.revisions = revisions;
        this.mapper = mapper;
    }

    public PlayerBoostSnapshot loadSnapshot(Connection connection, UUID playerId) throws SQLException {
        long revision = this.revisions.revision(connection, playerId);
        Map<BoosterId, Long> inventory = this.loadInventory(connection, playerId);
        Map<ActivationGroup, ActiveBooster> activeBoosters = this.loadActiveBoosters(connection, playerId);
        Map<ActivationGroup, List<QueuedBooster>> queuedBoosters = this.loadQueuedBoosters(connection, playerId);
        List<BoosterClaim> pendingClaims = this.loadPendingClaims(connection, playerId);
        return new PlayerBoostSnapshot(playerId, revision, inventory, activeBoosters, queuedBoosters, pendingClaims);
    }

    private Map<BoosterId, Long> loadInventory(Connection connection, UUID playerId) throws SQLException {
        LinkedHashMap<BoosterId, Long> inventory = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT booster_id, amount FROM " + this.inventoryTable + " WHERE player_uuid = ? ORDER BY booster_id"
        )) {
            JdbcUuid.set(statement, 1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    inventory.put(
                        this.mapper.boosterId(result),
                        this.mapper.positiveLong(result, "amount", "inventory.amount")
                    );
                }
            }
        }
        return inventory;
    }

    private Map<ActivationGroup, ActiveBooster> loadActiveBoosters(Connection connection, UUID playerId) throws SQLException {
        LinkedHashMap<ActivationGroup, ActiveBooster> activeBoosters = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT activation_id, player_uuid, booster_id, target_key, multiplier, activation_group,
                   conflict_policy, scope_type, game_scopes, server_scopes, requirement_mode,
                   requirement_permissions, activated_at, expires_at, source_type, actor_uuid,
                   source_reference, source_server_id
            FROM %s
            WHERE player_uuid = ? AND status = 'ACTIVE'
            ORDER BY activation_group, activation_id
            """.formatted(this.activationsTable))) {
            JdbcUuid.set(statement, 1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ActiveBooster active = this.mapper.activeBooster(result, playerId);
                    activeBoosters.put(active.activationGroup(), active);
                }
            }
        }
        return activeBoosters;
    }

    private Map<ActivationGroup, List<QueuedBooster>> loadQueuedBoosters(Connection connection, UUID playerId) throws SQLException {
        LinkedHashMap<ActivationGroup, List<QueuedBooster>> queuedBoosters = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT queue_id, player_uuid, activation_group, position, booster_id, target_key,
                   multiplier, conflict_policy, scope_type, game_scopes, server_scopes,
                   requirement_mode, requirement_permissions, duration_millis, queued_at,
                   source_type, actor_uuid, source_reference, source_server_id
            FROM %s
            WHERE player_uuid = ?
            ORDER BY activation_group, position
            """.formatted(this.queueTable))) {
            JdbcUuid.set(statement, 1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    QueuedBooster queued = this.mapper.queuedBooster(result, playerId);
                    queuedBoosters.computeIfAbsent(queued.activationGroup(), ignored -> new ArrayList<>()).add(queued);
                }
            }
        }
        return queuedBoosters;
    }

    private List<BoosterClaim> loadPendingClaims(Connection connection, UUID playerId) throws SQLException {
        ArrayList<BoosterClaim> claims = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT claim_id, player_uuid, booster_id, amount, source_type, actor_uuid,
                   source_reference, source_server_id, created_at, claimed_at, status
            FROM %s
            WHERE player_uuid = ? AND status = 'PENDING'
            ORDER BY created_at, claim_id
            """.formatted(this.claimsTable))) {
            JdbcUuid.set(statement, 1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    claims.add(this.mapper.claim(result, playerId));
                }
            }
        }
        return claims;
    }
}
