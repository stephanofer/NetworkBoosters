package com.stephanofer.networkboosters.api.player;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PlayerBoostSnapshot(
    UUID playerId,
    long revision,
    Map<BoosterId, Long> inventory,
    Map<ActivationGroup, ActiveBooster> activeBoosters,
    Map<ActivationGroup, List<QueuedBooster>> queuedBoosters,
    List<BoosterClaim> pendingClaims
) {

    public PlayerBoostSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
        inventory = copyInventory(inventory);
        activeBoosters = copyActiveBoosters(playerId, activeBoosters);
        queuedBoosters = copyQueuedBoosters(playerId, queuedBoosters);
        pendingClaims = copyPendingClaims(playerId, pendingClaims);
    }

    public static PlayerBoostSnapshot empty(UUID playerId) {
        return new PlayerBoostSnapshot(playerId, 0, Map.of(), Map.of(), Map.of(), List.of());
    }

    public long ownedAmount(BoosterId boosterId) {
        return inventory.getOrDefault(Objects.requireNonNull(boosterId, "boosterId"), 0L);
    }

    public long ownedTotal() {
        long total = 0;
        for (long amount : inventory.values()) {
            total = Math.addExact(total, amount);
        }
        return total;
    }

    private static Map<BoosterId, Long> copyInventory(Map<BoosterId, Long> rawInventory) {
        Objects.requireNonNull(rawInventory, "inventory");
        LinkedHashMap<BoosterId, Long> copied = new LinkedHashMap<>();
        rawInventory.forEach((boosterId, amount) -> {
            Objects.requireNonNull(boosterId, "boosterId");
            Objects.requireNonNull(amount, "amount");
            if (amount <= 0) {
                throw new IllegalArgumentException("inventory amount must be positive");
            }
            copied.put(boosterId, amount);
        });
        return Map.copyOf(copied);
    }

    private static Map<ActivationGroup, ActiveBooster> copyActiveBoosters(
        UUID playerId,
        Map<ActivationGroup, ActiveBooster> rawActiveBoosters
    ) {
        Objects.requireNonNull(rawActiveBoosters, "activeBoosters");
        LinkedHashMap<ActivationGroup, ActiveBooster> copied = new LinkedHashMap<>();
        rawActiveBoosters.forEach((group, activeBooster) -> {
            Objects.requireNonNull(group, "group");
            ActiveBooster active = Objects.requireNonNull(activeBooster, "activeBooster");
            if (!playerId.equals(active.playerId())) {
                throw new IllegalArgumentException("active booster playerId must match snapshot playerId");
            }
            if (!group.equals(active.activationGroup())) {
                throw new IllegalArgumentException("active booster group must match its map key");
            }
            copied.put(group, active);
        });
        return Map.copyOf(copied);
    }

    private static Map<ActivationGroup, List<QueuedBooster>> copyQueuedBoosters(
        UUID playerId,
        Map<ActivationGroup, List<QueuedBooster>> rawQueuedBoosters
    ) {
        Objects.requireNonNull(rawQueuedBoosters, "queuedBoosters");
        LinkedHashMap<ActivationGroup, List<QueuedBooster>> copied = new LinkedHashMap<>();
        rawQueuedBoosters.forEach((group, queue) -> {
            Objects.requireNonNull(group, "group");
            List<QueuedBooster> copiedQueue = List.copyOf(Objects.requireNonNull(queue, "queue"));
            long previousPosition = -1;
            for (QueuedBooster queuedBooster : copiedQueue) {
                if (!playerId.equals(queuedBooster.playerId())) {
                    throw new IllegalArgumentException("queued booster playerId must match snapshot playerId");
                }
                if (!group.equals(queuedBooster.activationGroup())) {
                    throw new IllegalArgumentException("queued booster group must match its map key");
                }
                if (queuedBooster.position() <= previousPosition) {
                    throw new IllegalArgumentException("queued boosters must have strictly increasing positions");
                }
                previousPosition = queuedBooster.position();
            }
            copied.put(group, copiedQueue);
        });
        return Map.copyOf(copied);
    }

    private static List<BoosterClaim> copyPendingClaims(UUID playerId, List<BoosterClaim> rawPendingClaims) {
        List<BoosterClaim> copied = List.copyOf(Objects.requireNonNull(rawPendingClaims, "pendingClaims"));
        HashSet<UUID> claimIds = new HashSet<>();
        for (BoosterClaim claim : copied) {
            if (!playerId.equals(claim.playerId())) {
                throw new IllegalArgumentException("claim playerId must match snapshot playerId");
            }
            if (claim.status() != ClaimStatus.PENDING) {
                throw new IllegalArgumentException("pendingClaims must contain only pending claims");
            }
            if (!claimIds.add(claim.claimId())) {
                throw new IllegalArgumentException("pendingClaims cannot contain duplicate claim IDs");
            }
        }
        return copied;
    }
}
