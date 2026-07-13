package com.stephanofer.networkboosters.menu;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class BoosterTimelineViews {

    private BoosterTimelineViews() {
    }

    public static List<BoosterTimelineView> create(PlayerBoostSnapshot snapshot, Instant now) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(now, "now");
        TreeSet<ActivationGroup> groups = new TreeSet<>(Comparator.comparing(ActivationGroup::value));
        groups.addAll(snapshot.activeBoosters().keySet());
        groups.addAll(snapshot.queuedBoosters().keySet());
        List<BoosterTimelineView> result = new ArrayList<>();
        for (ActivationGroup group : groups) {
            var active = snapshot.activeBoosters().get(group);
            Instant cursor = now;
            if (active != null && active.expiresAt().isAfter(now)) {
                result.add(new BoosterTimelineView(
                    active.boosterId(), active.activationGroup(), active.multiplier(), active.scope(), true, 0,
                    Duration.ZERO, Duration.between(now, active.expiresAt()), Duration.between(active.activatedAt(), active.expiresAt())
                ));
                cursor = active.expiresAt();
            }
            for (var queued : snapshot.queuedBoosters().getOrDefault(group, List.of())) {
                Instant endsAt;
                try {
                    endsAt = cursor.plus(queued.duration());
                } catch (ArithmeticException ignored) {
                    endsAt = Instant.MAX;
                }
                if (endsAt.isAfter(now)) {
                    result.add(new BoosterTimelineView(
                        queued.boosterId(), queued.activationGroup(), queued.multiplier(), queued.scope(), false, queued.position(),
                        Duration.between(now, cursor), Duration.between(now, endsAt), queued.duration()
                    ));
                }
                cursor = endsAt;
            }
        }
        return List.copyOf(result);
    }
}
