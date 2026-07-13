package com.stephanofer.networkboosters.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ConfigurationChanges(List<String> restartRequiredPaths) {

    public ConfigurationChanges {
        restartRequiredPaths = List.copyOf(Objects.requireNonNull(restartRequiredPaths, "restartRequiredPaths"));
    }

    public static ConfigurationChanges initial() {
        return new ConfigurationChanges(List.of());
    }

    public static ConfigurationChanges between(NetworkBoostersConfiguration previous, NetworkBoostersConfiguration candidate) {
        if (previous == null) {
            return initial();
        }
        Objects.requireNonNull(candidate, "candidate");
        List<String> restartRequiredPaths = new ArrayList<>();
        if (!previous.serverId().equals(candidate.serverId())) {
            restartRequiredPaths.add("server.id");
        }
        if (!previous.gameId().equals(candidate.gameId())) {
            restartRequiredPaths.add("server.game-id");
        }
        if (!previous.storage().equals(candidate.storage())) {
            restartRequiredPaths.add("storage");
        }
        if (!previous.redis().equals(candidate.redis())) {
            restartRequiredPaths.add("redis");
        }
        if (!previous.commands().equals(candidate.commands())) {
            restartRequiredPaths.add("commands");
        }
        if (previous.placeholderApi().enabled() != candidate.placeholderApi().enabled()) {
            restartRequiredPaths.add("placeholderapi.enabled");
        }
        return new ConfigurationChanges(restartRequiredPaths);
    }

    public boolean requiresRestart() {
        return !this.restartRequiredPaths.isEmpty();
    }
}
