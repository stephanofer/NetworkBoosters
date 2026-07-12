package com.stephanofer.networkboosters.api.source;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SourceReference(
    Optional<UUID> actorId,
    Optional<String> externalReference,
    Optional<String> serverId
) {

    private static final SourceReference NONE = new SourceReference(Optional.empty(), Optional.empty(), Optional.empty());

    public SourceReference {
        actorId = Objects.requireNonNull(actorId, "actorId");
        externalReference = normalizeText(externalReference, "externalReference");
        serverId = normalizeText(serverId, "serverId");
    }

    public static SourceReference none() {
        return NONE;
    }

    public static SourceReference actor(UUID actorId) {
        return new SourceReference(Optional.of(actorId), Optional.empty(), Optional.empty());
    }

    private static Optional<String> normalizeText(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(String::trim).filter(raw -> !raw.isEmpty());
    }
}
