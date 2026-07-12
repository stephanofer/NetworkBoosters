package com.stephanofer.networkboosters.api.booster;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record BoosterCategory(String value) {

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    public BoosterCategory {
        value = normalize(value);
    }

    public static BoosterCategory of(String value) {
        return new BoosterCategory(value);
    }

    private static String normalize(String raw) {
        String normalized = Objects.requireNonNull(raw, "category").trim().toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid booster category: " + raw);
        }
        return normalized;
    }

    @Override
    public String toString() {
        return value;
    }
}
