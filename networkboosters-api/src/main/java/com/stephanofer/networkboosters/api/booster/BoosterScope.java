package com.stephanofer.networkboosters.api.booster;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record BoosterScope(
    BoosterScopeType type,
    Set<String> gameIds,
    Set<String> serverIds
) {

    public static final String WILDCARD = "*";
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public BoosterScope {
        Objects.requireNonNull(type, "type");
        gameIds = normalizeScopeValues(gameIds, "gameIds");
        serverIds = normalizeScopeValues(serverIds, "serverIds");
    }

    public static BoosterScope personalGlobal() {
        return new BoosterScope(BoosterScopeType.PERSONAL, Set.of(WILDCARD), Set.of(WILDCARD));
    }

    public boolean appliesTo(Optional<String> gameId, Optional<String> serverId) {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(serverId, "serverId");
        return matches(gameIds, gameId) && matches(serverIds, serverId);
    }

    public boolean appliesTo(String gameId, String serverId) {
        return appliesTo(Optional.ofNullable(gameId), Optional.ofNullable(serverId));
    }

    private static boolean matches(Set<String> allowed, Optional<String> actual) {
        if (allowed.contains(WILDCARD)) {
            return true;
        }
        return actual.map(value -> allowed.contains(normalizeIdentifier(value, "scope value"))).orElse(false);
    }

    private static Set<String> normalizeScopeValues(Set<String> rawValues, String label) {
        Objects.requireNonNull(rawValues, label);
        if (rawValues.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : rawValues) {
            String value = normalizeIdentifier(raw, label);
            if (!normalized.add(value)) {
                throw new IllegalArgumentException("Duplicate " + label + " value: " + value);
            }
        }
        if (normalized.size() > 1 && normalized.contains(WILDCARD)) {
            throw new IllegalArgumentException(label + " cannot mix wildcard with explicit values");
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeIdentifier(String raw, String label) {
        String normalized = Objects.requireNonNull(raw, label).trim().toLowerCase(Locale.ROOT);
        if (WILDCARD.equals(normalized)) {
            return WILDCARD;
        }
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + raw);
        }
        return normalized;
    }
}
