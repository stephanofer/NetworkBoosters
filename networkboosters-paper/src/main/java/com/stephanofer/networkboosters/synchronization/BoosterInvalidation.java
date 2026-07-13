package com.stephanofer.networkboosters.synchronization;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BoosterInvalidation(
    int schemaVersion,
    UUID eventId,
    String sourceServerId,
    Instant occurredAt,
    List<PlayerChange> changes
) {

    public static final int CURRENT_SCHEMA = 1;

    public BoosterInvalidation {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported schema version: " + schemaVersion);
        }
        Objects.requireNonNull(eventId, "eventId");
        sourceServerId = requireNotBlank(sourceServerId, "sourceServerId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("changes cannot be empty");
        }
    }

    public record PlayerChange(
        UUID playerId,
        long revision,
        BoosterChangeType type,
        Optional<UUID> referenceId,
        Optional<TransferDetails> transfer
    ) {
        public PlayerChange {
            Objects.requireNonNull(playerId, "playerId");
            if (revision <= 0) {
                throw new IllegalArgumentException("revision must be positive");
            }
            Objects.requireNonNull(type, "type");
            referenceId = Objects.requireNonNull(referenceId, "referenceId");
            transfer = Objects.requireNonNull(transfer, "transfer");
        }

        public PlayerChange(UUID playerId, long revision, BoosterChangeType type, Optional<UUID> referenceId) {
            this(playerId, revision, type, referenceId, Optional.empty());
        }
    }

    public record TransferDetails(
        UUID transferId,
        UUID senderId,
        UUID recipientId,
        BoosterId boosterId,
        long amount,
        long senderRevision,
        long recipientRevision
    ) {
        public TransferDetails {
            Objects.requireNonNull(transferId, "transferId");
            Objects.requireNonNull(senderId, "senderId");
            Objects.requireNonNull(recipientId, "recipientId");
            Objects.requireNonNull(boosterId, "boosterId");
            if (amount <= 0 || senderRevision <= 0 || recipientRevision <= 0) {
                throw new IllegalArgumentException("transfer amount and revisions must be positive");
            }
        }
    }

    private static String requireNotBlank(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return normalized;
    }
}
