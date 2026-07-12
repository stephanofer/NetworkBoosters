package com.stephanofer.networkboosters.api.request;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.util.Objects;
import java.util.UUID;

public record ActivationRequest(
    UUID playerId,
    BoosterId boosterId,
    ActivationSource source,
    SourceReference sourceReference
) {

    public ActivationRequest {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boosterId, "boosterId");
        Objects.requireNonNull(source, "source");
        sourceReference = Objects.requireNonNullElse(sourceReference, SourceReference.none());
    }
}
