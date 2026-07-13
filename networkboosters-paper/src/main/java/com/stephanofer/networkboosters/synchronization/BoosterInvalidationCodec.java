package com.stephanofer.networkboosters.synchronization;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BoosterInvalidationCodec {

    private static final int MAX_PAYLOAD_LENGTH = 16_384;
    private static final int MAX_CHANGES = 32;
    private static final Pattern INTEGER = Pattern.compile("\\\"%s\\\"\\s*:\\s*(-?\\d+)");
    private static final Pattern STRING = Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern CHANGES = Pattern.compile("\\\"changes\\\"\\s*:\\s*\\[(.*)]\\s*}", Pattern.DOTALL);
    private static final Pattern OBJECT = Pattern.compile("\\{([^{}]*)}");

    public String encode(BoosterInvalidation invalidation) {
        StringBuilder builder = new StringBuilder(256);
        builder.append('{')
            .append("\"schemaVersion\":").append(invalidation.schemaVersion()).append(',')
            .append("\"eventId\":\"").append(invalidation.eventId()).append("\",")
            .append("\"sourceServerId\":\"").append(escape(invalidation.sourceServerId())).append("\",")
            .append("\"occurredAt\":\"").append(invalidation.occurredAt()).append("\",")
            .append("\"changes\":[");
        for (int index = 0; index < invalidation.changes().size(); index++) {
            BoosterInvalidation.PlayerChange change = invalidation.changes().get(index);
            if (index > 0) {
                builder.append(',');
            }
            builder.append('{')
                .append("\"playerId\":\"").append(change.playerId()).append("\",")
                .append("\"revision\":").append(change.revision()).append(',')
                .append("\"type\":\"").append(change.type()).append("\"");
            change.referenceId().ifPresent(reference -> builder.append(',').append("\"referenceId\":\"").append(reference).append("\""));
            change.transfer().ifPresent(transfer -> builder
                .append(',').append("\"transferId\":\"").append(transfer.transferId()).append("\"")
                .append(',').append("\"senderId\":\"").append(transfer.senderId()).append("\"")
                .append(',').append("\"recipientId\":\"").append(transfer.recipientId()).append("\"")
                .append(',').append("\"boosterId\":\"").append(transfer.boosterId().value()).append("\"")
                .append(',').append("\"amount\":").append(transfer.amount())
                .append(',').append("\"senderRevision\":").append(transfer.senderRevision())
                .append(',').append("\"recipientRevision\":").append(transfer.recipientRevision())
            );
            builder.append('}');
        }
        return builder.append("]}").toString();
    }

    public Optional<BoosterInvalidation> decode(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > MAX_PAYLOAD_LENGTH) {
            return Optional.empty();
        }
        try {
            int schemaVersion = schemaVersion(payload);
            if (schemaVersion != BoosterInvalidation.CURRENT_SCHEMA) {
                return Optional.empty();
            }
            UUID eventId = UUID.fromString(string(payload, "eventId"));
            String sourceServerId = string(payload, "sourceServerId");
            Instant occurredAt = Instant.parse(string(payload, "occurredAt"));
            Matcher changesMatcher = CHANGES.matcher(payload);
            if (!changesMatcher.find()) {
                return Optional.empty();
            }
            ArrayList<BoosterInvalidation.PlayerChange> changes = new ArrayList<>();
            Matcher objectMatcher = OBJECT.matcher(changesMatcher.group(1));
            while (objectMatcher.find()) {
                if (changes.size() >= MAX_CHANGES) {
                    return Optional.empty();
                }
                String raw = objectMatcher.group(1);
                UUID playerId = UUID.fromString(string(raw, "playerId"));
                long revision = longValue(raw, "revision");
                BoosterChangeType type = BoosterChangeType.valueOf(string(raw, "type"));
                Optional<UUID> referenceId = optionalString(raw, "referenceId").map(UUID::fromString);
                Optional<BoosterInvalidation.TransferDetails> transfer = transfer(raw);
                changes.add(new BoosterInvalidation.PlayerChange(playerId, revision, type, referenceId, transfer));
            }
            return Optional.of(new BoosterInvalidation(schemaVersion, eventId, sourceServerId, occurredAt, List.copyOf(changes)));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private static int schemaVersion(String payload) {
        long value = longValue(payload, "schemaVersion");
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid schemaVersion", exception);
        }
    }

    private static long longValue(String payload, String field) {
        Matcher matcher = Pattern.compile(INTEGER.pattern().formatted(Pattern.quote(field))).matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing integer field " + field);
        }
        return Long.parseLong(matcher.group(1));
    }

    private static String string(String payload, String field) {
        return optionalString(payload, field).orElseThrow(() -> new IllegalArgumentException("Missing string field " + field));
    }

    private static Optional<String> optionalString(String payload, String field) {
        Matcher matcher = Pattern.compile(STRING.pattern().formatted(Pattern.quote(field))).matcher(payload);
        return matcher.find() ? Optional.of(unescape(matcher.group(1))) : Optional.empty();
    }

    private static Optional<BoosterInvalidation.TransferDetails> transfer(String raw) {
        Optional<String> transferId = optionalString(raw, "transferId");
        if (transferId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BoosterInvalidation.TransferDetails(
            UUID.fromString(transferId.orElseThrow()),
            UUID.fromString(string(raw, "senderId")),
            UUID.fromString(string(raw, "recipientId")),
            com.stephanofer.networkboosters.api.booster.BoosterId.of(string(raw, "boosterId")),
            longValue(raw, "amount"),
            longValue(raw, "senderRevision"),
            longValue(raw, "recipientRevision")
        ));
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String raw) {
        return raw.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
