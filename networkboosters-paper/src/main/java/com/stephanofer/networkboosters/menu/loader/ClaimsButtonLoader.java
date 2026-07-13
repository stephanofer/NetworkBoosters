package com.stephanofer.networkboosters.menu.loader;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import com.stephanofer.networkboosters.menu.button.ClaimsButton;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClaimsButtonLoader extends ButtonLoader {

    private final NetworkBoostersMenuCoordinator coordinator;
    public ClaimsButtonLoader(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        super(plugin, "NETWORKBOOSTERS_CLAIMS");
        this.coordinator = coordinator;
    }

    @Override
    public @Nullable Button load(@NotNull YamlConfiguration configuration, @NotNull String path, @NotNull DefaultButtonValue defaultButtonValue) {
        return new ClaimsButton(this.coordinator, configuration.getInt(path + "empty-slot", -1));
    }
}
