package com.stephanofer.networkboosters.api.booster;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ActivationGroup(String value) {

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    public ActivationGroup {
        value = normalize(value, "activation group");
    }

    public static ActivationGroup of(String value) {
        return new ActivationGroup(value);
    }

    private static String normalize(String raw, String label) {
        String normalized = Objects.requireNonNull(raw, label).trim().toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + raw);
        }
        return normalized;
    }

    @Override
    public String toString() {
        return value;
    }
}
