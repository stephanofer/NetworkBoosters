package com.stephanofer.networkboosters.api.calculation;

import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BoostRequest(
    UUID playerId,
    BoosterTarget target,
    BigDecimal baseAmount,
    Optional<String> gameId,
    Optional<String> serverId
) {

    public BoostRequest {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(baseAmount, "baseAmount");
        gameId = normalizeOptional(gameId, "gameId");
        serverId = normalizeOptional(serverId, "serverId");
    }

    public static BoostRequest of(UUID playerId, BoosterTarget target, BigDecimal baseAmount, String gameId, String serverId) {
        return new BoostRequest(playerId, target, baseAmount, Optional.ofNullable(gameId), Optional.ofNullable(serverId));
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(String::trim).filter(raw -> !raw.isEmpty());
    }
}
