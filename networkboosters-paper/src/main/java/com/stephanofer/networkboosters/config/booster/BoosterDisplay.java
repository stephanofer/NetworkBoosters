package com.stephanofer.networkboosters.config.booster;

import java.util.Objects;
import java.util.OptionalInt;

public record BoosterDisplay(
    String material,
    String lockedMaterial,
    String activeMaterial,
    OptionalInt customModelData,
    boolean glow
) {

    private static final BoosterDisplay DEFAULTS = new BoosterDisplay(
        "TRIAL_KEY",
        "OMINOUS_TRIAL_KEY",
        "HEAVY_CORE",
        OptionalInt.empty(),
        false
    );

    public BoosterDisplay {
        material = normalize(material, "material");
        lockedMaterial = normalize(lockedMaterial, "lockedMaterial");
        activeMaterial = normalize(activeMaterial, "activeMaterial");
        customModelData = Objects.requireNonNull(customModelData, "customModelData");
    }

    public static BoosterDisplay defaults() {
        return DEFAULTS;
    }

    private static String normalize(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return normalized;
    }
}
