package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import java.util.Objects;

public final class QueueCompatibility {

    private QueueCompatibility() {
    }

    public static boolean matches(ActiveBooster activeBooster, BoosterDefinition definition) {
        Objects.requireNonNull(activeBooster, "activeBooster");
        Objects.requireNonNull(definition, "definition");
        return activeBooster.boosterId().equals(definition.id())
            && activeBooster.target().equals(definition.target())
            && activeBooster.multiplier().compareTo(definition.multiplier()) == 0
            && activeBooster.activationGroup().equals(definition.activationGroup())
            && activeBooster.conflictPolicy() == definition.conflictPolicy()
            && activeBooster.scope().equals(definition.scope())
            && activeBooster.requirements().equals(definition.requirements());
    }

    public static boolean matches(QueuedBooster queuedBooster, BoosterDefinition definition) {
        Objects.requireNonNull(queuedBooster, "queuedBooster");
        Objects.requireNonNull(definition, "definition");
        return queuedBooster.boosterId().equals(definition.id())
            && queuedBooster.target().equals(definition.target())
            && queuedBooster.multiplier().compareTo(definition.multiplier()) == 0
            && queuedBooster.activationGroup().equals(definition.activationGroup())
            && queuedBooster.conflictPolicy() == definition.conflictPolicy()
            && queuedBooster.scope().equals(definition.scope())
            && queuedBooster.requirements().equals(definition.requirements());
    }
}
