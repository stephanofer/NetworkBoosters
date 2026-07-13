package com.stephanofer.networkboosters.menu;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class MenuSessionStore {

    private final ConcurrentHashMap<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    public MenuSession get(UUID playerId) {
        return this.sessions.computeIfAbsent(Objects.requireNonNull(playerId, "playerId"), ignored -> MenuSession.initial());
    }

    public MenuSession update(UUID playerId, UnaryOperator<MenuSession> updater) {
        Objects.requireNonNull(updater, "updater");
        return this.sessions.compute(Objects.requireNonNull(playerId, "playerId"), (ignored, current) -> updater.apply(current == null ? MenuSession.initial() : current));
    }

    public Optional<MenuSession> find(UUID playerId) {
        return Optional.ofNullable(this.sessions.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public void remove(UUID playerId) {
        this.sessions.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public void clear() {
        this.sessions.clear();
    }
}
