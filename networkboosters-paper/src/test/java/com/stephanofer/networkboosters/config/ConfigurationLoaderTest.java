package com.stephanofer.networkboosters.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.config.booster.BoosterDefinitionRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationLoaderTest {

    @TempDir
    Path tempDir;
    private String configContent = NetworkBoostersConfigurationTest.validConfig();

    @Test
    void loadsValidFilesystemConfigurationAndCopiesExampleBooster() {
        ConfigurationSnapshot snapshot = loader().load();

        assertEquals(1, snapshot.definitions().size());
        assertTrue(snapshot.definitions().find(BoosterId.of("personal_points_x2")).isPresent());
        assertTrue(Files.isRegularFile(this.tempDir.resolve("boosters/personal_points_x2.yml")));
        assertEquals(1, snapshot.definitionChanges().added().size());
        assertTrue(snapshot.warnings().isEmpty());
    }

    @Test
    void rejectsDuplicateBoosterIdsWithFileAndPath() throws IOException {
        Files.createDirectories(this.tempDir.resolve("boosters"));
        Files.writeString(this.tempDir.resolve("boosters/first.yml"), validBooster("personal_points_x2", "2.0"));
        Files.writeString(this.tempDir.resolve("boosters/second.yml"), validBooster("PERSONAL_POINTS_X2", "3.0"));

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> loader().load());

        assertTrue(exception.issues().stream().anyMatch(issue ->
            issue.file().equals("boosters/second.yml") && issue.path().equals("id")));
    }

    @Test
    void invalidCandidateDoesNotReplaceCurrentSnapshot() throws IOException {
        ConfigurationStore store = new ConfigurationStore();
        ConfigurationSnapshot initial = store.initialize(loader().load());

        Files.writeString(this.tempDir.resolve("boosters/bad.yml"), """
            config-version: 1
            id: bad_booster
            enabled: true
            target: invalid
            multiplier: 99.0
            duration: 1.5h
            """);

        assertThrows(ConfigurationException.class, () -> loader().load(initial));
        assertEquals(initial, store.requireCurrent());
    }

    @Test
    void detectsDefinitionChangesAgainstPreviousRegistry() throws IOException {
        ConfigurationSnapshot initial = loader().load();
        Files.writeString(this.tempDir.resolve("boosters/personal_points_x2.yml"), validBooster("personal_points_x2", "3.0"));
        Files.writeString(this.tempDir.resolve("boosters/new_points.yml"), validBooster("new_points", "2.0"));

        ConfigurationSnapshot changed = loader().load(initial);

        assertTrue(changed.definitionChanges().modified().contains(BoosterId.of("personal_points_x2")));
        assertTrue(changed.definitionChanges().added().contains(BoosterId.of("new_points")));
        assertFalse(changed.definitionChanges().removed().contains(BoosterId.of("personal_points_x2")));
    }

    @Test
    void detectsRemovedAndDisabledDefinitions() throws IOException {
        ConfigurationSnapshot initial = loader().load();
        Files.delete(this.tempDir.resolve("boosters/personal_points_x2.yml"));
        Files.writeString(this.tempDir.resolve("boosters/new_points.yml"), validBooster("new_points", "2.0").replace("enabled: true", "enabled: false"));

        ConfigurationSnapshot changed = loader().load(initial);

        assertTrue(changed.definitionChanges().removed().contains(BoosterId.of("personal_points_x2")));
        assertTrue(changed.definitionChanges().added().contains(BoosterId.of("new_points")));
        assertFalse(changed.definitions().find(BoosterId.of("new_points")).orElseThrow().enabled());
    }

    @Test
    void warnsForIgnoredFilesWithoutFailingValidDefinitions() throws IOException {
        ConfigurationSnapshot initial = loader().load();
        Files.writeString(this.tempDir.resolve("boosters/readme.txt"), "ignored");

        ConfigurationSnapshot loaded = loader().load(initial);

        assertEquals(1, loaded.definitions().size());
        assertTrue(loaded.warnings().stream().anyMatch(issue -> issue.file().equals("boosters/readme.txt")));
    }

    @Test
    void storePublishesGenerationsAtomically() {
        ConfigurationStore store = new ConfigurationStore();
        ConfigurationSnapshot first = store.initialize(loader().load());
        ConfigurationSnapshot second = store.replace(loader().load(first));

        assertEquals(1, first.generation());
        assertEquals(2, second.generation());
        assertEquals(second, store.requireCurrent());
        assertThrows(IllegalStateException.class, () -> store.initialize(loader().load()));
    }

    @Test
    void allowsAnEmptyRegistryAfterEveryDefinitionIsRemoved() throws IOException {
        ConfigurationSnapshot initial = loader().load();
        Files.delete(this.tempDir.resolve("boosters/personal_points_x2.yml"));

        ConfigurationSnapshot changed = loader().load(initial);

        assertEquals(0, changed.definitions().size());
        assertTrue(changed.definitionChanges().removed().contains(BoosterId.of("personal_points_x2")));
        assertTrue(changed.warnings().stream().anyMatch(issue ->
            issue.path().equals("removed.personal_points_x2") && issue.file().equals("boosters")));
    }

    @Test
    void rejectsStaticConfigurationChangesBeforePublishing() throws IOException {
        ConfigurationStore store = new ConfigurationStore();
        ConfigurationSnapshot initial = store.initialize(loader().load());
        Files.writeString(this.tempDir.resolve("config.yml"), this.configContent.replace("id: test-01", "id: test-02"));
        ConfigurationSnapshot candidate = loader().load(initial);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> store.replace(candidate));

        assertTrue(exception.getMessage().contains("server.id"));
        assertEquals(initial, store.requireCurrent());
    }

    @Test
    void publishesReloadableConfigurationChanges() throws IOException {
        ConfigurationStore store = new ConfigurationStore();
        ConfigurationSnapshot initial = store.initialize(loader().load());
        Files.writeString(this.tempDir.resolve("config.yml"), this.configContent.replace("maximum-multiplier: 10.0", "maximum-multiplier: 8.0"));
        ConfigurationSnapshot candidate = loader().load(initial);

        ConfigurationSnapshot published = store.replace(candidate);

        assertEquals(2, published.generation());
        assertFalse(published.configurationChanges().requiresRestart());
        assertEquals(new java.math.BigDecimal("8").stripTrailingZeros(), published.configuration().limits().maximumMultiplier());
    }

    @Test
    void reportsExactScopePathAndRejectsMultiplierThatCannotBePersisted() throws IOException {
        ConfigurationSnapshot initial = loader().load();
        Files.writeString(this.tempDir.resolve("boosters/bad.yml"), validBooster("bad", "1.1234567")
            .replace("- \"*\"", "- \"*\"\n    - skywars"));

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> loader().load(initial));

        assertTrue(exception.issues().stream().anyMatch(issue -> issue.path().equals("scope.games[0]")));
        assertTrue(exception.issues().stream().anyMatch(issue ->
            issue.path().equals("multiplier") && issue.message().contains("6 decimal places")));
    }

    @Test
    void bootstrapCommandsUseDefaultsWithoutPreparingFullResources() {
        NetworkBoostersConfiguration.Commands commands = loader().loadBootstrapCommands();

        assertEquals("boosters", commands.root());
        assertEquals(List.of("booster", "boosts"), commands.aliases());
        assertTrue(Files.isRegularFile(this.tempDir.resolve("config.yml")));
        assertFalse(Files.exists(this.tempDir.resolve("boosters")));
        assertFalse(Files.exists(this.tempDir.resolve("messages")));
    }

    @Test
    void bootstrapCommandsUseExistingConfig() throws IOException {
        Files.createDirectories(this.tempDir);
        Files.writeString(this.tempDir.resolve("config.yml"), this.configContent
            .replace("root: boosters", "root: nb")
            .replace("- booster", "- networkbooster")
            .replace("- boosts", "- nboosts"));

        NetworkBoostersConfiguration.Commands commands = loader().loadBootstrapCommands();

        assertEquals("nb", commands.root());
        assertEquals(List.of("networkbooster", "nboosts"), commands.aliases());
    }

    @Test
    void bootstrapCommandsRejectInvalidRoot() throws IOException {
        Files.createDirectories(this.tempDir);
        Files.writeString(this.tempDir.resolve("config.yml"), this.configContent.replace("root: boosters", "root: invalid/root"));

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> loader().loadBootstrapCommands());

        assertTrue(exception.issues().stream().anyMatch(issue -> issue.path().equals("commands.root")));
    }

    @Test
    void bootstrapCommandsRejectInvalidAliases() throws IOException {
        Files.createDirectories(this.tempDir);
        Files.writeString(this.tempDir.resolve("config.yml"), this.configContent.replace("- booster", "- boosters"));

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> loader().loadBootstrapCommands());

        assertTrue(exception.issues().stream().anyMatch(issue -> issue.path().equals("commands.aliases[0]")));
    }

    private ConfigurationLoader loader() {
        return new ConfigurationLoader(this.tempDir.toFile(), this::resource);
    }

    private InputStream resource(String path) {
        if (path.equals("messages/es.yml") || path.equals("messages/en.yml")) {
            try (InputStream embedded = ConfigurationLoaderTest.class.getClassLoader().getResourceAsStream(path)) {
                if (embedded == null) {
                    return null;
                }
                String content = new String(embedded.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("  personal_points_x2:", "  new_points:\n    name: \"<aqua>New Points\"\n    description:\n      - \"<gray>Extra test booster.\"\n  bad:\n    name: \"<aqua>Bad\"\n    description:\n      - \"<gray>Invalid test booster.\"\n  personal_points_x2:");
                return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            } catch (IOException exception) {
                return null;
            }
        }
        String content = switch (path) {
            case "config.yml" -> this.configContent;
            case "boosters/personal_points_x2.yml" -> validBooster("personal_points_x2", "2.0");
            default -> null;
        };
        return content == null ? null : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String validBooster(String id, String multiplier) {
        return """
            config-version: 1
            id: %s
            enabled: true
            target: network_progression:points
            multiplier: %s
            duration: 2h
            scope:
              type: PERSONAL
              games:
                - "*"
              servers:
                - "*"
            activation:
              group: personal-points
              conflict-policy: QUEUE
              requirements:
                permissions: []
                mode: ALL
            transfer:
              enabled: true
              minimum-amount: 1
              maximum-amount: 5
              cooldown: 30s
              permission: ""
            display:
              order: 100
              category: points
            """.formatted(id, multiplier);
    }
}
