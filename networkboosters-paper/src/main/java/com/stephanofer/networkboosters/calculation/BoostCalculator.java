package com.stephanofer.networkboosters.calculation;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.calculation.AppliedBoost;
import com.stephanofer.networkboosters.api.calculation.BoostCalculation;
import com.stephanofer.networkboosters.api.calculation.BoostRequest;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BoostCalculator {

    public BoostCalculation calculate(
        Optional<PlayerBoostSnapshot> snapshot,
        BoostRequest request,
        Instant now,
        BigDecimal maximumMultiplier
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(now, "now");
        maximumMultiplier = Objects.requireNonNull(maximumMultiplier, "maximumMultiplier").stripTrailingZeros();
        if (maximumMultiplier.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("maximumMultiplier must be greater than or equal to 1");
        }
        if (request.baseAmount().compareTo(BigDecimal.ZERO) <= 0 || snapshot.isEmpty()) {
            return BoostCalculation.neutral(request.baseAmount());
        }

        List<ActiveBooster> applicable = snapshot.get().activeBoosters().values().stream()
            .filter(active -> active.target().equals(request.target()))
            .filter(active -> active.isActiveAt(now))
            .filter(active -> active.scope().appliesTo(request.gameId(), request.serverId()))
            .sorted(Comparator
                .comparing((ActiveBooster active) -> active.activationGroup().value())
                .thenComparing(active -> active.activationId().toString()))
            .toList();

        if (applicable.isEmpty()) {
            return BoostCalculation.neutral(request.baseAmount());
        }

        BigDecimal multiplier = BigDecimal.ONE;
        for (ActiveBooster activeBooster : applicable) {
            multiplier = multiplier.multiply(activeBooster.multiplier());
        }
        boolean capped = multiplier.compareTo(maximumMultiplier) > 0;
        if (capped) {
            multiplier = maximumMultiplier;
        }

        List<AppliedBoost> appliedBoosts = applicable.stream()
            .map(active -> new AppliedBoost(
                active.activationId(),
                active.boosterId(),
                active.activationGroup(),
                active.multiplier()
            ))
            .toList();

        return new BoostCalculation(
            request.baseAmount(),
            multiplier,
            request.baseAmount().multiply(multiplier),
            appliedBoosts,
            capped
        );
    }

    public BoostCalculation calculate(
        PlayerBoostSnapshot snapshot,
        BoostRequest request,
        Instant now,
        BigDecimal maximumMultiplier
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(request, "request");
        if (!snapshot.playerId().equals(request.playerId())) {
            throw new IllegalArgumentException("Snapshot player does not match request player");
        }
        return calculate(Optional.of(snapshot), request, now, maximumMultiplier);
    }
}
