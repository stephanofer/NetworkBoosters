package com.stephanofer.networkboosters.menu;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BoosterTimelineViewsTest {

    @Test
    void buildsAbsoluteActiveAndQueueTimelineWithoutNegativeDurations() {
        UUID playerId = UUID.randomUUID();
        ActivationGroup group = ActivationGroup.of("points");
        Instant now = Instant.parse("2026-07-13T12:00:00Z");
        ActiveBooster active = new ActiveBooster(
            UUID.randomUUID(), playerId, BoosterId.of("x2"), BoosterTarget.NETWORK_POINTS,
            BigDecimal.valueOf(2), group, ConflictPolicy.QUEUE, BoosterScope.personalGlobal(), ActivationRequirements.NONE,
            now.minusSeconds(60), now.plusSeconds(120), ActivationSource.PLAYER_MENU, SourceReference.none()
        );
        QueuedBooster queued = new QueuedBooster(
            UUID.randomUUID(), playerId, BoosterId.of("x3"), BoosterTarget.NETWORK_POINTS,
            BigDecimal.valueOf(3), group, ConflictPolicy.QUEUE, BoosterScope.personalGlobal(), ActivationRequirements.NONE,
            Duration.ofMinutes(5), now, ActivationSource.PLAYER_MENU, SourceReference.none(), 0
        );
        PlayerBoostSnapshot snapshot = new PlayerBoostSnapshot(playerId, 1, Map.of(), Map.of(group, active), Map.of(group, List.of(queued)), List.of());

        var timeline = BoosterTimelineViews.create(snapshot, now);

        assertEquals(2, timeline.size());
        assertTrue(timeline.getFirst().active());
        assertEquals(Duration.ofMinutes(2), timeline.getFirst().remainingUntilEnd());
        assertFalse(timeline.getLast().active());
        assertEquals(Duration.ofMinutes(2), timeline.getLast().remainingUntilStart());
        assertEquals(Duration.ofMinutes(7), timeline.getLast().remainingUntilEnd());
    }
}
