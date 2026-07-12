package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.result.ActivationStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ActivationDecisionEngine {

    public ActivationDecision decide(ActivationDecisionInput input) {
        Objects.requireNonNull(input, "input");
        QueueTimelineResult timeline = new QueueTimeline().advance(input.activeBooster(), input.queue(), input.now());
        BoosterDefinition definition = input.definition();
        if (!definition.enabled()) {
            return ActivationDecision.rejected(ActivationStatus.DEFINITION_DISABLED, timeline);
        }
        if (input.ownedAmount() <= 0) {
            return ActivationDecision.rejected(ActivationStatus.NOT_OWNED, timeline);
        }
        if (!input.requirementsSatisfied()) {
            return ActivationDecision.rejected(ActivationStatus.PERMISSION_DENIED, timeline);
        }

        Optional<ActiveBooster> active = input.activeBooster().filter(booster -> booster.isActiveAt(input.now()));
        Optional<QueuedBooster> promoted = timeline.promotedBooster().map(TimedQueuedBooster::queuedBooster);
        List<QueuedBooster> remainingQueue = timeline.remainingQueue();
        Duration queuedDuration = totalQueuedDuration(remainingQueue);

        if (active.isEmpty() && promoted.isEmpty()) {
            if (exceeds(input.maximumTotalDuration(), checkedAdd(queuedDuration, definition.duration()))) {
                return ActivationDecision.rejected(ActivationStatus.DURATION_LIMIT_REACHED, timeline);
            }
            return ActivationDecision.active(
                ActivationStatus.ACTIVATED,
                ActivationDecisionType.ACTIVATE_NEW,
                definition.duration(),
                timeline
            );
        }

        Duration activeRemaining = active
            .map(activeBooster -> Duration.between(input.now(), activeBooster.expiresAt()))
            .orElseGet(() -> Duration.between(input.now(), timeline.promotedBooster().orElseThrow().expiresAt()));
        boolean compatible = active.map(activeBooster -> QueueCompatibility.matches(activeBooster, definition))
            .orElseGet(() -> QueueCompatibility.matches(promoted.orElseThrow(), definition));
        if (compatible) {
            Duration newRemaining = checkedAdd(activeRemaining, definition.duration());
            Duration totalDuration = checkedAdd(newRemaining, queuedDuration);
            if (exceeds(input.maximumTotalDuration(), totalDuration)) {
                return ActivationDecision.rejected(ActivationStatus.DURATION_LIMIT_REACHED, timeline);
            }
            return ActivationDecision.active(
                ActivationStatus.EXTENDED,
                ActivationDecisionType.EXTEND_ACTIVE,
                newRemaining,
                timeline
            );
        }

        ConflictPolicy policy = definition.conflictPolicy();
        if (policy == ConflictPolicy.REJECT) {
            return ActivationDecision.rejected(ActivationStatus.GROUP_OCCUPIED, timeline);
        }
        if (policy == ConflictPolicy.REPLACE) {
            Duration totalDuration = checkedAdd(queuedDuration, definition.duration());
            if (exceeds(input.maximumTotalDuration(), totalDuration)) {
                return ActivationDecision.rejected(ActivationStatus.DURATION_LIMIT_REACHED, timeline);
            }
            return ActivationDecision.active(
                ActivationStatus.REPLACED,
                ActivationDecisionType.REPLACE_ACTIVE,
                definition.duration(),
                timeline
            );
        }

        Optional<QueuedBooster> lastQueued = remainingQueue.isEmpty()
            ? Optional.empty()
            : Optional.of(remainingQueue.get(remainingQueue.size() - 1));
        boolean canMerge = lastQueued.filter(queued -> QueueCompatibility.matches(queued, definition)).isPresent();
        Duration totalDuration = checkedAdd(checkedAdd(activeRemaining, queuedDuration), definition.duration());
        if (exceeds(input.maximumTotalDuration(), totalDuration)) {
            return ActivationDecision.rejected(ActivationStatus.DURATION_LIMIT_REACHED, timeline);
        }
        if (!canMerge && remainingQueue.size() >= input.maximumQueuedEntries()) {
            return ActivationDecision.rejected(ActivationStatus.QUEUE_LIMIT_REACHED, timeline);
        }
        return ActivationDecision.queued(
            canMerge ? ActivationStatus.QUEUE_MERGED : ActivationStatus.QUEUED,
            canMerge ? ActivationDecisionType.MERGE_LAST_QUEUE_ENTRY : ActivationDecisionType.QUEUE_NEW,
            lastQueued.map(queued -> checkedAdd(queued.duration(), definition.duration())).orElse(definition.duration()),
            timeline
        );
    }

    private static Duration totalQueuedDuration(List<QueuedBooster> queue) {
        Duration total = Duration.ZERO;
        for (QueuedBooster queuedBooster : queue) {
            total = checkedAdd(total, queuedBooster.duration());
        }
        return total;
    }

    private static Duration checkedAdd(Duration left, Duration right) {
        try {
            return left.plus(right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("duration overflow", exception);
        }
    }

    private static boolean exceeds(Duration maximum, Duration actual) {
        return actual.compareTo(maximum) > 0;
    }
}
