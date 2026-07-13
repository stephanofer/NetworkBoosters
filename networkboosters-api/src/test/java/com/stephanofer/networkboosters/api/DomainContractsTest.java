package com.stephanofer.networkboosters.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterCategory;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.booster.BoosterScopeType;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.booster.PermissionMode;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.booster.TransferPolicy;
import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.player.ClaimStatus;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.result.ActivationResult;
import com.stephanofer.networkboosters.api.result.ActivationStatus;
import com.stephanofer.networkboosters.api.result.ClaimResult;
import com.stephanofer.networkboosters.api.result.ClaimResultStatus;
import com.stephanofer.networkboosters.api.result.DeactivationResult;
import com.stephanofer.networkboosters.api.result.DeactivationStatus;
import com.stephanofer.networkboosters.api.result.InventoryMutationResult;
import com.stephanofer.networkboosters.api.result.InventoryMutationStatus;
import com.stephanofer.networkboosters.api.result.TransferResult;
import com.stephanofer.networkboosters.api.result.TransferStatus;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.ClaimSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainContractsTest {

    @Test
    void boosterIdsAreNormalizedAndStrict() {
        assertEquals("personal_points_x2", BoosterId.of(" PERSONAL_POINTS_X2 ").value());
        assertThrows(IllegalArgumentException.class, () -> BoosterId.of("points x2"));
        assertThrows(IllegalArgumentException.class, () -> BoosterId.of("ñ"));
        assertThrows(IllegalArgumentException.class, () -> BoosterId.of("a".repeat(65)));
    }

    @Test
    void targetsRequireNamespacedKeys() {
        assertEquals("network_progression:points", BoosterTarget.of("Network_Progression:Points").key());
        assertThrows(IllegalArgumentException.class, () -> BoosterTarget.of("points"));
        assertThrows(IllegalArgumentException.class, () -> BoosterTarget.of("network progression:points"));
    }

    @Test
    void scopesHandleWildcardAndContextSafely() {
        BoosterScope global = BoosterScope.personalGlobal();
        assertTrue(global.appliesTo(Optional.empty(), Optional.empty()));

        BoosterScope skywarsOnly = new BoosterScope(
            BoosterScopeType.PERSONAL,
            Set.of("skywars"),
            Set.of("*"));
        assertTrue(skywarsOnly.appliesTo(Optional.of("SkyWars"), Optional.empty()));
        assertFalse(skywarsOnly.appliesTo(Optional.empty(), Optional.empty()));
        assertFalse(skywarsOnly.appliesTo(Optional.of("bedwars"), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new BoosterScope(
            BoosterScopeType.PERSONAL,
            Set.of("*", "skywars"),
            Set.of("*")));
    }

    @Test
    void activationRequirementsArePureAndImmutable() {
        ActivationRequirements requirements = new ActivationRequirements(
            Set.of("networkboosters.tier.phantom", "networkboosters.tier.legend"),
            PermissionMode.ANY);
        assertTrue(requirements.satisfiedBy(permission -> permission.endsWith("legend")));
        assertFalse(requirements.satisfiedBy(permission -> false));
        assertThrows(UnsupportedOperationException.class, () -> requirements.permissions().add("x"));
        assertThrows(IllegalArgumentException.class, () -> new ActivationRequirements(Set.of(" "), PermissionMode.ALL));
    }

    @Test
    void definitionsRejectInvalidCoreBehavior() {
        BoosterDefinition valid = definition(BigDecimal.valueOf(2), Duration.ofHours(2));
        assertEquals(new BigDecimal("2"), valid.multiplier());

        assertThrows(IllegalArgumentException.class, () -> definition(BigDecimal.valueOf(0.5), Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> definition(BigDecimal.ONE, Duration.ZERO));
    }

    @Test
    void snapshotsAreDeeplyImmutableAndUseSafeTotals() {
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        Map<BoosterId, Long> inventory = new LinkedHashMap<>();
        inventory.put(boosterId, 3L);

        PlayerBoostSnapshot snapshot = new PlayerBoostSnapshot(
            UUID.randomUUID(),
            7,
            inventory,
            Map.of(),
            Map.of(),
            java.util.List.of());
        inventory.put(BoosterId.of("personal_points_x3"), 9L);

        assertEquals(3, snapshot.ownedAmount(boosterId));
        assertEquals(3, snapshot.ownedTotal());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.inventory().put(boosterId, 4L));
        assertThrows(IllegalArgumentException.class, () -> new PlayerBoostSnapshot(
            UUID.randomUUID(), -1, Map.of(), Map.of(), Map.of(), java.util.List.of()));
    }

    @Test
    void snapshotsRejectIncoherentNestedState() {
        UUID playerId = UUID.randomUUID();
        ActivationGroup group = ActivationGroup.of("personal-points");
        ActiveBooster active = activeBooster(playerId, group, ActivationRequirements.NONE);

        assertThrows(IllegalArgumentException.class, () -> new PlayerBoostSnapshot(
            playerId, 0, Map.of(), Map.of(ActivationGroup.of("other"), active), Map.of(), java.util.List.of()));

        QueuedBooster outOfOrder = queuedBooster(playerId, group, 0);
        QueuedBooster duplicatePosition = queuedBooster(playerId, group, 0);
        assertThrows(IllegalArgumentException.class, () -> new PlayerBoostSnapshot(
            playerId, 0, Map.of(), Map.of(), Map.of(group, java.util.List.of(outOfOrder, duplicatePosition)), java.util.List.of()));

        BoosterClaim claimed = new BoosterClaim(
            UUID.randomUUID(), playerId, BoosterId.of("personal_points_x2"), 1,
            ClaimSource.SYSTEM, SourceReference.none(), Instant.EPOCH, Optional.of(Instant.EPOCH), ClaimStatus.CLAIMED);
        assertThrows(IllegalArgumentException.class, () -> new PlayerBoostSnapshot(
            playerId, 0, Map.of(), Map.of(), Map.of(), java.util.List.of(claimed)));
    }

    @Test
    void typedResultsRejectPayloadsThatContradictTheirStatus() {
        ActiveBooster active = activeBooster(UUID.randomUUID(), ActivationGroup.of("personal-points"), ActivationRequirements.NONE);
        assertThrows(IllegalArgumentException.class, () -> new ActivationResult(
            ActivationStatus.NOT_OWNED, Optional.of(active), Optional.empty(), 1));

        BoosterClaim pending = new BoosterClaim(
            UUID.randomUUID(), UUID.randomUUID(), BoosterId.of("personal_points_x2"), 1,
            ClaimSource.SYSTEM, SourceReference.none(), Instant.EPOCH, Optional.empty(), ClaimStatus.PENDING);
        assertThrows(IllegalArgumentException.class, () -> new ClaimResult(
            ClaimResultStatus.CLAIMED, Optional.of(pending), 1));

        assertThrows(IllegalArgumentException.class, () -> new InventoryMutationResult(
            InventoryMutationStatus.CLAIM_CREATED, BoosterId.of("personal_points_x2"), 0, 0, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new InventoryMutationResult(
            InventoryMutationStatus.UNCHANGED, BoosterId.of("personal_points_x2"), 1, 2, Optional.empty()));

        assertThrows(IllegalArgumentException.class, () -> new TransferResult(
            TransferStatus.TRANSFERRED,
            UUID.randomUUID(),
            UUID.randomUUID(),
            BoosterId.of("personal_points_x2"),
            1,
            Optional.empty(),
            Optional.empty(),
            OptionalLong.of(0),
            OptionalLong.of(1)));
        assertThrows(IllegalArgumentException.class, () -> new TransferResult(
            TransferStatus.COOLDOWN,
            UUID.randomUUID(),
            UUID.randomUUID(),
            BoosterId.of("personal_points_x2"),
            1,
            Optional.empty(),
            Optional.empty(),
            OptionalLong.empty(),
            OptionalLong.empty()));

        assertThrows(IllegalArgumentException.class, () -> new DeactivationResult(
            DeactivationStatus.NOT_FOUND, Optional.of(active), Optional.empty()));
        assertEquals(active, new DeactivationResult(
            DeactivationStatus.DEACTIVATED, Optional.of(active), Optional.of(active)).promotedBooster().orElseThrow());
        assertEquals(active, new DeactivationResult(
            DeactivationStatus.EXPIRED, Optional.of(active), Optional.empty()).deactivatedBooster().orElseThrow());
    }

    private static BoosterDefinition definition(BigDecimal multiplier, Duration duration) {
        return new BoosterDefinition(
            BoosterId.of("personal_points_x2"),
            BoosterTarget.NETWORK_PROGRESSION_POINTS,
            multiplier,
            duration,
            BoosterScope.personalGlobal(),
            ActivationGroup.of("personal-points"),
            ConflictPolicy.QUEUE,
            ActivationRequirements.NONE,
            new TransferPolicy(true, 1, 10, Duration.ZERO, Optional.empty()),
            true,
            100,
            BoosterCategory.of("points"));
    }

    private static ActiveBooster activeBooster(UUID playerId, ActivationGroup group, ActivationRequirements requirements) {
        return new ActiveBooster(
            UUID.randomUUID(),
            playerId,
            BoosterId.of("personal_points_x2"),
            BoosterTarget.NETWORK_PROGRESSION_POINTS,
            BigDecimal.valueOf(2),
            group,
            ConflictPolicy.QUEUE,
            BoosterScope.personalGlobal(),
            requirements,
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            ActivationSource.SYSTEM,
            SourceReference.none());
    }

    private static QueuedBooster queuedBooster(UUID playerId, ActivationGroup group, long position) {
        return new QueuedBooster(
            UUID.randomUUID(),
            playerId,
            BoosterId.of("personal_points_x2"),
            BoosterTarget.NETWORK_PROGRESSION_POINTS,
            BigDecimal.valueOf(2),
            group,
            ConflictPolicy.QUEUE,
            BoosterScope.personalGlobal(),
            ActivationRequirements.NONE,
            Duration.ofMinutes(1),
            Instant.EPOCH,
            ActivationSource.SYSTEM,
            SourceReference.none(),
            position);
    }
}
