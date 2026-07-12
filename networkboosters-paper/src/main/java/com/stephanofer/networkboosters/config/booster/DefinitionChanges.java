package com.stephanofer.networkboosters.config.booster;

import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DefinitionChanges(
    List<BoosterId> added,
    List<BoosterId> modified,
    List<BoosterId> enabled,
    List<BoosterId> disabled,
    List<BoosterId> removed,
    List<BoosterId> unchanged
) {

    public DefinitionChanges {
        added = sortedCopy(added);
        modified = sortedCopy(modified);
        enabled = sortedCopy(enabled);
        disabled = sortedCopy(disabled);
        removed = sortedCopy(removed);
        unchanged = sortedCopy(unchanged);
    }

    public static DefinitionChanges between(BoosterDefinitionRegistry previous, BoosterDefinitionRegistry current) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");

        Map<BoosterId, BoosterDefinition> previousMap = previous.asMap();
        Map<BoosterId, BoosterDefinition> currentMap = current.asMap();
        List<BoosterId> added = new ArrayList<>();
        List<BoosterId> modified = new ArrayList<>();
        List<BoosterId> enabled = new ArrayList<>();
        List<BoosterId> disabled = new ArrayList<>();
        List<BoosterId> removed = new ArrayList<>();
        List<BoosterId> unchanged = new ArrayList<>();

        for (Map.Entry<BoosterId, BoosterDefinition> entry : currentMap.entrySet()) {
            BoosterDefinition before = previousMap.get(entry.getKey());
            BoosterDefinition after = entry.getValue();
            if (before == null) {
                added.add(entry.getKey());
                continue;
            }
            if (before.equals(after)) {
                unchanged.add(entry.getKey());
                continue;
            }
            modified.add(entry.getKey());
            if (!before.enabled() && after.enabled()) {
                enabled.add(entry.getKey());
            }
            if (before.enabled() && !after.enabled()) {
                disabled.add(entry.getKey());
            }
        }

        for (BoosterId id : previousMap.keySet()) {
            if (!currentMap.containsKey(id)) {
                removed.add(id);
            }
        }

        return new DefinitionChanges(added, modified, enabled, disabled, removed, unchanged);
    }

    private static List<BoosterId> sortedCopy(List<BoosterId> ids) {
        return ids.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(BoosterId::value))
            .toList();
    }
}
