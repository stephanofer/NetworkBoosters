package com.stephanofer.networkboosters.menu;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class MenuConfigurationUpdater {

    private static final String BOOSTERS_MENU = "inventories/boosters.yml";

    private MenuConfigurationUpdater() {
    }

    public static void update(File dataFolder, Function<String, InputStream> resources) throws IOException {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(resources, "resources");
        File file = new File(dataFolder, BOOSTERS_MENU);
        try (InputStream defaults = resources.apply(BOOSTERS_MENU)) {
            if (defaults == null) {
                throw new IOException("Missing embedded resource: " + BOOSTERS_MENU);
            }
            YamlDocument.create(
                file,
                defaults,
                LoaderSettings.builder()
                    .setAutoUpdate(true)
                    .setAllowDuplicateKeys(false)
                    .setErrorLabel("NetworkBoosters " + BOOSTERS_MENU)
                    .build(),
                UpdaterSettings.builder()
                    .setKeepAll(true)
                    .setVersioning(new BasicVersioning("config-version"))
                    .addCustomLogic("2", MenuConfigurationUpdater::migrateVersionTwo)
                    .build()
            );
        }
    }

    private static void migrateVersionTwo(YamlDocument document) {
        replaceLegacyName(document, "items.owned-boosters.item.name.default");
        replaceLegacyName(document, "items.owned-boosters.item.name.locales.es");
        replaceLegacyName(document, "items.owned-boosters.item.name.locales.en");
        migrateLore(document, "items.owned-boosters.item.lore.default", "<gray>Modalities: <white>%modalities%</white>");
        migrateLore(document, "items.owned-boosters.item.lore.locales.es", "<gray>Modalidades: <white>%modalities%</white>");
        migrateLore(document, "items.owned-boosters.item.lore.locales.en", "<gray>Modalities: <white>%modalities%</white>");
    }

    private static void replaceLegacyName(YamlDocument document, String path) {
        String value = document.getString(path, "");
        if (value.contains("%booster_id%")) {
            document.set(path, value.replace("%booster_id%", "%booster_name%"));
        }
    }

    private static void migrateLore(YamlDocument document, String path, String modalityLine) {
        List<?> existing = document.getList(path);
        if (existing == null) {
            return;
        }
        List<String> migrated = new ArrayList<>();
        boolean hasModalities = false;
        for (Object element : existing) {
            if (!(element instanceof String line)) {
                continue;
            }
            if (line.contains("%amount%") || line.toLowerCase(java.util.Locale.ROOT).contains("right click")
                || line.toLowerCase(java.util.Locale.ROOT).contains("click derecho")) {
                continue;
            }
            migrated.add(line);
            if (line.contains("%modalities%")) {
                hasModalities = true;
            }
            if (!hasModalities && line.contains("%duration%")) {
                migrated.add(modalityLine);
                hasModalities = true;
            }
        }
        document.set(path, migrated);
    }
}
