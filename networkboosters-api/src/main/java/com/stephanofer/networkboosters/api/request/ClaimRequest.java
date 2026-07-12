package com.stephanofer.networkboosters.api.request;

import java.util.Objects;
import java.util.UUID;

public record ClaimRequest(UUID playerId, UUID claimId) {

    public ClaimRequest {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(claimId, "claimId");
    }
}
