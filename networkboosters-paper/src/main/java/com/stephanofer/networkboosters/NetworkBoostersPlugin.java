package com.stephanofer.networkboosters;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import com.stephanofer.networkboosters.lifecycle.NetworkBoostersLifecycle;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.paper.PaperCommandManager;

public final class NetworkBoostersPlugin extends JavaPlugin {

    private final NetworkBoostersLifecycle lifecycle;

    public NetworkBoostersPlugin(PaperCommandManager.Bootstrapped<CommandSourceStack> commandManager) {
        this.lifecycle = new NetworkBoostersLifecycle(this, commandManager);
    }

    @Override
    public void onEnable() {
        this.lifecycle.enable();
    }

    @Override
    public void onDisable() {
        this.lifecycle.disable();
    }
}
