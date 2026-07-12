package com.stephanofer.networkboosters;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;

public final class NetworkBoostersBootstrap implements PluginBootstrap {

    private PaperCommandManager.Bootstrapped<CommandSourceStack> commandManager;

    @Override
    public void bootstrap(BootstrapContext context) {
        this.commandManager = PaperCommandManager.builder()
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildBootstrapped(context);
        this.registerInfrastructureCommand();
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        if (this.commandManager == null) {
            throw new IllegalStateException("NetworkBoosters command manager was not bootstrapped");
        }
        return new NetworkBoostersPlugin(this.commandManager);
    }

    private void registerInfrastructureCommand() {
        this.commandManager.command(this.commandManager.commandBuilder("networkboostersdiag")
            .handler(context -> {
                var sender = context.sender().getSender();
                if (!sender.hasPermission("networkboosters.admin.inspect")) {
                    sender.sendMessage(Component.text("You do not have permission to run this command.", NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text("NetworkBoosters infrastructure command is registered.", NamedTextColor.GREEN));
            })
        );
    }
}
