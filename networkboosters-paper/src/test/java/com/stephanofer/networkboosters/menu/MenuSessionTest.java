package com.stephanofer.networkboosters.menu;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MenuSessionTest {

    @Test
    void pendingActivationExpiresAtConfiguredInstant() {
        Instant expiresAt = Instant.parse("2026-01-01T00:00:30Z");
        PendingActivation pending = new PendingActivation(
            UUID.randomUUID(),
            UUID.randomUUID(),
            BoosterId.of("personal_points_x2"),
            1,
            1,
            BoosterMenuFilter.ALL,
            BoosterMenuSort.RECOMMENDED,
            expiresAt
        );

        assertFalse(pending.expired(expiresAt.minusMillis(1)));
        assertTrue(pending.expired(expiresAt));
    }

    @Test
    void sessionRejectsInvalidPage() {
        assertThrows(IllegalArgumentException.class, () -> new MenuSession(
            UUID.randomUUID(),
            BoosterMenuFilter.ALL,
            BoosterMenuSort.RECOMMENDED,
            0,
            java.util.Optional.empty(),
            java.util.Optional.empty()
        ));
    }

    @Test
    void changingFilterReturnsToFirstPage() {
        MenuSession session = MenuSession.initial().withPage(5);

        MenuSession changed = session.withFilter(BoosterMenuFilter.TRANSFERABLE);

        assertTrue(changed.page() == 1);
        assertTrue(changed.filter() == BoosterMenuFilter.TRANSFERABLE);
    }

    @Test
    void changingSortReturnsToFirstPage() {
        MenuSession session = MenuSession.initial().withPage(5);

        MenuSession changed = session.withSort(BoosterMenuSort.QUANTITY);

        assertTrue(changed.page() == 1);
        assertTrue(changed.sort() == BoosterMenuSort.QUANTITY);
    }
}
