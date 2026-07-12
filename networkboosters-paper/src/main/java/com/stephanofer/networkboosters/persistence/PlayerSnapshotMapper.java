package com.stephanofer.networkboosters.persistence;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.booster.BoosterScopeType;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.booster.PermissionMode;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.player.ClaimStatus;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.ClaimSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class PlayerSnapshotMapper {

    private final SnapshotJsonCodec json;

    public PlayerSnapshotMapper(SnapshotJsonCodec json) {
        this.json = json;
    }

    public BoosterId boosterId(ResultSet result) throws SQLException {
        return map("inventory.booster_id", () -> BoosterId.of(requiredString(result, "booster_id")));
    }

    public long positiveLong(ResultSet result, String column, String label) throws SQLException {
        BigDecimal value = result.getBigDecimal(column);
        if (value == null || value.signum() <= 0 || value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw new PersistenceException(label + " must be a positive signed long, got " + value);
        }
        return value.longValueExact();
    }

    public long nonNegativeLong(ResultSet result, String column, String label) throws SQLException {
        BigDecimal value = result.getBigDecimal(column);
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw new PersistenceException(label + " must be a non-negative signed long, got " + value);
        }
        return value.longValueExact();
    }

    public ActiveBooster activeBooster(ResultSet result, UUID expectedPlayerId) throws SQLException {
        return map("activations." + JdbcUuid.get(result, "activation_id"), () -> new ActiveBooster(
            JdbcUuid.get(result, "activation_id"),
            requirePlayer(result, expectedPlayerId),
            BoosterId.of(requiredString(result, "booster_id")),
            BoosterTarget.of(requiredString(result, "target_key")),
            requiredBigDecimal(result, "multiplier"),
            ActivationGroup.of(requiredString(result, "activation_group")),
            enumValue(ConflictPolicy.class, requiredString(result, "conflict_policy"), "conflict_policy"),
            scope(result),
            requirements(result),
            requiredInstant(result, "activated_at"),
            requiredInstant(result, "expires_at"),
            enumValue(ActivationSource.class, requiredString(result, "source_type"), "source_type"),
            sourceReference(result)
        ));
    }

    public QueuedBooster queuedBooster(ResultSet result, UUID expectedPlayerId) throws SQLException {
        return map("queue." + JdbcUuid.get(result, "queue_id"), () -> new QueuedBooster(
            JdbcUuid.get(result, "queue_id"),
            requirePlayer(result, expectedPlayerId),
            BoosterId.of(requiredString(result, "booster_id")),
            BoosterTarget.of(requiredString(result, "target_key")),
            requiredBigDecimal(result, "multiplier"),
            ActivationGroup.of(requiredString(result, "activation_group")),
            enumValue(ConflictPolicy.class, requiredString(result, "conflict_policy"), "conflict_policy"),
            scope(result),
            requirements(result),
            Duration.ofMillis(positiveLong(result, "duration_millis", "queue.duration_millis")),
            requiredInstant(result, "queued_at"),
            enumValue(ActivationSource.class, requiredString(result, "source_type"), "source_type"),
            sourceReference(result),
            nonNegativeLong(result, "position", "queue.position")
        ));
    }

    public BoosterClaim claim(ResultSet result, UUID expectedPlayerId) throws SQLException {
        return map("claims." + JdbcUuid.get(result, "claim_id"), () -> new BoosterClaim(
            JdbcUuid.get(result, "claim_id"),
            requirePlayer(result, expectedPlayerId),
            BoosterId.of(requiredString(result, "booster_id")),
            positiveLong(result, "amount", "claims.amount"),
            enumValue(ClaimSource.class, requiredString(result, "source_type"), "source_type"),
            sourceReference(result),
            requiredInstant(result, "created_at"),
            optionalInstant(result, "claimed_at"),
            enumValue(ClaimStatus.class, requiredString(result, "status"), "status")
        ));
    }

    private UUID requirePlayer(ResultSet result, UUID expectedPlayerId) throws SQLException {
        UUID actual = JdbcUuid.get(result, "player_uuid");
        if (!expectedPlayerId.equals(actual)) {
            throw new PersistenceException("Row belongs to " + actual + " but snapshot is for " + expectedPlayerId);
        }
        return actual;
    }

    private BoosterScope scope(ResultSet result) throws SQLException {
        return new BoosterScope(
            enumValue(BoosterScopeType.class, requiredString(result, "scope_type"), "scope_type"),
            this.json.readStringArray(requiredString(result, "game_scopes"), "game_scopes"),
            this.json.readStringArray(requiredString(result, "server_scopes"), "server_scopes")
        );
    }

    private ActivationRequirements requirements(ResultSet result) throws SQLException {
        return new ActivationRequirements(
            this.json.readStringArray(requiredString(result, "requirement_permissions"), "requirement_permissions"),
            enumValue(PermissionMode.class, requiredString(result, "requirement_mode"), "requirement_mode")
        );
    }

    private SourceReference sourceReference(ResultSet result) throws SQLException {
        UUID actorId = JdbcUuid.getNullable(result, "actor_uuid");
        return new SourceReference(
            Optional.ofNullable(actorId),
            optionalString(result, "source_reference"),
            optionalString(result, "source_server_id")
        );
    }

    private static String requiredString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null) {
            throw new PersistenceException(column + " cannot be null");
        }
        return value;
    }

    private static Optional<String> optionalString(ResultSet result, String column) throws SQLException {
        return Optional.ofNullable(result.getString(column));
    }

    private static BigDecimal requiredBigDecimal(ResultSet result, String column) throws SQLException {
        BigDecimal value = result.getBigDecimal(column);
        if (value == null) {
            throw new PersistenceException(column + " cannot be null");
        }
        return value;
    }

    private static Instant requiredInstant(ResultSet result, String column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        if (timestamp == null) {
            throw new PersistenceException(column + " cannot be null");
        }
        return timestamp.toInstant();
    }

    private static Optional<Instant> optionalInstant(ResultSet result, String column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        return timestamp == null ? Optional.empty() : Optional.of(timestamp.toInstant());
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException("Invalid " + label + ": " + value, exception);
        }
    }

    private static <T> T map(String label, SqlSupplier<T> supplier) throws SQLException {
        try {
            return supplier.get();
        } catch (PersistenceException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException("Invalid persisted row " + label + ": " + exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
