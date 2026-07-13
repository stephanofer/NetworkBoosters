package com.stephanofer.networkboosters.menu;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record OwnedBoosterView(
    BoosterId boosterId,
    long amount,
    Optional<BoosterDefinition> definition,
    BoosterVisualState state,
    boolean applicableNow,
    boolean activationAllowed,
    boolean transferable,
    Optional<ActiveBooster> active,
    List<QueuedBooster> queue
) {

    public OwnedBoosterView {
        Objects.requireNonNull(boosterId, "boosterId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(state, "state");
        active = Objects.requireNonNull(active, "active");
        queue = List.copyOf(Objects.requireNonNull(queue, "queue"));
    }
}
