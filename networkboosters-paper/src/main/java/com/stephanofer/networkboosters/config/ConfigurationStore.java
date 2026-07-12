package com.stephanofer.networkboosters.config;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigurationStore {

    private final AtomicReference<ConfigurationSnapshot> current = new AtomicReference<>();
    private final AtomicLong generations = new AtomicLong();

    public Optional<ConfigurationSnapshot> current() {
        return Optional.ofNullable(this.current.get());
    }

    public ConfigurationSnapshot requireCurrent() {
        ConfigurationSnapshot snapshot = this.current.get();
        if (snapshot == null) {
            throw new IllegalStateException("Configuration has not been initialized");
        }
        return snapshot;
    }

    public synchronized ConfigurationSnapshot initialize(ConfigurationSnapshot candidate) {
        Objects.requireNonNull(candidate, "candidate");
        ConfigurationSnapshot published = candidate.withGeneration(this.generations.incrementAndGet());
        if (!this.current.compareAndSet(null, published)) {
            throw new IllegalStateException("Configuration has already been initialized");
        }
        return published;
    }

    public synchronized ConfigurationSnapshot replace(ConfigurationSnapshot candidate) {
        Objects.requireNonNull(candidate, "candidate");
        ConfigurationSnapshot previous = this.requireCurrent();
        ConfigurationChanges changes = ConfigurationChanges.between(previous.configuration(), candidate.configuration());
        if (changes.requiresRestart()) {
            throw new IllegalArgumentException(
                "Configuration changes require restart: " + String.join(", ", changes.restartRequiredPaths())
            );
        }
        ConfigurationSnapshot published = candidate
            .withConfigurationChanges(changes)
            .withGeneration(this.generations.incrementAndGet());
        this.current.set(published);
        return published;
    }

    public synchronized void clear() {
        this.current.set(null);
    }
}
