package com.stephanofer.networkboosters.api.event;

import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class PlayerBoostersReadyEvent extends AbstractPlayerBoostersEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public PlayerBoostersReadyEvent(Player player, PlayerBoostSnapshot snapshot, String sourceServerId) {
        super(player.getUniqueId(), snapshot.revision(), BoosterEventOrigin.LOCAL, sourceServerId, snapshot);
        this.player = Objects.requireNonNull(player, "player");
    }

    public Player player() {
        return this.player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
