package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.request.ActivationRequest;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BoosterPreActivateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ActivationRequest request;
    private final BoosterDefinition definition;
    private final PlayerBoostSnapshot snapshot;
    private boolean cancelled;

    public BoosterPreActivateEvent(
        Player player,
        ActivationRequest request,
        BoosterDefinition definition,
        PlayerBoostSnapshot snapshot
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.request = Objects.requireNonNull(request, "request");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (!player.getUniqueId().equals(request.playerId()) || !player.getUniqueId().equals(snapshot.playerId())) {
            throw new IllegalArgumentException("player must match request and snapshot");
        }
    }

    public Player player() {
        return this.player;
    }

    public ActivationRequest request() {
        return this.request;
    }

    public BoosterDefinition definition() {
        return this.definition;
    }

    public PlayerBoostSnapshot snapshot() {
        return this.snapshot;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
