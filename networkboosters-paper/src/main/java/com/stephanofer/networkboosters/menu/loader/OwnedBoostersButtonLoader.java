package com.stephanofer.networkboosters.menu.loader;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import com.stephanofer.networkboosters.menu.button.OwnedBoostersButton;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OwnedBoostersButtonLoader extends ButtonLoader {

    private final NetworkBoostersMenuCoordinator coordinator;

    public OwnedBoostersButtonLoader(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        super(plugin, "NETWORKBOOSTERS_OWNED_BOOSTERS");
        this.coordinator = coordinator;
    }

    @Override
    public @Nullable Button load(@NotNull YamlConfiguration configuration, @NotNull String path, @NotNull DefaultButtonValue defaultButtonValue) {
        return new OwnedBoostersButton(this.coordinator, configuration.getInt(path + "empty-slot", -1));
    }
}
