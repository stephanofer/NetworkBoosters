package com.stephanofer.networkboosters.booster;

import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PlayerPermissionProvider {

    private final Server server;
    private final Plugin plugin;

    public PlayerPermissionProvider(Server server, Plugin plugin) {
        this.server = Objects.requireNonNull(server, "server");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public CompletableFuture<Boolean> satisfies(UUID playerId, ActivationRequirements requirements) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(requirements, "requirements");
        if (Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(this.satisfiesNow(playerId, requirements));
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        this.server.getScheduler().runTask(this.plugin, () -> result.complete(this.satisfiesNow(playerId, requirements)));
        return result;
    }

    private boolean satisfiesNow(UUID playerId, ActivationRequirements requirements) {
        if (requirements.permissions().isEmpty()) {
            return true;
        }
        Player player = this.server.getPlayer(playerId);
        return player != null && player.isOnline() && requirements.satisfiedBy(player::hasPermission);
    }
}
