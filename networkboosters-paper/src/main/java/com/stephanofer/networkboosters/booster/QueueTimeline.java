package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class QueueTimeline {

    public QueueTimelineResult advance(Optional<ActiveBooster> activeBooster, List<QueuedBooster> queue, Instant now) {
        Objects.requireNonNull(activeBooster, "activeBooster");
        List<QueuedBooster> queuedBoosters = List.copyOf(Objects.requireNonNull(queue, "queue"));
        Objects.requireNonNull(now, "now");

        if (activeBooster.isPresent() && activeBooster.get().expiresAt().isAfter(now)) {
            return new QueueTimelineResult(false, List.of(), Optional.empty(), queuedBoosters);
        }
        if (queuedBoosters.isEmpty()) {
            return new QueueTimelineResult(activeBooster.isPresent(), List.of(), Optional.empty(), List.of());
        }

        Instant cursor = activeBooster.map(ActiveBooster::expiresAt).orElse(now);
        List<TimedQueuedBooster> expired = new ArrayList<>();

        for (int index = 0; index < queuedBoosters.size(); index++) {
            QueuedBooster queued = queuedBoosters.get(index);
            Instant startsAt = cursor;
            Instant expiresAt;
            try {
                expiresAt = startsAt.plus(queued.duration());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("queue timeline overflow", exception);
            }
            TimedQueuedBooster timed = new TimedQueuedBooster(queued, startsAt, expiresAt);
            if (!expiresAt.isAfter(now)) {
                expired.add(timed);
                cursor = expiresAt;
                continue;
            }
            return new QueueTimelineResult(
                activeBooster.isPresent(),
                expired,
                Optional.of(timed),
                queuedBoosters.subList(index + 1, queuedBoosters.size())
            );
        }

        return new QueueTimelineResult(activeBooster.isPresent(), expired, Optional.empty(), List.of());
    }
}
