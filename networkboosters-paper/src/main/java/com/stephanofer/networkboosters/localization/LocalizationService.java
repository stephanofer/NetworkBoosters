package com.stephanofer.networkboosters.localization;

import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LocalizationService {

    private final ConfigurationStore configurationStore;
    private final PlayerSettingsService playerSettings;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LocalizationService(ConfigurationStore configurationStore, PlayerSettingsService playerSettings) {
        this.configurationStore = Objects.requireNonNull(configurationStore, "configurationStore");
        this.playerSettings = Objects.requireNonNull(playerSettings, "playerSettings");
    }

    public String language(CommandSender sender) {
        if (sender instanceof Player player) {
            return this.playerSettings.resolvedLanguage(player).code();
        }
        return this.configurationStore.requireCurrent().localization().consoleLanguage();
    }

    public Component message(CommandSender sender, MessageKey key, TagResolver... resolvers) {
        return this.message(this.language(sender), key, resolvers);
    }

    public Component message(String language, MessageKey key, TagResolver... resolvers) {
        String template = this.configurationStore.requireCurrent().localization().template(language, key).orElse(key.path());
        return this.miniMessage.deserialize(template, resolvers);
    }

    public List<Component> lines(CommandSender sender, MessageKey key, TagResolver... resolvers) {
        String language = this.language(sender);
        return this.configurationStore.requireCurrent().localization().lines(language, key).stream()
            .map(line -> this.miniMessage.deserialize(line, resolvers))
            .toList();
    }

    public Component boosterName(CommandSender sender, String boosterId) {
        String language = this.language(sender);
        String value = this.configurationStore.requireCurrent().localization().catalog(language).booster(boosterId)
            .map(MessageCatalog.BoosterTranslation::name)
            .orElse(boosterId);
        return this.miniMessage.deserialize(value);
    }
}
