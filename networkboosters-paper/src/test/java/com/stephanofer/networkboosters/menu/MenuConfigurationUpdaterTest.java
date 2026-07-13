package com.stephanofer.networkboosters.menu;

import dev.dejvokep.boostedyaml.YamlDocument;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MenuConfigurationUpdaterTest {

    @TempDir
    Path tempDir;

    @Test
    void upgradesLegacyMenuWithoutRemovingCustomOptions() throws Exception {
        Path inventories = Files.createDirectories(this.tempDir.resolve("inventories"));
        Path menu = inventories.resolve("boosters.yml");
        Files.writeString(menu, """
            name: Legacy
            size: 54
            custom-option: preserved
            items:
              owned-boosters:
                type: NETWORKBOOSTERS_OWNED_BOOSTERS
                item:
                  name:
                    default: "<aqua>%booster_id%"
                    locales:
                      es: "<aqua>%booster_id%"
                      en: "<aqua>%booster_id%"
                  lore:
                    default:
                      - "<gray>Amount: <white>%amount%</white>"
                      - "<gray>Duration: <white>%duration%</white>"
                      - "<yellow>Right click to transfer."
                    locales:
                      es:
                        - "<gray>Cantidad: <white>%amount%</white>"
                        - "<gray>Duración: <white>%duration%</white>"
                        - "<yellow>Click derecho para transferir."
                      en:
                        - "<gray>Amount: <white>%amount%</white>"
                        - "<gray>Duration: <white>%duration%</white>"
                        - "<yellow>Right click to transfer."
            """);

        MenuConfigurationUpdater.update(this.tempDir.toFile(), this::resource);

        YamlDocument updated = YamlDocument.create(menu.toFile());
        assertEquals("2", updated.getString("config-version"));
        assertEquals("preserved", updated.getString("custom-option"));
        assertTrue(updated.contains("items.status"));
        assertEquals("<aqua>%booster_name%", updated.getString("items.owned-boosters.item.name.default"));
        var lore = updated.getStringList("items.owned-boosters.item.lore.locales.es");
        assertTrue(lore.stream().anyMatch(line -> line.contains("%modalities%")));
        assertFalse(lore.stream().anyMatch(line -> line.contains("%amount%") || line.contains("derecho")));
    }

    private InputStream resource(String path) {
        return MenuConfigurationUpdaterTest.class.getClassLoader().getResourceAsStream(path);
    }
}
