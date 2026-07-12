package com.stephanofer.networkboosters.inventory;

import com.stephanofer.networkboosters.capacity.InventoryCapacityResolver;
import com.stephanofer.networkboosters.capacity.ResolvedInventoryCapacity;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public final class PlayerCapacityProvider {

    private final Server server;
    private final InventoryCapacityResolver resolver;

    public PlayerCapacityProvider(Server server, InventoryCapacityResolver resolver) {
        this.server = Objects.requireNonNull(server, "server");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public ResolvedInventoryCapacity resolve(UUID playerId, NetworkBoostersConfiguration.InventoryLimits limits) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(limits, "limits");
        Player player = this.server.getPlayer(playerId);
        return this.resolver.resolve(
            limits.fallback(),
            limits.tiers(),
            permission -> player != null && player.hasPermission(permission)
        );
    }
}
