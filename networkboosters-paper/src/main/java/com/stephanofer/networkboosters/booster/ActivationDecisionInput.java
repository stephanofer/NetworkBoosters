package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ActivationDecisionInput(
    BoosterDefinition definition,
    long ownedAmount,
    Optional<ActiveBooster> activeBooster,
    List<QueuedBooster> queue,
    boolean requirementsSatisfied,
    Instant now,
    Duration maximumTotalDuration,
    int maximumQueuedEntries
) {

    public ActivationDecisionInput {
        Objects.requireNonNull(definition, "definition");
        if (ownedAmount < 0) {
            throw new IllegalArgumentException("ownedAmount cannot be negative");
        }
        activeBooster = Objects.requireNonNull(activeBooster, "activeBooster");
        queue = List.copyOf(Objects.requireNonNull(queue, "queue"));
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(maximumTotalDuration, "maximumTotalDuration");
        if (maximumTotalDuration.isZero() || maximumTotalDuration.isNegative()) {
            throw new IllegalArgumentException("maximumTotalDuration must be positive");
        }
        if (maximumQueuedEntries < 0) {
            throw new IllegalArgumentException("maximumQueuedEntries cannot be negative");
        }
    }
}
