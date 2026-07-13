package com.stephanofer.networkboosters.menu;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import com.stephanofer.networkboosters.api.booster.BoosterCategory;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.booster.PermissionMode;
import com.stephanofer.networkboosters.api.booster.TransferPolicy;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class BoosterMenuViewsTest {

    @Test
    void ordersUnlockedApplicableBoostersBeforeLockedOnRecommendedSort() {
        UUID playerId = UUID.randomUUID();
        BoosterId locked = BoosterId.of("locked");
        BoosterId unlocked = BoosterId.of("unlocked");
        PlayerBoostSnapshot snapshot = new PlayerBoostSnapshot(playerId, 1, Map.of(locked, 1L, unlocked, 1L), Map.of(), Map.of(), java.util.List.of());

        var views = BoosterMenuViews.ownedViews(
            snapshot,
            Map.of(
                locked, definition(locked, 10, new ActivationRequirements(Set.of("networkboosters.tier.phantom"), PermissionMode.ALL)),
                unlocked, definition(unlocked, 20, ActivationRequirements.NONE)
            ),
            permission -> false,
            "skywars",
            "skywars-01",
            BoosterMenuFilter.ALL,
            BoosterMenuSort.RECOMMENDED
        );

        assertEquals(unlocked, views.getFirst().boosterId());
    }

    @Test
    void lockedFilterReturnsOnlyPermissionBlockedBoosters() {
        UUID playerId = UUID.randomUUID();
        BoosterId locked = BoosterId.of("locked");
        BoosterId unlocked = BoosterId.of("unlocked");
        PlayerBoostSnapshot snapshot = new PlayerBoostSnapshot(playerId, 1, Map.of(locked, 1L, unlocked, 1L), Map.of(), Map.of(), java.util.List.of());

        var views = BoosterMenuViews.ownedViews(
            snapshot,
            Map.of(
                locked, definition(locked, 10, new ActivationRequirements(Set.of("networkboosters.tier.phantom"), PermissionMode.ALL)),
                unlocked, definition(unlocked, 20, ActivationRequirements.NONE)
            ),
            permission -> false,
            "skywars",
            "skywars-01",
            BoosterMenuFilter.LOCKED,
            BoosterMenuSort.RECOMMENDED
        );

        assertEquals(1, views.size());
        assertEquals(BoosterVisualState.BLOCKED_PERMISSION, views.getFirst().state());
    }

    @Test
    void orphanedInventoryEntryStaysVisible() {
        UUID playerId = UUID.randomUUID();
        BoosterId orphaned = BoosterId.of("removed_booster");
        PlayerBoostSnapshot snapshot = new PlayerBoostSnapshot(playerId, 1, Map.of(orphaned, 2L), Map.of(), Map.of(), java.util.List.of());

        var views = BoosterMenuViews.ownedViews(snapshot, Map.of(), permission -> true, "skywars", "skywars-01", BoosterMenuFilter.ALL, BoosterMenuSort.RECOMMENDED);

        assertEquals(1, views.size());
        assertEquals(BoosterVisualState.ORPHANED, views.getFirst().state());
    }

    @Test
    void paginatesAggregatedAmountsAsIndependentVisualUnits() {
        BoosterId firstId = BoosterId.of("first");
        BoosterId secondId = BoosterId.of("second");
        OwnedBoosterView first = new OwnedBoosterView(firstId, 5, Optional.empty(), BoosterVisualState.ORPHANED, false, false, false, Optional.empty(), java.util.List.of());
        OwnedBoosterView second = new OwnedBoosterView(secondId, 3, Optional.empty(), BoosterVisualState.ORPHANED, false, false, false, Optional.empty(), java.util.List.of());

        assertEquals(8, BoosterMenuViews.visibleUnitCount(java.util.List.of(first, second)));
        var page = BoosterMenuViews.page(java.util.List.of(first, second), 2, 4);
        assertEquals(4, page.size());
        assertSame(first, page.getFirst());
        assertSame(second, page.get(1));
        assertSame(second, page.getLast());
    }

    @Test
    void hugeAmountsAreSaturatedWithoutMaterializingEveryUnit() {
        OwnedBoosterView huge = new OwnedBoosterView(
            BoosterId.of("huge"), Long.MAX_VALUE, Optional.empty(), BoosterVisualState.ORPHANED,
            false, false, false, Optional.empty(), java.util.List.of()
        );

        assertEquals(Integer.MAX_VALUE, BoosterMenuViews.visibleUnitCount(java.util.List.of(huge)));
        assertEquals(21, BoosterMenuViews.page(java.util.List.of(huge), 1000, 21).size());
    }

    private static BoosterDefinition definition(BoosterId id, int order, ActivationRequirements requirements) {
        return new BoosterDefinition(
            id,
            BoosterTarget.of("network_progression:points"),
            BigDecimal.valueOf(2),
            Duration.ofHours(2),
            BoosterScope.personalGlobal(),
            ActivationGroup.of("personal-points"),
            ConflictPolicy.QUEUE,
            requirements,
            new TransferPolicy(true, 1, 5, Duration.ZERO, Optional.empty()),
            true,
            order,
            BoosterCategory.of("points")
        );
    }
}
