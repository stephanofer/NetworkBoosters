package com.stephanofer.networkboosters.command;

import com.stephanofer.networkboosters.api.NetworkBoostersService;
import com.stephanofer.networkboosters.capacity.ResolvedInventoryCapacity;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.localization.LocalizationService;
import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public interface NetworkBoostersCommandRuntime {

    JavaPlugin plugin();

    Server server();

    NetworkBoostersService service();

    ConfigurationStore configurationStore();

    LocalizationService localization();

    PlayerSettingsService playerSettings();

    Clock clock();

    ResolvedInventoryCapacity capacity(Player player);

    CompletableFuture<Long> persistedRevision(UUID playerId);

    CompletableFuture<ReloadReport> reload();

    boolean openMenu(Player player);

    String redisStatus();

    boolean isRunning();

    record ReloadReport(boolean success, boolean restartRequired, String detail, int definitions, int warnings) {
    }
}
