package com.stephanofer.networkboosters.localization;

import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.config.ConfigurationIssue;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class LocalizationLoader {

    private final File dataFolder;
    private final Function<String, InputStream> resources;

    public LocalizationLoader(File dataFolder, Function<String, InputStream> resources) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public LocalizationSnapshot load(
        String fallbackLanguage,
        String consoleLanguage,
        Collection<BoosterDefinition> definitions,
        List<ConfigurationIssue> issues
    ) {
        Map<String, MessageCatalog> catalogs = new LinkedHashMap<>();
        catalogs.put("es", this.loadCatalog("es", definitions, issues));
        catalogs.put("en", this.loadCatalog("en", definitions, issues));
        return new LocalizationSnapshot(fallbackLanguage, consoleLanguage, catalogs);
    }

    private MessageCatalog loadCatalog(String language, Collection<BoosterDefinition> definitions, List<ConfigurationIssue> issues) {
        String resourcePath = "messages/" + language + ".yml";
        File file = new File(this.dataFolder, resourcePath);
        try (InputStream defaults = this.requiredResource(resourcePath)) {
            YamlDocument document = YamlDocument.create(
                file,
                defaults,
                LoaderSettings.builder()
                    .setAutoUpdate(true)
                    .setAllowDuplicateKeys(false)
                    .setErrorLabel("NetworkBoosters " + resourcePath)
                    .build(),
                UpdaterSettings.builder()
                    .setVersioning(new BasicVersioning("config-version"))
                    .build()
            );
            Map<String, List<String>> messages = new LinkedHashMap<>();
            for (MessageKey key : MessageKey.values()) {
                List<String> lines = readLines(document, key.path(), resourcePath, issues);
                if (!lines.isEmpty()) {
                    messages.put(key.path(), lines);
                }
            }
            Map<String, MessageCatalog.BoosterTranslation> boosters = new LinkedHashMap<>();
            for (BoosterDefinition definition : definitions) {
                String id = definition.id().value();
                String base = "boosters." + id;
                List<String> name = readLines(document, base + ".name", resourcePath, issues);
                List<String> description = readLines(document, base + ".description", resourcePath, issues);
                if (!name.isEmpty()) {
                    boosters.put(id, new MessageCatalog.BoosterTranslation(name.getFirst(), description));
                }
            }
            return new MessageCatalog(language, messages, boosters);
        } catch (IOException | RuntimeException exception) {
            issues.add(ConfigurationIssue.error(resourcePath, "$", "Failed to load messages: " + exception.getMessage()));
            return new MessageCatalog(language, Map.of(), Map.of());
        }
    }

    private static List<String> readLines(YamlDocument document, String path, String file, List<ConfigurationIssue> issues) {
        if (!document.contains(path)) {
            issues.add(ConfigurationIssue.error(file, path, "Missing required message"));
            return List.of();
        }
        if (document.isString(path)) {
            String value = document.getString(path);
            if (value == null || value.isBlank()) {
                issues.add(ConfigurationIssue.error(file, path, "Message cannot be empty"));
                return List.of();
            }
            return List.of(value);
        }
        if (document.isList(path)) {
            List<?> raw = document.getList(path);
            List<String> values = new ArrayList<>();
            for (int index = 0; index < raw.size(); index++) {
                Object element = raw.get(index);
                if (!(element instanceof String value) || value.isBlank()) {
                    issues.add(ConfigurationIssue.error(file, path + "[" + index + "]", "Expected a non-empty string"));
                    continue;
                }
                values.add(value);
            }
            return List.copyOf(values);
        }
        Section ignored = document.getSection(path, null);
        issues.add(ConfigurationIssue.error(file, path, ignored == null ? "Expected a string or list" : "Expected a message, found section"));
        return List.of();
    }

    private InputStream requiredResource(String path) throws IOException {
        InputStream input = this.resources.apply(path);
        if (input == null) {
            throw new IOException("Missing embedded resource: " + path);
        }
        return input;
    }
}
