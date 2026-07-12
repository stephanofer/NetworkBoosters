package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.result.ActivationStatus;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record ActivationDecision(
    ActivationStatus status,
    ActivationDecisionType type,
    boolean consumesInventory,
    Optional<Duration> resultingActiveRemaining,
    Optional<Duration> resultingQueuedDuration,
    QueueTimelineResult timeline
) {

    public ActivationDecision {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(type, "type");
        resultingActiveRemaining = Objects.requireNonNull(resultingActiveRemaining, "resultingActiveRemaining");
        resultingQueuedDuration = Objects.requireNonNull(resultingQueuedDuration, "resultingQueuedDuration");
        Objects.requireNonNull(timeline, "timeline");
    }

    public static ActivationDecision rejected(ActivationStatus status, QueueTimelineResult timeline) {
        return new ActivationDecision(status, ActivationDecisionType.REJECTED, false, Optional.empty(), Optional.empty(), timeline);
    }

    public static ActivationDecision active(
        ActivationStatus status,
        ActivationDecisionType type,
        Duration remaining,
        QueueTimelineResult timeline
    ) {
        return new ActivationDecision(status, type, true, Optional.of(remaining), Optional.empty(), timeline);
    }

    public static ActivationDecision queued(
        ActivationStatus status,
        ActivationDecisionType type,
        Duration queuedDuration,
        QueueTimelineResult timeline
    ) {
        return new ActivationDecision(status, type, true, Optional.empty(), Optional.of(queuedDuration), timeline);
    }
}
