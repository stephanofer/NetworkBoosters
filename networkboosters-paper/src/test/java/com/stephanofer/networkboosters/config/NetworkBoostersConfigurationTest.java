package com.stephanofer.networkboosters.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NetworkBoostersConfigurationTest {

    @Test
    void loadsValidConfiguration() throws IOException {
        NetworkBoostersConfiguration config = NetworkBoostersConfiguration.load(document(validConfig()));

        assertEquals("test-01", config.serverId());
        assertEquals("skywars", config.gameId());
        assertEquals(8, config.storage().maximumPoolSize());
        assertEquals(Duration.ofSeconds(3), config.storage().shutdownTimeout());
        assertEquals("test", config.redis().environment());
        assertEquals(Duration.ofSeconds(2), config.redis().connectTimeout());
        assertEquals(Duration.ofSeconds(30), config.redis().reconciliationInterval());
        assertEquals(new BigDecimal("10").stripTrailingZeros(), config.limits().maximumMultiplier());
        assertEquals(Duration.ofDays(7), config.activation().maximumTotalDuration());
        assertEquals(20, config.activation().maximumQueuedEntries());
        assertEquals(Duration.ofSeconds(1), config.activation().expiryCheckInterval());
        assertEquals(100, config.activation().expiryBatchSize());
        assertEquals(Duration.ofMinutes(5), config.activation().expiryWarnings().getFirst());
        assertEquals(30, config.inventoryLimits().fallback());
        assertEquals(2, config.inventoryLimits().tiers().size());
        assertEquals("SkyWars", config.scopeDisplay().game("skywars").orElseThrow());
        assertEquals("boosters", config.commands().root());
        assertTrue(config.placeholderApi().enabled());
    }

    @Test
    void rejectsUnsupportedVersionAndKeepsAllIssues() throws IOException {
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> NetworkBoostersConfiguration.load(document("""
            config-version: 4
            server:
              id: test-01
              game-id: skywars
            storage:
              host: 127.0.0.1
              port: 3306
              database: hera_network
              username: networkboosters
              pool:
                maximum-size: 0
                minimum-idle: 2
            redis:
              host: 127.0.0.1
              key-prefix: hera
              environment: test
            limits:
              maximum-multiplier: 0
            activation:
              maximum-total-duration: 7d
              maximum-queued-entries: -1
              expiry-batch-size: 0
              expiry-warnings:
                - 7d
            inventory-limits:
              fallback: -1
            localization:
              fallback-language: en
              console-language: en
            commands:
              root: boosters
              aliases:
                - boosters
            placeholderapi:
              enabled: true
            """)));

        assertTrue(exception.issues().stream().anyMatch(issue -> issue.path().equals("config-version")));
        assertTrue(exception.issues().stream().anyMatch(issue -> issue.path().equals("storage.pool.maximum-size")));
        assertTrue(exception.issues().stream().anyMatch(issue -> issue.path().equals("limits.maximum-multiplier")));
        assertTrue(exception.issues().stream().anyMatch(issue -> issue.path().equals("activation.expiry-batch-size")));
        assertTrue(exception.issues().stream().anyMatch(issue -> issue.path().equals("commands.aliases[0]")));
    }

    @Test
    void supportsHourAndDayDurationsButRejectsAmbiguousValues() throws IOException {
        NetworkBoostersConfiguration config = NetworkBoostersConfiguration.load(document(validConfig()
            .replace("connection-timeout: 10s", "connection-timeout: 1m")
            .replace("maximum-total-duration: 7d", "maximum-total-duration: 24h")));

        assertEquals(Duration.ofMinutes(1), config.storage().connectionTimeout());
        assertEquals(Duration.ofHours(24), config.activation().maximumTotalDuration());

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> NetworkBoostersConfiguration.load(document(validConfig()
            .replace("maximum-total-duration: 7d", "maximum-total-duration: 1.5h"))));

        assertTrue(exception.issues().stream().anyMatch(issue -> issue.path().equals("activation.maximum-total-duration")));
    }

    @Test
    void exposesImmutableCollections() throws IOException {
        NetworkBoostersConfiguration config = NetworkBoostersConfiguration.load(document(validConfig()));

        assertThrows(UnsupportedOperationException.class, () -> config.commands().aliases().add("other"));
        assertThrows(UnsupportedOperationException.class, () -> config.activation().expiryWarnings().add(Duration.ofSeconds(1)));
        assertThrows(UnsupportedOperationException.class, () -> config.inventoryLimits().tiers().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.scopeDisplay().games().clear());
    }

    @Test
    void upgradesVersionOneConfigurationWithScopeDisplay(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, validConfig()
            .replace("config-version: 3", "config-version: 1")
            .replace("scope-display:\n  games:\n    skywars: SkyWars\n", ""));
        try (var defaults = NetworkBoostersConfigurationTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            YamlDocument updated = YamlDocument.create(
                file.toFile(),
                defaults,
                LoaderSettings.builder().setAutoUpdate(true).setAllowDuplicateKeys(false).build(),
                UpdaterSettings.builder().setVersioning(new BasicVersioning("config-version")).build()
            );

            assertTrue(updated.isInt("config-version"));
            assertEquals(3, updated.getInt("config-version"));
            assertEquals("Development", updated.getString("scope-display.games.development"));
        }
    }

    @Test
    void repairsStringVersionTwoConfiguration(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, validConfig().replace("config-version: 3", "config-version: \"2\""));
        try (var defaults = NetworkBoostersConfigurationTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            YamlDocument updated = YamlDocument.create(
                file.toFile(),
                defaults,
                LoaderSettings.builder().setAutoUpdate(true).setAllowDuplicateKeys(false).build(),
                UpdaterSettings.builder().setVersioning(new BasicVersioning("config-version")).build()
            );

            assertTrue(updated.isInt("config-version"));
            assertEquals(3, updated.getInt("config-version"));
            assertTrue(NetworkBoostersConfiguration.load(updated).placeholderApi().enabled());
        }
    }

    static String validConfig() {
        return """
            config-version: 3
            server:
              id: test-01
              game-id: skywars
            storage:
              host: 127.0.0.1
              port: 3306
              database: hera_network
              username: networkboosters
              password: secret
              table-prefix: network_booster_
              pool:
                maximum-size: 8
                minimum-idle: 2
                connection-timeout: 10s
                validation-timeout: 5s
                shutdown-timeout: 3s
            redis:
              enabled: true
              host: 127.0.0.1
              port: 6379
              database: 0
              username: ""
              password: secret
              ssl: false
              verify-peer: true
              key-prefix: hera
              environment: test
              command-timeout: 3s
              connect-timeout: 2s
              shutdown-timeout: 1s
              auto-reconnect: true
              reconciliation-interval: 30s
              degraded-reconciliation-interval: 5s
            limits:
              maximum-multiplier: 10.0
            activation:
              maximum-total-duration: 7d
              maximum-queued-entries: 20
              expiry-check-interval: 1s
              expiry-batch-size: 100
              expiry-warnings:
                - 5m
                - 1m
                - 10s
            inventory-limits:
              fallback: 30
              tiers:
                phantom:
                  permission: networkboosters.capacity.phantom
                  maximum: 60
                  priority: 100
                legend:
                  permission: networkboosters.capacity.legend
                  maximum: 100
                  priority: 200
            scope-display:
              games:
                skywars: SkyWars
            localization:
              fallback-language: en
              console-language: en
            commands:
              root: boosters
              aliases:
                - booster
                - boosts
            placeholderapi:
              enabled: true
            """;
    }

    static YamlDocument document(String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return YamlDocument.create(
            new ByteArrayInputStream(bytes),
            new ByteArrayInputStream(bytes),
            LoaderSettings.builder()
                .setAutoUpdate(true)
                .setAllowDuplicateKeys(false)
                .build(),
            UpdaterSettings.builder()
                .setVersioning(new BasicVersioning("config-version"))
                .build()
        );
    }
}
