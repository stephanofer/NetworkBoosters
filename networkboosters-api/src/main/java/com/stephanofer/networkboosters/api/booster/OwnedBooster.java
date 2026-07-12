package com.stephanofer.networkboosters.api.booster;

import java.util.Objects;
import java.util.UUID;

public record OwnedBooster(UUID playerId, BoosterId boosterId, long amount) {

    public OwnedBooster {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boosterId, "boosterId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
