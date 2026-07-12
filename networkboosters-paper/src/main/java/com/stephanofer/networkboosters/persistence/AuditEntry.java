package com.stephanofer.networkboosters.persistence;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AuditEntry(
    UUID operationId,
    String operationType,
    String actorType,
    Optional<UUID> actorId,
    UUID affectedPlayerId,
    Optional<BoosterId> boosterId,
    Optional<Long> amount,
    Optional<String> previousValueJson,
    Optional<String> newValueJson,
    Optional<UUID> activationId,
    Optional<UUID> transferId,
    String sourceType,
    Optional<String> sourceReference,
    Optional<String> sourceServerId,
    String result
) {

    public AuditEntry {
        Objects.requireNonNull(operationId, "operationId");
        operationType = requireNotBlank(operationType, "operationType");
        actorType = requireNotBlank(actorType, "actorType");
        actorId = Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(affectedPlayerId, "affectedPlayerId");
        boosterId = Objects.requireNonNull(boosterId, "boosterId");
        amount = Objects.requireNonNull(amount, "amount");
        previousValueJson = normalizeOptional(previousValueJson, "previousValueJson");
        newValueJson = normalizeOptional(newValueJson, "newValueJson");
        activationId = Objects.requireNonNull(activationId, "activationId");
        transferId = Objects.requireNonNull(transferId, "transferId");
        sourceType = requireNotBlank(sourceType, "sourceType");
        sourceReference = normalizeOptional(sourceReference, "sourceReference");
        sourceServerId = normalizeOptional(sourceServerId, "sourceServerId");
        result = requireNotBlank(result, "result");
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(String::trim).filter(raw -> !raw.isEmpty());
    }

    private static String requireNotBlank(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
