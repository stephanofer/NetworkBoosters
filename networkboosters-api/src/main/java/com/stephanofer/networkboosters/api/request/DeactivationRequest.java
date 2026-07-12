package com.stephanofer.networkboosters.api.request;

import com.stephanofer.networkboosters.api.source.DeactivationReason;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.util.Objects;
import java.util.UUID;

public record DeactivationRequest(
    UUID activationId,
    DeactivationReason reason,
    SourceReference sourceReference
) {

    public DeactivationRequest {
        Objects.requireNonNull(activationId, "activationId");
        Objects.requireNonNull(reason, "reason");
        sourceReference = Objects.requireNonNullElse(sourceReference, SourceReference.none());
    }
}
