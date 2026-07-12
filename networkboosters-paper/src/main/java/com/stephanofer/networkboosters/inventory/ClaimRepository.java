package com.stephanofer.networkboosters.inventory;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.source.ClaimSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.persistence.JdbcUuid;
import com.stephanofer.networkboosters.persistence.PlayerSnapshotMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimRepository {

    private final String table;
    private final PlayerSnapshotMapper mapper;

    public ClaimRepository(String table, PlayerSnapshotMapper mapper) {
        this.table = Objects.requireNonNull(table, "table");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public BoosterClaim insert(
        Connection connection,
        UUID claimId,
        UUID playerId,
        BoosterId boosterId,
        long amount,
        ClaimSource source,
        SourceReference sourceReference,
        Instant createdAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO %s (
                claim_id, player_uuid, booster_id, amount, source_type, actor_uuid,
                source_reference, source_server_id, created_at, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, claimId);
            JdbcUuid.set(statement, 2, playerId);
            statement.setString(3, boosterId.value());
            statement.setLong(4, amount);
            statement.setString(5, source.name());
            JdbcUuid.setNullable(statement, 6, sourceReference.actorId().orElse(null));
            statement.setString(7, sourceReference.externalReference().orElse(null));
            statement.setString(8, sourceReference.serverId().orElse(null));
            statement.setTimestamp(9, Timestamp.from(createdAt));
            statement.executeUpdate();
        }
        return new BoosterClaim(
            claimId,
            playerId,
            boosterId,
            amount,
            source,
            sourceReference,
            createdAt,
            Optional.empty(),
            com.stephanofer.networkboosters.api.player.ClaimStatus.PENDING
        );
    }

    public Optional<BoosterClaim> findForUpdate(Connection connection, UUID claimId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT claim_id, player_uuid, booster_id, amount, source_type, actor_uuid,
                   source_reference, source_server_id, created_at, claimed_at, status
            FROM %s
            WHERE claim_id = ?
            FOR UPDATE
            """.formatted(this.table))) {
            JdbcUuid.set(statement, 1, claimId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(this.mapper.claim(result, JdbcUuid.get(result, "player_uuid")));
            }
        }
    }

    public void markClaimed(Connection connection, UUID claimId, Instant claimedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + this.table + " SET status = 'CLAIMED', claimed_at = ? WHERE claim_id = ? AND status = 'PENDING'"
        )) {
            statement.setTimestamp(1, Timestamp.from(claimedAt));
            JdbcUuid.set(statement, 2, claimId);
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Expected to claim one row for " + claimId + ", updated " + updated);
            }
        }
    }
}
