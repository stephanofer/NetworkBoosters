package com.stephanofer.networkboosters.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NetworkBoostersConfigurationTest {

    @Test
    void loadsValidConfiguration() throws IOException {
        NetworkBoostersConfiguration config = NetworkBoostersConfiguration.load(document("""
                config-version: 1
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
                """
        ));

        assertEquals("test-01", config.serverId());
        assertEquals("skywars", config.gameId());
        assertEquals(8, config.storage().maximumPoolSize());
        assertEquals(Duration.ofSeconds(3), config.storage().shutdownTimeout());
        assertEquals("test", config.redis().environment());
        assertEquals(Duration.ofSeconds(2), config.redis().connectTimeout());
    }

    @Test
    void rejectsUnsupportedVersion() throws IOException {
        YamlDocument yaml = document("config-version: 2");

        assertThrows(IllegalArgumentException.class, () -> NetworkBoostersConfiguration.load(yaml));
    }

    @Test
    void rejectsInvalidDurations() throws IOException {
        YamlDocument yaml = document("""
            config-version: 1
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
                connection-timeout: 0s
                validation-timeout: 5s
                shutdown-timeout: 3s
            redis:
              host: 127.0.0.1
              port: 6379
              database: 0
              key-prefix: hera
              environment: test
            """);

        assertThrows(IllegalArgumentException.class, () -> NetworkBoostersConfiguration.load(yaml));
    }

    private static YamlDocument document(String content) throws IOException {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
