package com.stephanofer.networkboosters.api.booster;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record BoosterTarget(String key) {

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}:[a-z0-9][a-z0-9/._-]{0,127}");
    public static final BoosterTarget NETWORK_PROGRESSION_POINTS = new BoosterTarget("network_progression:points");

    public BoosterTarget {
        key = normalize(key);
    }

    public static BoosterTarget of(String key) {
        return new BoosterTarget(key);
    }

    private static String normalize(String raw) {
        String normalized = Objects.requireNonNull(raw, "target").trim().toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid booster target: " + raw);
        }
        return normalized;
    }

    @Override
    public String toString() {
        return key;
    }
}
