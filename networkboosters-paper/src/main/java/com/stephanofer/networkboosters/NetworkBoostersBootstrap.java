package com.stephanofer.networkboosters;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import com.stephanofer.networkboosters.command.NetworkBoostersCommandBridge;
import com.stephanofer.networkboosters.command.NetworkBoostersCommands;
import com.stephanofer.networkboosters.config.ConfigurationLoader;
import com.stephanofer.networkboosters.localization.MessageKey;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;

public final class NetworkBoostersBootstrap implements PluginBootstrap {

    private PaperCommandManager.Bootstrapped<CommandSourceStack> commandManager;
    private final NetworkBoostersCommandBridge commandBridge = new NetworkBoostersCommandBridge();

    @Override
    public void bootstrap(BootstrapContext context) {
        this.commandManager = PaperCommandManager.builder()
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildBootstrapped(context);
        this.registerLocalizedCommandErrors();
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        if (this.commandManager == null) {
            throw new IllegalStateException("NetworkBoosters command manager was not bootstrapped");
        }
        ConfigurationLoader configurationLoader = new ConfigurationLoader(
            context.getDataDirectory().toFile(),
            path -> NetworkBoostersBootstrap.class.getClassLoader().getResourceAsStream(path)
        );
        var commands = configurationLoader.load().configuration().commands();
        new NetworkBoostersCommands(this.commandBridge, commands).register(this.commandManager);
        return new NetworkBoostersPlugin(this.commandManager, this.commandBridge);
    }

    private void registerLocalizedCommandErrors() {
        this.commandManager.exceptionController().registerHandler(NoPermissionException.class, context ->
            this.commandBridge.runtime().ifPresent(runtime -> context.context().sender().getSender().sendMessage(
                runtime.localization().message(context.context().sender().getSender(), MessageKey.COMMON_NO_PERMISSION)
            ))
        );
        this.commandManager.exceptionController().registerHandler(InvalidSyntaxException.class, context ->
            this.commandBridge.runtime().ifPresent(runtime -> {
                String root = runtime.configurationStore().requireCurrent().configuration().commands().root();
                context.context().sender().getSender().sendMessage(runtime.localization().message(
                    context.context().sender().getSender(),
                    MessageKey.COMMON_INVALID_SYNTAX,
                    com.stephanofer.networkboosters.localization.MessageArguments.text("command", root)
                ));
            })
        );
    }
}
