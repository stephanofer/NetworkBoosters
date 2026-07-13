package com.stephanofer.networkboosters.menu;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MenuResourcesTest {

    @Test
    void embeddedZMenuResourcesAreValidYaml() throws Exception {
        for (String path : List.of(
            "inventories/boosters.yml",
            "inventories/booster-status.yml",
            "inventories/booster-confirm.yml",
            "inventories/booster-transfer-target.yml",
            "inventories/booster-transfer.yml",
            "inventories/booster-claims.yml",
            "patterns/pagination.yml"
        )) {
            try (var stream = MenuResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                assertNotNull(configuration.get("name"), path);
            }
        }
        try (var stream = MenuResourcesTest.class.getClassLoader().getResourceAsStream("inventories/boosters.yml")) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals("NETWORKBOOSTERS_STATUS", configuration.getString("items.status.type"));
            assertEquals("NETWORKBOOSTERS_OWNED_BOOSTERS", configuration.getString("items.owned-boosters.type"));
            for (String item : List.of("summary", "status", "filter", "sort", "claims")) {
                assertTrue(configuration.getBoolean("items." + item + ".is-permanent"), item);
            }
            assertFalse(configuration.contains("update-interval"));
            assertFalse(configuration.getBoolean("items.status.update"));
        }
        try (var stream = MenuResourcesTest.class.getClassLoader().getResourceAsStream("inventories/booster-claims.yml")) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals("NETWORKBOOSTERS_CLAIMS", configuration.getString("items.claims.type"));
            assertEquals("<gold><bold>%booster_name% <gray>x%amount%", configuration.getString("items.claims.item.name.default"));
            var lore = configuration.getStringList("items.claims.item.lore.default");
            assertTrue(lore.stream().anyMatch(line -> line.contains("%multiplier%")));
            assertTrue(lore.stream().anyMatch(line -> line.contains("%duration%")));
            assertTrue(lore.stream().anyMatch(line -> line.contains("%modalities%")));
            assertFalse(lore.stream().anyMatch(line -> line.contains("%claim_id%")));
        }
        try (var stream = MenuResourcesTest.class.getClassLoader().getResourceAsStream("inventories/booster-status.yml")) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertFalse(configuration.contains("update-interval"));
            assertFalse(configuration.getBoolean("items.timeline.update"));
        }
    }
}
