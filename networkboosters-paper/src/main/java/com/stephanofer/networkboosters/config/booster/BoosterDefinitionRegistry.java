package com.stephanofer.networkboosters.config.booster;

import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BoosterDefinitionRegistry {

    private static final BoosterDefinitionRegistry EMPTY = new BoosterDefinitionRegistry(Map.of());

    private final Map<BoosterId, BoosterDefinition> definitions;
    private final List<BoosterDefinition> orderedDefinitions;

    public BoosterDefinitionRegistry(Map<BoosterId, BoosterDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        this.orderedDefinitions = definitions.values().stream()
            .sorted(Comparator
                .comparingInt(BoosterDefinition::displayOrder)
                .thenComparing(definition -> definition.id().value()))
            .toList();

        LinkedHashMap<BoosterId, BoosterDefinition> ordered = new LinkedHashMap<>();
        for (BoosterDefinition definition : this.orderedDefinitions) {
            ordered.put(definition.id(), definition);
        }
        this.definitions = Map.copyOf(ordered);
    }

    public static BoosterDefinitionRegistry empty() {
        return EMPTY;
    }

    public Optional<BoosterDefinition> find(BoosterId id) {
        return Optional.ofNullable(this.definitions.get(Objects.requireNonNull(id, "id")));
    }

    public Collection<BoosterDefinition> definitions() {
        return this.orderedDefinitions;
    }

    public Map<BoosterId, BoosterDefinition> asMap() {
        return this.definitions;
    }

    public int size() {
        return this.definitions.size();
    }
}
