package com.stephanofer.networkboosters.menu;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        }
    }
}
