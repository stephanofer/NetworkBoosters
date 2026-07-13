package com.stephanofer.networkboosters.config.booster;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import com.stephanofer.networkboosters.api.booster.BoosterCategory;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.booster.BoosterScopeType;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.booster.PermissionMode;
import com.stephanofer.networkboosters.api.booster.TransferPolicy;
import com.stephanofer.networkboosters.config.ConfigurationIssue;
import com.stephanofer.networkboosters.config.DurationParser;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

public final class BoosterDefinitionLoader {

    private static final Pattern SCOPE_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern MATERIAL_ID_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_]{0,63}");
    private static final int PERSISTED_MULTIPLIER_SCALE = 6;

    private final File boostersDirectory;
    private final NetworkBoostersConfiguration configuration;

    public BoosterDefinitionLoader(File boostersDirectory, NetworkBoostersConfiguration configuration) {
        this.boostersDirectory = Objects.requireNonNull(boostersDirectory, "boostersDirectory");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public BoosterDefinitionRegistry load(List<ConfigurationIssue> issues) {
        Objects.requireNonNull(issues, "issues");
        Map<BoosterId, BoosterDefinition> definitions = new LinkedHashMap<>();
        Map<BoosterId, BoosterDisplay> displays = new LinkedHashMap<>();
        Map<BoosterId, String> firstDeclaration = new LinkedHashMap<>();

        if (!this.boostersDirectory.isDirectory()) {
            issues.add(ConfigurationIssue.error("boosters", "$", "Boosters directory does not exist or is not a directory"));
            return BoosterDefinitionRegistry.empty();
        }

        File[] entries = this.boostersDirectory.listFiles();
        if (entries == null) {
            issues.add(ConfigurationIssue.error("boosters", "$", "Boosters directory cannot be read"));
            return BoosterDefinitionRegistry.empty();
        }

        List<File> sortedEntries = new ArrayList<>(List.of(entries));
        sortedEntries.sort(Comparator.comparing(File::getName));
        for (File entry : sortedEntries) {
            if (entry.isDirectory()) {
                issues.add(ConfigurationIssue.warning(relative(entry), "$", "Subdirectories are ignored by the booster definition loader"));
                continue;
            }
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".yml") && !name.endsWith(".yaml")) {
                issues.add(ConfigurationIssue.warning(relative(entry), "$", "Only .yml and .yaml booster definition files are loaded"));
                continue;
            }

            BoosterDefinition definition = this.loadDefinition(entry, issues);
            if (definition == null) {
                continue;
            }

            String previous = firstDeclaration.putIfAbsent(definition.id(), relative(entry));
            if (previous != null) {
                issues.add(ConfigurationIssue.error(relative(entry), "id", "Duplicate booster ID " + definition.id() + "; first declared in " + previous + ":id"));
                continue;
            }
            definitions.put(definition.id(), definition);
            displays.put(definition.id(), this.display(relative(entry), definition.id(), entry, issues));

            if (!BoosterTarget.NETWORK_PROGRESSION_POINTS.equals(definition.target())) {
                issues.add(ConfigurationIssue.warning(relative(entry), "target", "No built-in consumer is currently known for target " + definition.target()));
            }
        }

        return new BoosterDefinitionRegistry(definitions, displays);
    }

    private BoosterDisplay display(String source, BoosterId id, File file, List<ConfigurationIssue> issues) {
        try {
            YamlDocument document = YamlDocument.create(
                file,
                LoaderSettings.builder()
                    .setAutoUpdate(false)
                    .setAllowDuplicateKeys(false)
                    .setErrorLabel(source)
                    .build()
            );
            String material = optionalString(source, document, "display.material", "TRIAL_KEY", issues);
            String lockedMaterial = optionalString(source, document, "display.locked-material", "OMINOUS_TRIAL_KEY", issues);
            String activeMaterial = optionalString(source, document, "display.active-material", "HEAVY_CORE", issues);
            Integer customModelData = optionalInt(document, "display.custom-model-data", null, issues, source);
            Boolean glow = optionalBoolean(document, "display.glow", false, issues, source);
            validateMaterial(source, "display.material", material, issues);
            validateMaterial(source, "display.locked-material", lockedMaterial, issues);
            validateMaterial(source, "display.active-material", activeMaterial, issues);
            return new BoosterDisplay(
                material,
                lockedMaterial,
                activeMaterial,
                customModelData == null || customModelData <= 0 ? OptionalInt.empty() : OptionalInt.of(customModelData),
                Boolean.TRUE.equals(glow)
            );
        } catch (RuntimeException | IOException exception) {
            issues.add(ConfigurationIssue.error(source, "display", "Failed to load booster display: " + exception.getMessage()));
            return BoosterDisplay.defaults();
        }
    }

    private static void validateMaterial(String source, String route, String value, List<ConfigurationIssue> issues) {
        if (value == null || !MATERIAL_ID_PATTERN.matcher(value.trim().toUpperCase(Locale.ROOT)).matches()) {
            error(issues, source, route, "Invalid material ID: " + value);
        }
    }

    private BoosterDefinition loadDefinition(File file, List<ConfigurationIssue> issues) {
        String source = relative(file);
        try {
            YamlDocument document = YamlDocument.create(
                file,
                LoaderSettings.builder()
                    .setAutoUpdate(false)
                    .setAllowDuplicateKeys(false)
                    .setErrorLabel(source)
                    .build()
            );
            return parseDefinition(source, document, issues);
        } catch (IOException | RuntimeException exception) {
            issues.add(ConfigurationIssue.error(source, "$", "Failed to load booster definition: " + exception.getMessage()));
            return null;
        }
    }

    private BoosterDefinition parseDefinition(String source, YamlDocument document, List<ConfigurationIssue> issues) {
        Integer version = requiredInt(source, document, "config-version", issues);
        if (version != null && version != 1) {
            error(issues, source, "config-version", "Unsupported config-version: " + version);
        }

        BoosterId id = boosterId(source, document, "id", issues);
        Boolean enabled = requiredBoolean(source, document, "enabled", issues);
        BoosterTarget target = boosterTarget(source, document, "target", issues);
        BigDecimal multiplier = decimal(source, document, "multiplier", issues);
        Duration duration = positiveDuration(source, document, "duration", issues);
        BoosterScope scope = scope(source, document, issues);
        ActivationGroup activationGroup = activationGroup(source, document, "activation.group", issues);
        ConflictPolicy conflictPolicy = enumValue(source, document, "activation.conflict-policy", ConflictPolicy.class, issues);
        ActivationRequirements requirements = requirements(source, document.getSection("activation.requirements", null), issues);
        TransferPolicy transferPolicy = transferPolicy(source, document.getSection("transfer", null), issues);
        Integer displayOrder = requiredInt(source, document, "display.order", issues);
        BoosterCategory category = category(source, document, "display.category", issues);

        if (multiplier != null && multiplier.compareTo(this.configuration.limits().maximumMultiplier()) > 0) {
            error(issues, source, "multiplier", "Cannot be greater than limits.maximum-multiplier");
        }
        if (duration != null && duration.compareTo(this.configuration.activation().maximumTotalDuration()) > 0) {
            error(issues, source, "duration", "Cannot be greater than activation.maximum-total-duration");
        }

        if (id == null || enabled == null || target == null || multiplier == null || duration == null || scope == null
            || activationGroup == null || conflictPolicy == null || requirements == null || transferPolicy == null
            || displayOrder == null || category == null) {
            return null;
        }

        try {
            return new BoosterDefinition(
                id,
                target,
                multiplier,
                duration,
                scope,
                activationGroup,
                conflictPolicy,
                requirements,
                transferPolicy,
                enabled,
                displayOrder,
                category
            );
        } catch (IllegalArgumentException exception) {
            error(issues, source, "$", exception.getMessage());
            return null;
        }
    }

    private BoosterScope scope(String source, YamlDocument document, List<ConfigurationIssue> issues) {
        Section scope = document.getSection("scope", null);
        if (scope == null) {
            error(issues, source, "scope", "Missing required section");
            return null;
        }
        BoosterScopeType type = enumValue(source, scope, "type", "scope.type", BoosterScopeType.class, issues);
        Set<String> games = scopeValues(source, scope, "games", "scope.games", issues);
        Set<String> servers = scopeValues(source, scope, "servers", "scope.servers", issues);
        if (type == null || games == null || servers == null) {
            return null;
        }
        try {
            return new BoosterScope(type, games, servers);
        } catch (IllegalArgumentException exception) {
            error(issues, source, "scope", exception.getMessage());
            return null;
        }
    }

    private ActivationRequirements requirements(String source, Section section, List<ConfigurationIssue> issues) {
        if (section == null) {
            return ActivationRequirements.NONE;
        }
        Set<String> permissions = stringSet(source, section, "permissions", "activation.requirements.permissions", issues);
        PermissionMode mode = enumValue(source, section, "mode", "activation.requirements.mode", PermissionMode.class, issues);
        if (permissions == null || mode == null) {
            return null;
        }
        if (permissions.isEmpty() && mode == PermissionMode.ANY) {
            error(issues, source, "activation.requirements.mode", "ANY requires at least one permission");
            return null;
        }
        try {
            return permissions.isEmpty() ? ActivationRequirements.NONE : new ActivationRequirements(permissions, mode);
        } catch (IllegalArgumentException exception) {
            error(issues, source, "activation.requirements", exception.getMessage());
            return null;
        }
    }

    private TransferPolicy transferPolicy(String source, Section section, List<ConfigurationIssue> issues) {
        if (section == null) {
            error(issues, source, "transfer", "Missing required section");
            return null;
        }
        Boolean enabled = requiredBoolean(source, section, "enabled", "transfer.enabled", issues);
        Long minimumAmount = requiredLong(source, section, "minimum-amount", "transfer.minimum-amount", issues);
        Long maximumAmount = requiredLong(source, section, "maximum-amount", "transfer.maximum-amount", issues);
        Duration cooldown = nonNegativeDuration(source, section, "cooldown", "transfer.cooldown", issues);
        String permission = optionalString(source, section, "permission", "transfer.permission", "", issues);
        if (minimumAmount != null && minimumAmount <= 0) {
            error(issues, source, "transfer.minimum-amount", "Must be positive");
        }
        if (maximumAmount != null && minimumAmount != null && maximumAmount < minimumAmount) {
            error(issues, source, "transfer.maximum-amount", "Cannot be less than transfer.minimum-amount");
        }
        if (enabled == null || minimumAmount == null || maximumAmount == null || cooldown == null || permission == null) {
            return null;
        }
        try {
            return new TransferPolicy(enabled, minimumAmount, maximumAmount, cooldown, Optional.of(permission));
        } catch (IllegalArgumentException exception) {
            error(issues, source, "transfer", exception.getMessage());
            return null;
        }
    }

    private BoosterId boosterId(String source, YamlDocument document, String route, List<ConfigurationIssue> issues) {
        String value = requiredString(source, document, route, issues);
        if (value == null) {
            return null;
        }
        try {
            return BoosterId.of(value);
        } catch (IllegalArgumentException exception) {
            error(issues, source, route, exception.getMessage());
            return null;
        }
    }

    private BoosterTarget boosterTarget(String source, YamlDocument document, String route, List<ConfigurationIssue> issues) {
        String value = requiredString(source, document, route, issues);
        if (value == null) {
            return null;
        }
        try {
            return BoosterTarget.of(value);
        } catch (IllegalArgumentException exception) {
            error(issues, source, route, exception.getMessage());
            return null;
        }
    }

    private ActivationGroup activationGroup(String source, YamlDocument document, String route, List<ConfigurationIssue> issues) {
        String value = requiredString(source, document, route, issues);
        if (value == null) {
            return null;
        }
        try {
            return ActivationGroup.of(value);
        } catch (IllegalArgumentException exception) {
            error(issues, source, route, exception.getMessage());
            return null;
        }
    }

    private BoosterCategory category(String source, YamlDocument document, String route, List<ConfigurationIssue> issues) {
        String value = requiredString(source, document, route, issues);
        if (value == null) {
            return null;
        }
        try {
            return BoosterCategory.of(value);
        } catch (IllegalArgumentException exception) {
            error(issues, source, route, exception.getMessage());
            return null;
        }
    }

    private BigDecimal decimal(String source, YamlDocument document, String route, List<ConfigurationIssue> issues) {
        if (!document.contains(route)) {
            error(issues, source, route, "Missing required decimal");
            return null;
        }
        Object raw = document.get(route);
        if (!(raw instanceof Number) && !(raw instanceof String)) {
            error(issues, source, route, "Expected a decimal value");
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.toString()).stripTrailingZeros();
            if (value.compareTo(BigDecimal.ONE) < 0) {
                error(issues, source, route, "Must be greater than or equal to 1");
                return null;
            }
            if (value.scale() > PERSISTED_MULTIPLIER_SCALE) {
                error(issues, source, route, "Cannot have more than " + PERSISTED_MULTIPLIER_SCALE + " decimal places");
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            error(issues, source, route, "Expected a finite decimal value");
            return null;
        }
    }

    private Duration positiveDuration(String source, YamlDocument document, String route, List<ConfigurationIssue> issues) {
        String raw = requiredString(source, document, route, issues);
        if (raw == null) {
            return null;
        }
        try {
            return DurationParser.positive(raw, route);
        } catch (IllegalArgumentException exception) {
            error(issues, source, route, exception.getMessage());
            return null;
        }
    }

    private Duration nonNegativeDuration(String source, Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        String raw = requiredString(source, section, route, fullPath, issues);
        if (raw == null) {
            return null;
        }
        try {
            return DurationParser.nonNegative(raw, fullPath);
        } catch (IllegalArgumentException exception) {
            error(issues, source, fullPath, exception.getMessage());
            return null;
        }
    }

    private Set<String> stringSet(String source, Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, source, fullPath, "Missing required list");
            return null;
        }
        if (!section.isList(route)) {
            error(issues, source, fullPath, "Expected a list");
            return null;
        }
        List<?> rawList = section.getList(route);
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < rawList.size(); index++) {
            Object raw = rawList.get(index);
            String path = fullPath + "[" + index + "]";
            if (!(raw instanceof String value)) {
                error(issues, source, path, "Expected a string value");
                continue;
            }
            if (value.isBlank()) {
                error(issues, source, path, "Cannot be empty");
                continue;
            }
            String normalized = value.trim();
            if (!values.add(normalized)) {
                error(issues, source, path, "Duplicate value: " + normalized);
            }
        }
        return values;
    }

    private Set<String> scopeValues(String source, Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, source, fullPath, "Missing required list");
            return null;
        }
        if (!section.isList(route)) {
            error(issues, source, fullPath, "Expected a list");
            return null;
        }

        List<?> rawList = section.getList(route);
        Set<String> values = new LinkedHashSet<>();
        int wildcardIndex = -1;
        for (int index = 0; index < rawList.size(); index++) {
            Object raw = rawList.get(index);
            String path = fullPath + "[" + index + "]";
            if (!(raw instanceof String value)) {
                error(issues, source, path, "Expected a string value");
                continue;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                error(issues, source, path, "Cannot be empty");
                continue;
            }
            if (BoosterScope.WILDCARD.equals(normalized)) {
                wildcardIndex = index;
            } else if (!SCOPE_ID_PATTERN.matcher(normalized).matches()) {
                error(issues, source, path, "Invalid scope identifier: " + value);
                continue;
            }
            if (!values.add(normalized)) {
                error(issues, source, path, "Duplicate value: " + normalized);
            }
        }
        if (values.isEmpty()) {
            error(issues, source, fullPath, "Cannot be empty");
            return null;
        }
        if (wildcardIndex >= 0 && values.size() > 1) {
            error(issues, source, fullPath + "[" + wildcardIndex + "]", "Wildcard cannot be combined with explicit values");
        }
        return values;
    }

    private <E extends Enum<E>> E enumValue(String source, YamlDocument document, String route, Class<E> enumType, List<ConfigurationIssue> issues) {
        String value = requiredString(source, document, route, issues);
        return value == null ? null : parseEnum(source, route, value, enumType, issues);
    }

    private <E extends Enum<E>> E enumValue(String source, Section section, String route, String fullPath, Class<E> enumType, List<ConfigurationIssue> issues) {
        String value = requiredString(source, section, route, fullPath, issues);
        return value == null ? null : parseEnum(source, fullPath, value, enumType, issues);
    }

    private <E extends Enum<E>> E parseEnum(String source, String path, String value, Class<E> enumType, List<ConfigurationIssue> issues) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            error(issues, source, path, "Invalid value: " + value);
            return null;
        }
    }

    private Integer requiredInt(String source, Section section, String route, List<ConfigurationIssue> issues) {
        return requiredInt(source, section, route, route, issues);
    }

    private Integer requiredInt(String source, Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, source, fullPath, "Missing required integer");
            return null;
        }
        if (!section.isInt(route)) {
            error(issues, source, fullPath, "Expected an integer value");
            return null;
        }
        return section.getInt(route);
    }

    private Long requiredLong(String source, Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, source, fullPath, "Missing required integer");
            return null;
        }
        if (!section.isLong(route) && !section.isInt(route)) {
            error(issues, source, fullPath, "Expected an integer value");
            return null;
        }
        return section.getLong(route);
    }

    private Boolean requiredBoolean(String source, Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, source, fullPath, "Missing required boolean");
            return null;
        }
        if (!section.isBoolean(route)) {
            error(issues, source, fullPath, "Expected a boolean value");
            return null;
        }
        return section.getBoolean(route);
    }

    private Boolean requiredBoolean(String source, YamlDocument document, String route, List<ConfigurationIssue> issues) {
        return requiredBoolean(source, document, route, route, issues);
    }

    private String requiredString(String source, Section section, String route, List<ConfigurationIssue> issues) {
        return requiredString(source, section, route, route, issues);
    }

    private String requiredString(String source, Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, source, fullPath, "Missing required value");
            return null;
        }
        if (!section.isString(route)) {
            error(issues, source, fullPath, "Expected a string value");
            return null;
        }
        String value = section.getString(route);
        if (value == null || value.isBlank()) {
            error(issues, source, fullPath, "Cannot be empty");
            return null;
        }
        return value.trim();
    }

    private String optionalString(String source, Section section, String route, String fullPath, String fallback, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            return fallback;
        }
        if (!section.isString(route)) {
            error(issues, source, fullPath, "Expected a string value");
            return null;
        }
        String value = section.getString(route);
        return value == null ? fallback : value.trim();
    }

    private String optionalString(String source, Section section, String route, String fallback, List<ConfigurationIssue> issues) {
        return optionalString(source, section, route, route, fallback, issues);
    }

    private Integer optionalInt(Section section, String route, Integer fallback, List<ConfigurationIssue> issues, String source) {
        if (!section.contains(route)) {
            return fallback;
        }
        if (!section.isInt(route)) {
            error(issues, source, route, "Expected an integer value");
            return fallback;
        }
        return section.getInt(route);
    }

    private Boolean optionalBoolean(Section section, String route, boolean fallback, List<ConfigurationIssue> issues, String source) {
        if (!section.contains(route)) {
            return fallback;
        }
        if (!section.isBoolean(route)) {
            error(issues, source, route, "Expected a boolean value");
            return fallback;
        }
        return section.getBoolean(route);
    }

    private static void error(List<ConfigurationIssue> issues, String source, String path, String message) {
        issues.add(ConfigurationIssue.error(source, path, message));
    }

    private static String relative(File file) {
        File parent = file.getParentFile();
        if (parent != null && "boosters".equals(parent.getName())) {
            return "boosters/" + file.getName();
        }
        return file.getName();
    }
}
