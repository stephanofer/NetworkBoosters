package com.stephanofer.networkboosters;

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
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.booster.TransferPolicy;
import com.stephanofer.networkboosters.api.calculation.BoostCalculation;
import com.stephanofer.networkboosters.api.calculation.BoostRequest;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.result.ActivationStatus;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.booster.ActivationDecision;
import com.stephanofer.networkboosters.booster.ActivationDecisionEngine;
import com.stephanofer.networkboosters.booster.ActivationDecisionInput;
import com.stephanofer.networkboosters.booster.ActivationDecisionType;
import com.stephanofer.networkboosters.booster.QueueCompatibility;
import com.stephanofer.networkboosters.booster.QueueTimeline;
import com.stephanofer.networkboosters.booster.QueueTimelineResult;
import com.stephanofer.networkboosters.calculation.BoostCalculator;
import com.stephanofer.networkboosters.capacity.InventoryCapacityResolver;
import com.stephanofer.networkboosters.capacity.InventoryCapacityRule;
import com.stephanofer.networkboosters.capacity.ResolvedInventoryCapacity;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainRulesTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void capacityChoosesLargestEffectiveRuleAndRejectsOverflow() {
        InventoryCapacityResolver resolver = new InventoryCapacityResolver();
        ResolvedInventoryCapacity capacity = resolver.resolve(30, List.of(
            new InventoryCapacityRule("phantom", Optional.of("capacity.phantom"), 60, 100),
            new InventoryCapacityRule("legend", Optional.of("capacity.legend"), 100, 200),
            new InventoryCapacityRule("lower", Optional.of("capacity.lower"), 20, 999)
        ), permission -> permission.equals("capacity.phantom"));

        assertEquals(60, capacity.maximum());
        assertEquals(Optional.of("phantom"), capacity.ruleId());
        assertTrue(resolver.canReceive(58, 2, capacity));
        assertFalse(resolver.canReceive(58, 3, capacity));
        assertFalse(resolver.canReceive(Long.MAX_VALUE, 1, capacity));
    }

    @Test
    void calculationIsSynchronousPureDeterministicAndCapped() {
        ActiveBooster personal = activeBooster(
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            BoosterId.of("personal_points_x2"),
            ActivationGroup.of("personal-points"),
            BigDecimal.valueOf(2),
            NOW.minusSeconds(60),
            NOW.plusSeconds(60),
            BoosterScope.personalGlobal());
        ActiveBooster event = activeBooster(
            UUID.fromString("00000000-0000-0000-0000-000000000102"),
            BoosterId.of("event_points_x3"),
            ActivationGroup.of("event-points"),
            BigDecimal.valueOf(3),
            NOW.minusSeconds(60),
            NOW.plusSeconds(60),
            BoosterScope.personalGlobal());
        ActiveBooster expired = activeBooster(
            UUID.fromString("00000000-0000-0000-0000-000000000103"),
            BoosterId.of("expired_points_x10"),
            ActivationGroup.of("expired-points"),
            BigDecimal.TEN,
            NOW.minusSeconds(120),
            NOW.minusSeconds(1),
            BoosterScope.personalGlobal());

        PlayerBoostSnapshot snapshot = new PlayerBoostSnapshot(
            PLAYER_ID,
            1,
            Map.of(),
            Map.of(
                personal.activationGroup(), personal,
                event.activationGroup(), event,
                expired.activationGroup(), expired),
            Map.of(),
            List.of());

        BoostCalculation calculation = new BoostCalculator().calculate(
            Optional.of(snapshot),
            BoostRequest.of(PLAYER_ID, BoosterTarget.NETWORK_PROGRESSION_POINTS, BigDecimal.TEN, "skywars", "sw-01"),
            NOW,
            BigDecimal.valueOf(5));

        assertEquals(new BigDecimal("5"), calculation.multiplier());
        assertEquals(new BigDecimal("50"), calculation.finalAmount());
        assertEquals(2, calculation.appliedBoosts().size());
        assertTrue(calculation.capped());
        assertEquals(BigDecimal.TEN.negate(), new BoostCalculator().calculate(
            Optional.of(snapshot),
            BoostRequest.of(PLAYER_ID, BoosterTarget.NETWORK_PROGRESSION_POINTS, BigDecimal.TEN.negate(), "skywars", "sw-01"),
            NOW,
            BigDecimal.valueOf(5)).finalAmount());
    }

    @Test
    void compatibilityUsesBehaviorSnapshotNotOnlyId() {
        BoosterDefinition x3 = definition(BoosterId.of("personal_points"), BigDecimal.valueOf(3), ConflictPolicy.QUEUE);
        ActiveBooster activeX2SameId = activeBooster(
            UUID.randomUUID(),
            BoosterId.of("personal_points"),
            ActivationGroup.of("personal-points"),
            BigDecimal.valueOf(2),
            NOW.minusSeconds(1),
            NOW.plusSeconds(60),
            BoosterScope.personalGlobal());

        assertFalse(QueueCompatibility.matches(activeX2SameId, x3));

        BoosterDefinition sameBehaviorDifferentPolicy = definition(BoosterId.of("personal_points"), BigDecimal.valueOf(2), ConflictPolicy.REJECT);
        assertTrue(QueueCompatibility.matches(activeX2SameId, sameBehaviorDifferentPolicy));

        BoosterDefinition changedRequirements = new BoosterDefinition(
            x3.id(),
            x3.target(),
            x3.multiplier(),
            x3.duration(),
            x3.scope(),
            x3.activationGroup(),
            x3.conflictPolicy(),
            new ActivationRequirements(Set.of("networkboosters.tier.legend"), com.stephanofer.networkboosters.api.booster.PermissionMode.ALL),
            x3.transferPolicy(),
            x3.enabled(),
            x3.displayOrder(),
            x3.category());
        ActiveBooster activeX3WithoutRequirement = activeBooster(
            UUID.randomUUID(),
            x3.id(),
            x3.activationGroup(),
            BigDecimal.valueOf(3),
            NOW.minusSeconds(1),
            NOW.plusSeconds(60),
            x3.scope());
        assertFalse(QueueCompatibility.matches(activeX3WithoutRequirement, changedRequirements));
    }

    @Test
    void activationDecisionExtendsQueuesMergesAndReplacesSafely() {
        ActivationDecisionEngine engine = new ActivationDecisionEngine();
        BoosterDefinition x2 = definition(BoosterId.of("personal_points_x2"), BigDecimal.valueOf(2), ConflictPolicy.QUEUE);
        ActiveBooster activeX2 = activeBooster(
            UUID.randomUUID(),
            x2.id(),
            x2.activationGroup(),
            BigDecimal.valueOf(2),
            NOW.minusSeconds(60),
            NOW.plus(Duration.ofHours(1)),
            x2.scope());

        ActivationDecision extended = engine.decide(input(x2, 1, Optional.of(activeX2), List.of(), true));
        assertEquals(ActivationStatus.EXTENDED, extended.status());
        assertEquals(ActivationDecisionType.EXTEND_ACTIVE, extended.type());
        assertEquals(Duration.ofHours(3), extended.resultingActiveRemaining().orElseThrow());

        BoosterDefinition x2RejectPolicy = definition(BoosterId.of("personal_points_x2"), BigDecimal.valueOf(2), ConflictPolicy.REJECT);
        assertEquals(ActivationStatus.EXTENDED, engine.decide(input(x2RejectPolicy, 1, Optional.of(activeX2), List.of(), true)).status());

        BoosterDefinition x3 = definition(BoosterId.of("personal_points_x3"), BigDecimal.valueOf(3), ConflictPolicy.QUEUE);
        QueuedBooster queuedX3 = queuedBooster(x3, Duration.ofHours(2), 0);
        ActivationDecision merged = engine.decide(input(x3, 1, Optional.of(activeX2), List.of(queuedX3), true));
        assertEquals(ActivationStatus.QUEUE_MERGED, merged.status());
        assertEquals(Duration.ofHours(4), merged.resultingQueuedDuration().orElseThrow());

        BoosterDefinition reject = definition(BoosterId.of("personal_points_x4"), BigDecimal.valueOf(4), ConflictPolicy.REJECT);
        assertEquals(ActivationStatus.GROUP_OCCUPIED, engine.decide(input(reject, 1, Optional.of(activeX2), List.of(), true)).status());

        BoosterDefinition replace = definition(BoosterId.of("personal_points_x5"), BigDecimal.valueOf(5), ConflictPolicy.REPLACE);
        ActivationDecision replaced = engine.decide(input(replace, 1, Optional.of(activeX2), List.of(queuedX3), true));
        assertEquals(ActivationStatus.REPLACED, replaced.status());
        assertEquals(Duration.ofHours(2), replaced.resultingActiveRemaining().orElseThrow());
    }

    @Test
    void activationDecisionHonorsQueueAndDurationLimitsWithoutPartialConsumption() {
        ActivationDecisionEngine engine = new ActivationDecisionEngine();
        BoosterDefinition x2 = definition(BoosterId.of("personal_points_x2"), BigDecimal.valueOf(2), ConflictPolicy.QUEUE);
        BoosterDefinition x3 = definition(BoosterId.of("personal_points_x3"), BigDecimal.valueOf(3), ConflictPolicy.QUEUE);
        ActiveBooster activeX2 = activeBooster(
            UUID.randomUUID(), x2.id(), x2.activationGroup(), BigDecimal.valueOf(2), NOW, NOW.plus(Duration.ofHours(1)), x2.scope());
        QueuedBooster queuedX3 = queuedBooster(x3, Duration.ofHours(2), 0);

        ActivationDecision queueFull = engine.decide(new ActivationDecisionInput(
            definition(BoosterId.of("personal_points_x4"), BigDecimal.valueOf(4), ConflictPolicy.QUEUE),
            1,
            Optional.of(activeX2),
            List.of(queuedX3),
            true,
            NOW,
            Duration.ofDays(7),
            1));
        assertEquals(ActivationStatus.QUEUE_LIMIT_REACHED, queueFull.status());
        assertFalse(queueFull.consumesInventory());

        ActivationDecision durationExceeded = engine.decide(new ActivationDecisionInput(
            x3, 1, Optional.of(activeX2), List.of(queuedX3), true, NOW, Duration.ofHours(2), 20));
        assertEquals(ActivationStatus.DURATION_LIMIT_REACHED, durationExceeded.status());
        assertFalse(durationExceeded.consumesInventory());
    }

    @Test
    void activationDecisionRejectsCorruptNegativeInventoryState() {
        BoosterDefinition definition = definition(BoosterId.of("personal_points_x2"), BigDecimal.valueOf(2), ConflictPolicy.QUEUE);
        assertThrows(IllegalArgumentException.class, () -> new ActivationDecisionInput(
            definition,
            -1,
            Optional.empty(),
            List.of(),
            true,
            NOW,
            Duration.ofDays(7),
            20));
    }

    @Test
    void queueTimelineAdvancesThroughDowntime() {
        BoosterDefinition x2 = definition(BoosterId.of("personal_points_x2"), BigDecimal.valueOf(2), ConflictPolicy.QUEUE);
        ActiveBooster active = activeBooster(
            UUID.randomUUID(), x2.id(), x2.activationGroup(), BigDecimal.valueOf(2), NOW.minus(Duration.ofHours(2)), NOW, x2.scope());
        QueuedBooster first = queuedBooster(x2, Duration.ofHours(1), 0);
        QueuedBooster second = queuedBooster(x2, Duration.ofHours(2), 1);

        QueueTimelineResult result = new QueueTimeline().advance(
            Optional.of(active),
            List.of(first, second),
            NOW.plus(Duration.ofMinutes(90)));

        assertTrue(result.activeExpired());
        assertEquals(1, result.expiredQueuedBoosters().size());
        assertEquals(first, result.expiredQueuedBoosters().getFirst().queuedBooster());
        assertEquals(second, result.promotedBooster().orElseThrow().queuedBooster());
        assertEquals(NOW.plus(Duration.ofHours(1)), result.promotedBooster().orElseThrow().startsAt());
        assertEquals(NOW.plus(Duration.ofHours(3)), result.promotedBooster().orElseThrow().expiresAt());
        assertTrue(result.remainingQueue().isEmpty());
    }

    @Test
    void activationReconcilesExpiredStateBeforeApplyingNewBooster() {
        ActivationDecisionEngine engine = new ActivationDecisionEngine();
        BoosterDefinition x2 = definition(BoosterId.of("personal_points_x2"), BigDecimal.valueOf(2), ConflictPolicy.QUEUE);
        BoosterDefinition x3 = definition(BoosterId.of("personal_points_x3"), BigDecimal.valueOf(3), ConflictPolicy.QUEUE);
        ActiveBooster expired = activeBooster(
            UUID.randomUUID(), x2.id(), x2.activationGroup(), BigDecimal.valueOf(2), NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), x2.scope());
        QueuedBooster queuedX3 = queuedBooster(x3, Duration.ofHours(2), 0);

        ActivationDecision decision = engine.decide(input(x2, 1, Optional.of(expired), List.of(queuedX3), true));

        assertEquals(ActivationStatus.QUEUED, decision.status());
        assertEquals(ActivationDecisionType.QUEUE_NEW, decision.type());
        assertTrue(decision.timeline().activeExpired());
        assertEquals(queuedX3, decision.timeline().promotedBooster().orElseThrow().queuedBooster());
    }

    private static ActivationDecisionInput input(
        BoosterDefinition definition,
        long ownedAmount,
        Optional<ActiveBooster> activeBooster,
        List<QueuedBooster> queue,
        boolean requirementsSatisfied
    ) {
        return new ActivationDecisionInput(
            definition,
            ownedAmount,
            activeBooster,
            queue,
            requirementsSatisfied,
            NOW,
            Duration.ofDays(7),
            20);
    }

    private static BoosterDefinition definition(BoosterId boosterId, BigDecimal multiplier, ConflictPolicy policy) {
        return new BoosterDefinition(
            boosterId,
            BoosterTarget.NETWORK_PROGRESSION_POINTS,
            multiplier,
            Duration.ofHours(2),
            BoosterScope.personalGlobal(),
            ActivationGroup.of("personal-points"),
            policy,
            ActivationRequirements.NONE,
            new TransferPolicy(true, 1, 10, Duration.ZERO, Optional.empty()),
            true,
            100,
            BoosterCategory.of("points"));
    }

    private static ActiveBooster activeBooster(
        UUID activationId,
        BoosterId boosterId,
        ActivationGroup group,
        BigDecimal multiplier,
        Instant activatedAt,
        Instant expiresAt,
        BoosterScope scope
    ) {
        return new ActiveBooster(
            activationId,
            PLAYER_ID,
            boosterId,
            BoosterTarget.NETWORK_PROGRESSION_POINTS,
            multiplier,
            group,
            ConflictPolicy.QUEUE,
            scope,
            ActivationRequirements.NONE,
            activatedAt,
            expiresAt,
            ActivationSource.PLAYER_COMMAND,
            SourceReference.none());
    }

    private static QueuedBooster queuedBooster(BoosterDefinition definition, Duration duration, long position) {
        return new QueuedBooster(
            UUID.randomUUID(),
            PLAYER_ID,
            definition.id(),
            definition.target(),
            definition.multiplier(),
            definition.activationGroup(),
            definition.conflictPolicy(),
            definition.scope(),
            definition.requirements(),
            duration,
            NOW,
            ActivationSource.PLAYER_COMMAND,
            SourceReference.none(),
            position);
    }
}
