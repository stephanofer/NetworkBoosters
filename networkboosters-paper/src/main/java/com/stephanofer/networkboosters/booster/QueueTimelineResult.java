package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record QueueTimelineResult(
    boolean activeExpired,
    List<TimedQueuedBooster> expiredQueuedBoosters,
    Optional<TimedQueuedBooster> promotedBooster,
    List<QueuedBooster> remainingQueue
) {

    public QueueTimelineResult {
        expiredQueuedBoosters = List.copyOf(Objects.requireNonNull(expiredQueuedBoosters, "expiredQueuedBoosters"));
        promotedBooster = Objects.requireNonNull(promotedBooster, "promotedBooster");
        remainingQueue = List.copyOf(Objects.requireNonNull(remainingQueue, "remainingQueue"));
    }
}
