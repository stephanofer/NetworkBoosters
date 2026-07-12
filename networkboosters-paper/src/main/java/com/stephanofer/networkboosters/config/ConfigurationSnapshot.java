package com.stephanofer.networkboosters.config;

import com.stephanofer.networkboosters.config.booster.BoosterDefinitionRegistry;
import com.stephanofer.networkboosters.config.booster.DefinitionChanges;
import java.util.List;
import java.util.Objects;

public record ConfigurationSnapshot(
    long generation,
    NetworkBoostersConfiguration configuration,
    BoosterDefinitionRegistry definitions,
    DefinitionChanges definitionChanges,
    ConfigurationChanges configurationChanges,
    List<ConfigurationIssue> warnings
) {

    public ConfigurationSnapshot {
        if (generation < 0) {
            throw new IllegalArgumentException("generation cannot be negative");
        }
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(definitionChanges, "definitionChanges");
        Objects.requireNonNull(configurationChanges, "configurationChanges");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public ConfigurationSnapshot withGeneration(long newGeneration) {
        return new ConfigurationSnapshot(newGeneration, configuration, definitions, definitionChanges, configurationChanges, warnings);
    }

    public ConfigurationSnapshot withConfigurationChanges(ConfigurationChanges changes) {
        return new ConfigurationSnapshot(generation, configuration, definitions, definitionChanges, changes, warnings);
    }
}
