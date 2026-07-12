package com.stephanofer.networkboosters.config;

import com.stephanofer.networkboosters.config.booster.BoosterDefinitionLoader;
import com.stephanofer.networkboosters.config.booster.BoosterDefinitionRegistry;
import com.stephanofer.networkboosters.config.booster.DefinitionChanges;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class ConfigurationLoader {

    private static final String CONFIG_RESOURCE = "config.yml";
    private static final String EXAMPLE_BOOSTER_RESOURCE = "boosters/personal_points_x2.yml";

    private final File dataFolder;
    private final Function<String, InputStream> resources;

    public ConfigurationLoader(File dataFolder, Function<String, InputStream> resources) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public ConfigurationSnapshot load() {
        return this.load((ConfigurationSnapshot) null);
    }

    public ConfigurationSnapshot load(ConfigurationSnapshot previous) {
        BoosterDefinitionRegistry previousDefinitions = previous == null
            ? BoosterDefinitionRegistry.empty()
            : previous.definitions();
        List<ConfigurationIssue> issues = new ArrayList<>();
        prepareResources(issues);

        NetworkBoostersConfiguration configuration = loadGlobalConfiguration(issues);
        BoosterDefinitionRegistry definitions = configuration == null
            ? BoosterDefinitionRegistry.empty()
            : new BoosterDefinitionLoader(new File(this.dataFolder, "boosters"), configuration).load(issues);

        List<ConfigurationIssue> errors = issues.stream()
            .filter(issue -> issue.severity() == ConfigurationIssue.Severity.ERROR)
            .toList();
        if (!errors.isEmpty()) {
            throw new ConfigurationException(issues);
        }

        DefinitionChanges changes = DefinitionChanges.between(previousDefinitions, definitions);
        for (var boosterId : changes.removed()) {
            issues.add(ConfigurationIssue.warning(
                "boosters",
                "removed." + boosterId.value(),
                "Definition was removed; persisted inventory, active boosters, and queued boosters keep their snapshots"
            ));
        }
        ConfigurationChanges configurationChanges = ConfigurationChanges.between(
            previous == null ? null : previous.configuration(),
            configuration
        );
        List<ConfigurationIssue> warnings = issues.stream()
            .filter(issue -> issue.severity() == ConfigurationIssue.Severity.WARNING)
            .toList();
        return new ConfigurationSnapshot(0, configuration, definitions, changes, configurationChanges, warnings);
    }

    private NetworkBoostersConfiguration loadGlobalConfiguration(List<ConfigurationIssue> issues) {
        File configFile = new File(this.dataFolder, CONFIG_RESOURCE);
        try (InputStream defaults = this.requiredResource(CONFIG_RESOURCE)) {
            YamlDocument document = YamlDocument.create(
                configFile,
                defaults,
                LoaderSettings.builder()
                    .setAutoUpdate(true)
                    .setAllowDuplicateKeys(false)
                    .setErrorLabel("NetworkBoosters config.yml")
                    .build(),
                UpdaterSettings.builder()
                    .setVersioning(new BasicVersioning("config-version"))
                    .build()
            );
            return NetworkBoostersConfiguration.parse(document, issues);
        } catch (IOException | RuntimeException exception) {
            issues.add(ConfigurationIssue.error(CONFIG_RESOURCE, "$", "Failed to load config.yml: " + exception.getMessage()));
            return null;
        }
    }

    private void prepareResources(List<ConfigurationIssue> issues) {
        try {
            Files.createDirectories(this.dataFolder.toPath());
            File boostersDirectory = new File(this.dataFolder, "boosters");
            boolean boostersDirectoryAlreadyExisted = boostersDirectory.exists();
            Files.createDirectories(boostersDirectory.toPath());
            if (!boostersDirectoryAlreadyExisted) {
                copyIfMissing(new File(boostersDirectory, "personal_points_x2.yml"), EXAMPLE_BOOSTER_RESOURCE);
            }
        } catch (IOException exception) {
            issues.add(ConfigurationIssue.error(".", "$", "Failed to prepare configuration files: " + exception.getMessage()));
        }
    }

    private void copyIfMissing(File target, String resourcePath) throws IOException {
        if (target.isFile()) {
            return;
        }
        try (InputStream input = this.requiredResource(resourcePath)) {
            Files.copy(input, target.toPath());
        }
    }

    private InputStream requiredResource(String path) throws IOException {
        InputStream input = this.resources.apply(path);
        if (input == null) {
            throw new IOException("Missing embedded resource: " + path);
        }
        return input;
    }
}
