package com.stephanofer.networkboosters.menu;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class BoosterMenuViews {

    private BoosterMenuViews() {
    }

    public static List<OwnedBoosterView> ownedViews(
        PlayerBoostSnapshot snapshot,
        Map<BoosterId, BoosterDefinition> definitions,
        Predicate<String> permissionChecker,
        String gameId,
        String serverId,
        BoosterMenuFilter filter,
        BoosterMenuSort sort
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(permissionChecker, "permissionChecker");
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(sort, "sort");
        ArrayList<OwnedBoosterView> views = new ArrayList<>();
        snapshot.inventory().forEach((boosterId, amount) -> views.add(view(snapshot, definitions.get(boosterId), boosterId, amount, permissionChecker, gameId, serverId)));
        return views.stream()
            .filter(filter(filter))
            .sorted(comparator(sort))
            .toList();
    }

    private static OwnedBoosterView view(
        PlayerBoostSnapshot snapshot,
        BoosterDefinition definition,
        BoosterId boosterId,
        long amount,
        Predicate<String> permissionChecker,
        String gameId,
        String serverId
    ) {
        if (definition == null) {
            return new OwnedBoosterView(boosterId, amount, Optional.empty(), BoosterVisualState.ORPHANED, false, false, false, Optional.empty(), List.of());
        }
        Optional<ActiveBooster> active = Optional.ofNullable(snapshot.activeBoosters().get(definition.activationGroup()));
        List<com.stephanofer.networkboosters.api.booster.QueuedBooster> queue = snapshot.queuedBoosters().getOrDefault(definition.activationGroup(), List.of());
        boolean permission = definition.requirements().satisfiedBy(permissionChecker);
        boolean applicable = applies(definition.scope(), gameId, serverId);
        boolean transferable = definition.transferPolicy().enabled();
        BoosterVisualState state;
        if (!definition.enabled()) {
            state = BoosterVisualState.DISABLED;
        } else if (!permission) {
            state = BoosterVisualState.BLOCKED_PERMISSION;
        } else if (!applicable) {
            state = BoosterVisualState.OUT_OF_SCOPE;
        } else if (active.map(value -> value.boosterId().equals(boosterId)).orElse(false)) {
            state = BoosterVisualState.EXTENDABLE;
        } else if (active.isPresent()) {
            state = switch (definition.conflictPolicy()) {
                case QUEUE -> queue.stream().anyMatch(queued -> queued.boosterId().equals(boosterId)) ? BoosterVisualState.QUEUED : BoosterVisualState.WOULD_QUEUE;
                case REJECT -> BoosterVisualState.ACTIVE;
                case REPLACE -> BoosterVisualState.WOULD_REPLACE;
            };
        } else if (queue.stream().anyMatch(queued -> queued.boosterId().equals(boosterId))) {
            state = BoosterVisualState.QUEUED;
        } else if (!transferable) {
            state = BoosterVisualState.NOT_TRANSFERABLE;
        } else {
            state = BoosterVisualState.AVAILABLE;
        }
        return new OwnedBoosterView(boosterId, amount, Optional.of(definition), state, applicable, permission, transferable, active, queue);
    }

    private static Predicate<OwnedBoosterView> filter(BoosterMenuFilter filter) {
        return switch (filter) {
            case ALL -> ignored -> true;
            case ACTIVE -> view -> view.active().map(active -> active.boosterId().equals(view.boosterId())).orElse(false) || view.state() == BoosterVisualState.EXTENDABLE;
            case CURRENT_CONTEXT -> OwnedBoosterView::applicableNow;
            case POINTS -> view -> view.definition().map(definition -> definition.target().key().equals("network_progression:points")).orElse(false);
            case LOCKED -> view -> view.state() == BoosterVisualState.BLOCKED_PERMISSION;
            case TRANSFERABLE -> OwnedBoosterView::transferable;
        };
    }

    private static Comparator<OwnedBoosterView> comparator(BoosterMenuSort sort) {
        Comparator<OwnedBoosterView> recommended = Comparator
            .comparing((OwnedBoosterView view) -> view.active().isPresent()).reversed()
            .thenComparing(OwnedBoosterView::applicableNow, Comparator.reverseOrder())
            .thenComparing(OwnedBoosterView::activationAllowed, Comparator.reverseOrder())
            .thenComparingInt(view -> view.definition().map(BoosterDefinition::displayOrder).orElse(Integer.MAX_VALUE))
            .thenComparing((OwnedBoosterView view) -> view.definition().map(BoosterDefinition::duration).orElse(java.time.Duration.ZERO), Comparator.reverseOrder())
            .thenComparing(view -> view.boosterId().value());
        if (sort == BoosterMenuSort.QUANTITY) {
            return Comparator.comparingLong(OwnedBoosterView::amount).reversed().thenComparing(recommended);
        }
        return recommended;
    }

    private static boolean applies(BoosterScope scope, String gameId, String serverId) {
        return scope.appliesTo(gameId, serverId);
    }
}
