package com.stephanofer.networkboosters.booster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ExpiryWarningTrackerTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTIVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final List<Duration> THRESHOLDS = List.of(
        Duration.ofMinutes(5),
        Duration.ofMinutes(1),
        Duration.ofSeconds(10)
    );

    @Test
    void choosesClosestCrossedThresholdAndSkipsOlderMissedWarnings() {
        ExpiryWarningTracker tracker = new ExpiryWarningTracker();

        assertEquals(Duration.ofMinutes(1), tracker.nextWarning(
            PLAYER_ID,
            ACTIVATION_ID,
            Duration.ofSeconds(30),
            THRESHOLDS
        ).orElseThrow());

        assertEquals(Duration.ofSeconds(10), tracker.nextWarning(
            PLAYER_ID,
            ACTIVATION_ID,
            Duration.ofSeconds(5),
            THRESHOLDS
        ).orElseThrow());

        assertTrue(tracker.nextWarning(
            PLAYER_ID,
            ACTIVATION_ID,
            Duration.ofSeconds(4),
            THRESHOLDS
        ).isEmpty());
    }

    @Test
    void doesNotWarnBeforeThresholdAndWarnsOnlyOncePerActivation() {
        ExpiryWarningTracker tracker = new ExpiryWarningTracker();

        assertTrue(tracker.nextWarning(
            PLAYER_ID,
            ACTIVATION_ID,
            Duration.ofMinutes(6),
            THRESHOLDS
        ).isEmpty());

        assertEquals(Duration.ofMinutes(5), tracker.nextWarning(
            PLAYER_ID,
            ACTIVATION_ID,
            Duration.ofMinutes(5),
            THRESHOLDS
        ).orElseThrow());

        assertTrue(tracker.nextWarning(
            PLAYER_ID,
            ACTIVATION_ID,
            Duration.ofMinutes(4),
            THRESHOLDS
        ).isEmpty());
    }

    @Test
    void cleanupAllowsNewActivationWithSamePlayerToWarnAgain() {
        ExpiryWarningTracker tracker = new ExpiryWarningTracker();

        assertEquals(Duration.ofMinutes(5), tracker.nextWarning(
            PLAYER_ID,
            ACTIVATION_ID,
            Duration.ofMinutes(5),
            THRESHOLDS
        ).orElseThrow());
        tracker.retainActive(PLAYER_ID, Set.of());

        assertEquals(Duration.ofMinutes(5), tracker.nextWarning(
            PLAYER_ID,
            ACTIVATION_ID,
            Duration.ofMinutes(5),
            THRESHOLDS
        ).orElseThrow());
    }
}
