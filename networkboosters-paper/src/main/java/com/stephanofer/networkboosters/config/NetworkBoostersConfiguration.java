package com.stephanofer.networkboosters.config;

import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.ExistingSchemaStrategy;
import com.hera.craftkit.database.ExecutorConfig;
import com.hera.craftkit.database.MigrationConfig;
import com.hera.craftkit.database.PoolConfig;
import com.hera.craftkit.redis.RedisConfig;
import com.stephanofer.networkboosters.capacity.InventoryCapacityRule;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record NetworkBoostersConfiguration(
    String serverId,
    String gameId,
    Storage storage,
    Redis redis,
    Limits limits,
    Activation activation,
    InventoryLimits inventoryLimits,
    Localization localization,
    Commands commands,
    PlaceholderApi placeholderApi
) {

    private static final String CONFIG_FILE = "config.yml";
    private static final Pattern ID_WITH_DOTS = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    public NetworkBoostersConfiguration {
        serverId = normalize(serverId, "serverId", ID_WITH_DOTS);
        gameId = normalize(gameId, "gameId", ID_WITH_DOTS);
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(inventoryLimits, "inventoryLimits");
        Objects.requireNonNull(localization, "localization");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(placeholderApi, "placeholderApi");
    }

    public static NetworkBoostersConfiguration load(YamlDocument config) {
        List<ConfigurationIssue> issues = new ArrayList<>();
        NetworkBoostersConfiguration loaded = parse(config, issues);
        if (!errors(issues).isEmpty()) {
            throw new ConfigurationException(issues);
        }
        return loaded;
    }

    public static NetworkBoostersConfiguration parse(YamlDocument config, List<ConfigurationIssue> issues) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(issues, "issues");

        Integer version = requiredInt(config, "config-version", issues);
        if (version != null && version != 1) {
            error(issues, "config-version", "Unsupported config-version: " + version);
        }

        Section server = requiredSection(config, "server", issues);
        Section storage = requiredSection(config, "storage", issues);
        Section pool = storage == null ? null : requiredSection(storage, "pool", "storage.pool", issues);
        Section redis = requiredSection(config, "redis", issues);
        Section limits = requiredSection(config, "limits", issues);
        Section activation = requiredSection(config, "activation", issues);
        Section inventoryLimits = requiredSection(config, "inventory-limits", issues);
        Section localization = requiredSection(config, "localization", issues);
        Section commands = requiredSection(config, "commands", issues);
        Section placeholderApi = requiredSection(config, "placeholderapi", issues);

        String serverId = server == null ? null : requiredIdentifier(server, "id", "server.id", ID_WITH_DOTS, issues);
        String gameId = server == null ? null : requiredIdentifier(server, "game-id", "server.game-id", ID_WITH_DOTS, issues);

        Storage storageConfig = parseStorage(storage, pool, issues);
        Redis redisConfig = parseRedis(redis, issues);
        Limits limitsConfig = parseLimits(limits, issues);
        Activation activationConfig = parseActivation(activation, issues);
        InventoryLimits inventoryLimitsConfig = parseInventoryLimits(inventoryLimits, issues);
        Localization localizationConfig = parseLocalization(localization, issues);
        Commands commandsConfig = parseCommands(commands, issues);
        PlaceholderApi placeholderApiConfig = parsePlaceholderApi(placeholderApi, issues);

        if (!errors(issues).isEmpty()) {
            return null;
        }

        return new NetworkBoostersConfiguration(
            serverId,
            gameId,
            storageConfig,
            redisConfig,
            limitsConfig,
            activationConfig,
            inventoryLimitsConfig,
            localizationConfig,
            commandsConfig,
            placeholderApiConfig
        );
    }

    public static Commands parseCommands(YamlDocument config, List<ConfigurationIssue> issues) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(issues, "issues");
        return parseCommands(requiredSection(config, "commands", issues), issues);
    }

    public record Storage(
        String host,
        int port,
        String database,
        String username,
        String password,
        String tablePrefix,
        int maximumPoolSize,
        int minimumIdle,
        Duration connectionTimeout,
        Duration validationTimeout,
        Duration shutdownTimeout
    ) {

        public Storage {
            host = requireNotBlank(host, "host");
            database = requireNotBlank(database, "database");
            username = requireNotBlank(username, "username");
            password = Objects.requireNonNull(password, "password");
            tablePrefix = Objects.requireNonNull(tablePrefix, "tablePrefix");
            validatePort(port, "storage.port");
            if (maximumPoolSize < 1) {
                throw new IllegalArgumentException("maximumPoolSize must be positive");
            }
            if (minimumIdle < 0) {
                throw new IllegalArgumentException("minimumIdle cannot be negative");
            }
            if (minimumIdle > maximumPoolSize) {
                throw new IllegalArgumentException("minimumIdle cannot be greater than maximumPoolSize");
            }
            requirePositive(connectionTimeout, "connectionTimeout");
            requirePositive(validationTimeout, "validationTimeout");
            requirePositive(shutdownTimeout, "shutdownTimeout");
        }

        public DatabaseConfig toDatabaseConfig(ClassLoader classLoader) {
            MigrationConfig migration = MigrationConfig.builder()
                .existingSchemaStrategy(ExistingSchemaStrategy.BASELINE_AT_ZERO)
                .classLoader(classLoader)
                .build();

            PoolConfig pool = PoolConfig.builder()
                .poolName("networkboosters-mysql")
                .maximumPoolSize(maximumPoolSize)
                .minimumIdle(minimumIdle)
                .connectionTimeoutMillis(connectionTimeout.toMillis())
                .validationTimeoutMillis(validationTimeout.toMillis())
                .build();

            ExecutorConfig executor = ExecutorConfig.builder()
                .threadCount(maximumPoolSize)
                .threadNamePrefix("networkboosters-db")
                .shutdownTimeoutMillis(shutdownTimeout.toMillis())
                .build();

            return DatabaseConfig.builder()
                .host(host)
                .port(port)
                .database(database)
                .username(username)
                .password(password)
                .tablePrefix(tablePrefix)
                .pool(pool)
                .executor(executor)
                .migration(migration)
                .build();
        }
    }

    public record Redis(
        boolean enabled,
        String host,
        int port,
        int database,
        String username,
        String password,
        boolean ssl,
        boolean verifyPeer,
        String keyPrefix,
        String environment,
        Duration commandTimeout,
        Duration connectTimeout,
        Duration shutdownTimeout,
        boolean autoReconnect,
        Duration reconciliationInterval,
        Duration degradedReconciliationInterval
    ) {

        public Redis {
            host = requireNotBlank(host, "host");
            username = Objects.requireNonNull(username, "username");
            password = Objects.requireNonNull(password, "password");
            keyPrefix = requireNotBlank(keyPrefix, "keyPrefix");
            environment = requireNotBlank(environment, "environment");
            validatePort(port, "redis.port");
            if (database < 0) {
                throw new IllegalArgumentException("database cannot be negative");
            }
            requirePositive(commandTimeout, "commandTimeout");
            requirePositive(connectTimeout, "connectTimeout");
            requirePositive(shutdownTimeout, "shutdownTimeout");
            requirePositive(reconciliationInterval, "reconciliationInterval");
            requirePositive(degradedReconciliationInterval, "degradedReconciliationInterval");
            if (degradedReconciliationInterval.compareTo(reconciliationInterval) > 0) {
                throw new IllegalArgumentException("degradedReconciliationInterval cannot be greater than reconciliationInterval");
            }
        }

        public RedisConfig toRedisConfig(String serverId) {
            return RedisConfig.builder()
                .host(host)
                .port(port)
                .database(database)
                .username(username)
                .password(password)
                .ssl(ssl)
                .verifyPeer(verifyPeer)
                .keyPrefix(keyPrefix)
                .environment(environment)
                .serverId(serverId)
                .commandTimeout(commandTimeout)
                .connectTimeout(connectTimeout)
                .shutdownTimeout(shutdownTimeout)
                .autoReconnect(autoReconnect)
                .build();
        }
    }

    public record Limits(BigDecimal maximumMultiplier) {

        public Limits {
            maximumMultiplier = Objects.requireNonNull(maximumMultiplier, "maximumMultiplier").stripTrailingZeros();
            if (maximumMultiplier.compareTo(BigDecimal.ONE) < 0) {
                throw new IllegalArgumentException("maximumMultiplier must be greater than or equal to 1");
            }
        }
    }

    public record Activation(
        Duration maximumTotalDuration,
        int maximumQueuedEntries,
        Duration expiryCheckInterval,
        int expiryBatchSize,
        List<Duration> expiryWarnings
    ) {

        public Activation {
            requirePositive(maximumTotalDuration, "maximumTotalDuration");
            if (maximumQueuedEntries < 0) {
                throw new IllegalArgumentException("maximumQueuedEntries cannot be negative");
            }
            requirePositive(expiryCheckInterval, "expiryCheckInterval");
            if (expiryBatchSize < 1) {
                throw new IllegalArgumentException("expiryBatchSize must be positive");
            }
            expiryWarnings = List.copyOf(Objects.requireNonNull(expiryWarnings, "expiryWarnings"));
            for (Duration warning : expiryWarnings) {
                requirePositive(warning, "expiryWarning");
                if (warning.compareTo(maximumTotalDuration) >= 0) {
                    throw new IllegalArgumentException("expiryWarning must be lower than maximumTotalDuration");
                }
            }
        }
    }

    public record InventoryLimits(long fallback, List<InventoryCapacityRule> tiers) {

        public InventoryLimits {
            if (fallback < 0) {
                throw new IllegalArgumentException("fallback cannot be negative");
            }
            tiers = List.copyOf(Objects.requireNonNull(tiers, "tiers"));
        }
    }

    public record Localization(String fallbackLanguage, String consoleLanguage) {

        public Localization {
            fallbackLanguage = normalizeLanguage(fallbackLanguage, "fallbackLanguage");
            consoleLanguage = normalizeLanguage(consoleLanguage, "consoleLanguage");
        }
    }

    public record Commands(String root, List<String> aliases) {

        public Commands {
            root = normalize(root, "root", ID);
            List<String> normalizedAliases = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            seen.add(root);
            for (String alias : Objects.requireNonNull(aliases, "aliases")) {
                String normalized = normalize(alias, "alias", ID);
                if (!seen.add(normalized)) {
                    throw new IllegalArgumentException("Duplicate command alias: " + alias);
                }
                normalizedAliases.add(normalized);
            }
            aliases = List.copyOf(normalizedAliases);
        }
    }

    public record PlaceholderApi(boolean enabled) {
    }

    private static Storage parseStorage(Section storage, Section pool, List<ConfigurationIssue> issues) {
        if (storage == null || pool == null) {
            return null;
        }
        int issueCount = issues.size();
        String host = requiredString(storage, "host", "storage.host", issues);
        String database = requiredString(storage, "database", "storage.database", issues);
        String username = requiredString(storage, "username", "storage.username", issues);
        String password = optionalString(storage, "password", "storage.password", "", issues);
        String tablePrefix = optionalString(storage, "table-prefix", "storage.table-prefix", "", issues);
        Integer port = optionalInt(storage, "port", "storage.port", 3306, issues);
        Integer maximumSize = optionalInt(pool, "maximum-size", "storage.pool.maximum-size", 10, issues);
        Integer minimumIdle = optionalInt(pool, "minimum-idle", "storage.pool.minimum-idle", 2, issues);
        Duration connectionTimeout = optionalPositiveDuration(pool, "connection-timeout", "storage.pool.connection-timeout", "10s", issues);
        Duration validationTimeout = optionalPositiveDuration(pool, "validation-timeout", "storage.pool.validation-timeout", "5s", issues);
        Duration shutdownTimeout = optionalPositiveDuration(pool, "shutdown-timeout", "storage.pool.shutdown-timeout", "10s", issues);

        if (port != null && (port < 1 || port > 65_535)) {
            error(issues, "storage.port", "Must be between 1 and 65535");
        }
        if (maximumSize != null && maximumSize < 1) {
            error(issues, "storage.pool.maximum-size", "Must be positive");
        }
        if (minimumIdle != null && minimumIdle < 0) {
            error(issues, "storage.pool.minimum-idle", "Cannot be negative");
        }
        if (minimumIdle != null && maximumSize != null && minimumIdle > maximumSize) {
            error(issues, "storage.pool.minimum-idle", "Cannot be greater than storage.pool.maximum-size");
        }

        if (issues.size() > issueCount || host == null || database == null || username == null || password == null || tablePrefix == null
            || port == null || maximumSize == null || minimumIdle == null
            || connectionTimeout == null || validationTimeout == null || shutdownTimeout == null) {
            return null;
        }
        return new Storage(
            host,
            port,
            database,
            username,
            password,
            tablePrefix,
            maximumSize,
            minimumIdle,
            connectionTimeout,
            validationTimeout,
            shutdownTimeout
        );
    }

    private static Redis parseRedis(Section redis, List<ConfigurationIssue> issues) {
        if (redis == null) {
            return null;
        }
        int issueCount = issues.size();
        Boolean enabled = optionalBoolean(redis, "enabled", "redis.enabled", true, issues);
        String host = requiredString(redis, "host", "redis.host", issues);
        String username = optionalString(redis, "username", "redis.username", "", issues);
        String password = optionalString(redis, "password", "redis.password", "", issues);
        Boolean ssl = optionalBoolean(redis, "ssl", "redis.ssl", false, issues);
        Boolean verifyPeer = optionalBoolean(redis, "verify-peer", "redis.verify-peer", true, issues);
        String keyPrefix = requiredString(redis, "key-prefix", "redis.key-prefix", issues);
        String environment = requiredString(redis, "environment", "redis.environment", issues);
        Duration commandTimeout = optionalPositiveDuration(redis, "command-timeout", "redis.command-timeout", "3s", issues);
        Duration connectTimeout = optionalPositiveDuration(redis, "connect-timeout", "redis.connect-timeout", "3s", issues);
        Duration shutdownTimeout = optionalPositiveDuration(redis, "shutdown-timeout", "redis.shutdown-timeout", "5s", issues);
        Boolean autoReconnect = optionalBoolean(redis, "auto-reconnect", "redis.auto-reconnect", true, issues);
        Integer port = optionalInt(redis, "port", "redis.port", 6379, issues);
        Integer database = optionalInt(redis, "database", "redis.database", 0, issues);
        Duration reconciliationInterval = optionalPositiveDuration(redis, "reconciliation-interval", "redis.reconciliation-interval", "30s", issues);
        Duration degradedReconciliationInterval = optionalPositiveDuration(redis, "degraded-reconciliation-interval", "redis.degraded-reconciliation-interval", "5s", issues);

        if (port != null && (port < 1 || port > 65_535)) {
            error(issues, "redis.port", "Must be between 1 and 65535");
        }
        if (database != null && database < 0) {
            error(issues, "redis.database", "Cannot be negative");
        }
        if (reconciliationInterval != null && degradedReconciliationInterval != null
            && degradedReconciliationInterval.compareTo(reconciliationInterval) > 0) {
            error(issues, "redis.degraded-reconciliation-interval", "Cannot be greater than redis.reconciliation-interval");
        }

        if (issues.size() > issueCount || enabled == null || host == null || username == null || password == null || ssl == null
            || verifyPeer == null || keyPrefix == null || environment == null
            || commandTimeout == null || connectTimeout == null || shutdownTimeout == null || autoReconnect == null
            || port == null || database == null || reconciliationInterval == null || degradedReconciliationInterval == null) {
            return null;
        }
        return new Redis(
            enabled,
            host,
            port,
            database,
            username,
            password,
            ssl,
            verifyPeer,
            keyPrefix,
            environment,
            commandTimeout,
            connectTimeout,
            shutdownTimeout,
            autoReconnect,
            reconciliationInterval,
            degradedReconciliationInterval
        );
    }

    private static Limits parseLimits(Section limits, List<ConfigurationIssue> issues) {
        if (limits == null) {
            return null;
        }
        int issueCount = issues.size();
        BigDecimal maximumMultiplier = requiredDecimal(limits, "maximum-multiplier", "limits.maximum-multiplier", issues);
        if (maximumMultiplier != null && maximumMultiplier.compareTo(BigDecimal.ONE) < 0) {
            error(issues, "limits.maximum-multiplier", "Must be greater than or equal to 1");
        }
        return issues.size() > issueCount || maximumMultiplier == null ? null : new Limits(maximumMultiplier);
    }

    private static Activation parseActivation(Section activation, List<ConfigurationIssue> issues) {
        if (activation == null) {
            return null;
        }
        int issueCount = issues.size();
        Duration maximumTotalDuration = requiredPositiveDuration(activation, "maximum-total-duration", "activation.maximum-total-duration", issues);
        Integer maximumQueuedEntries = requiredInt(activation, "maximum-queued-entries", "activation.maximum-queued-entries", issues);
        Duration expiryCheckInterval = optionalPositiveDuration(activation, "expiry-check-interval", "activation.expiry-check-interval", "1s", issues);
        Integer expiryBatchSize = optionalInt(activation, "expiry-batch-size", "activation.expiry-batch-size", 100, issues);
        if (maximumQueuedEntries != null && maximumQueuedEntries < 0) {
            error(issues, "activation.maximum-queued-entries", "Cannot be negative");
        }
        if (expiryBatchSize != null && expiryBatchSize < 1) {
            error(issues, "activation.expiry-batch-size", "Must be positive");
        }
        List<Duration> warnings = durationList(activation, "expiry-warnings", "activation.expiry-warnings", issues);
        if (maximumTotalDuration != null) {
            Set<Duration> seen = new HashSet<>();
            for (int index = 0; index < warnings.size(); index++) {
                Duration warning = warnings.get(index);
                if (!seen.add(warning)) {
                    error(issues, "activation.expiry-warnings[" + index + "]", "Duplicate expiry warning");
                }
                if (warning.compareTo(maximumTotalDuration) >= 0) {
                    error(issues, "activation.expiry-warnings[" + index + "]", "Must be lower than activation.maximum-total-duration");
                }
            }
        }
        warnings.sort((left, right) -> right.compareTo(left));
        return issues.size() > issueCount || maximumTotalDuration == null || maximumQueuedEntries == null
            || expiryCheckInterval == null || expiryBatchSize == null
            ? null
            : new Activation(maximumTotalDuration, maximumQueuedEntries, expiryCheckInterval, expiryBatchSize, warnings);
    }

    private static InventoryLimits parseInventoryLimits(Section inventoryLimits, List<ConfigurationIssue> issues) {
        if (inventoryLimits == null) {
            return null;
        }
        int issueCount = issues.size();
        Long fallback = requiredLong(inventoryLimits, "fallback", "inventory-limits.fallback", issues);
        if (fallback != null && fallback < 0) {
            error(issues, "inventory-limits.fallback", "Cannot be negative");
        }

        Section tiers = inventoryLimits.getSection("tiers", null);
        List<InventoryCapacityRule> rules = new ArrayList<>();
        if (tiers != null) {
            Map<String, String> normalizedIds = new LinkedHashMap<>();
            for (Object key : tiers.getKeys()) {
                String rawId = String.valueOf(key);
                String id = normalizeOrIssue(rawId, "inventory-limits.tiers." + rawId, ID, issues);
                if (id == null) {
                    continue;
                }
                String previous = normalizedIds.putIfAbsent(id, rawId);
                if (previous != null) {
                    error(issues, "inventory-limits.tiers." + rawId, "Duplicate inventory tier ID after normalization; first declared as " + previous);
                    continue;
                }

                Section tier = tiers.getSection(rawId, null);
                if (tier == null) {
                    error(issues, "inventory-limits.tiers." + rawId, "Must be a section");
                    continue;
                }
                String path = "inventory-limits.tiers." + rawId;
                String permission = requiredString(tier, "permission", path + ".permission", issues);
                Long maximum = requiredLong(tier, "maximum", path + ".maximum", issues);
                Integer priority = requiredInt(tier, "priority", path + ".priority", issues);
                if (maximum != null && maximum < 0) {
                    error(issues, path + ".maximum", "Cannot be negative");
                }
                if (permission != null && maximum != null && priority != null) {
                    rules.add(new InventoryCapacityRule(id, Optional.of(permission), maximum, priority));
                }
            }
        }
        rules.sort((left, right) -> left.id().compareTo(right.id()));
        return issues.size() > issueCount || fallback == null ? null : new InventoryLimits(fallback, rules);
    }

    private static Commands parseCommands(Section commands, List<ConfigurationIssue> issues) {
        if (commands == null) {
            return null;
        }
        int issueCount = issues.size();
        String root = requiredIdentifier(commands, "root", "commands.root", ID, issues);
        List<String> aliases = stringList(commands, "aliases", "commands.aliases", issues);
        Set<String> seen = new HashSet<>();
        if (root != null) {
            seen.add(root);
        }
        for (int index = 0; index < aliases.size(); index++) {
            String alias = aliases.get(index);
            if (alias == null) {
                continue;
            }
            String normalized = normalizeOrIssue(alias, "commands.aliases[" + index + "]", ID, issues);
            if (normalized == null) {
                continue;
            }
            if (!seen.add(normalized)) {
                error(issues, "commands.aliases[" + index + "]", "Command aliases cannot duplicate the root command or another alias");
            }
            aliases.set(index, normalized);
        }
        return issues.size() > issueCount || root == null ? null : new Commands(root, aliases);
    }

    private static Localization parseLocalization(Section localization, List<ConfigurationIssue> issues) {
        if (localization == null) {
            return null;
        }
        int issueCount = issues.size();
        String fallback = requiredString(localization, "fallback-language", "localization.fallback-language", issues);
        String console = requiredString(localization, "console-language", "localization.console-language", issues);
        fallback = normalizeLanguageOrIssue(fallback, "localization.fallback-language", issues);
        console = normalizeLanguageOrIssue(console, "localization.console-language", issues);
        return issues.size() > issueCount || fallback == null || console == null ? null : new Localization(fallback, console);
    }

    private static PlaceholderApi parsePlaceholderApi(Section placeholderApi, List<ConfigurationIssue> issues) {
        if (placeholderApi == null) {
            return null;
        }
        return new PlaceholderApi(optionalBoolean(placeholderApi, "enabled", "placeholderapi.enabled", true, issues));
    }

    private static Section requiredSection(Section parent, String path, List<ConfigurationIssue> issues) {
        return requiredSection(parent, path, path, issues);
    }

    private static Section requiredSection(Section parent, String route, String fullPath, List<ConfigurationIssue> issues) {
        Section section = parent.getSection(route, null);
        if (section == null) {
            error(issues, fullPath, "Missing required section");
        }
        return section;
    }

    private static String requiredString(Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, fullPath, "Missing required value");
            return null;
        }
        if (!section.isString(route)) {
            error(issues, fullPath, "Expected a string value");
            return null;
        }
        String value = section.getString(route);
        if (value == null || value.isBlank()) {
            error(issues, fullPath, "Cannot be empty");
            return null;
        }
        return value.trim();
    }

    private static String optionalString(Section section, String route, String fullPath, String fallback, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            return fallback;
        }
        if (!section.isString(route)) {
            error(issues, fullPath, "Expected a string value");
            return fallback;
        }
        String value = section.getString(route);
        return value == null ? fallback : value.trim();
    }

    private static String requiredIdentifier(Section section, String route, String fullPath, Pattern pattern, List<ConfigurationIssue> issues) {
        String value = requiredString(section, route, fullPath, issues);
        return value == null ? null : normalizeOrIssue(value, fullPath, pattern, issues);
    }

    private static String normalizeOrIssue(String raw, String fullPath, Pattern pattern, List<ConfigurationIssue> issues) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!pattern.matcher(normalized).matches()) {
            error(issues, fullPath, "Invalid identifier: " + raw);
            return null;
        }
        return normalized;
    }

    private static Integer requiredInt(Section section, String route, List<ConfigurationIssue> issues) {
        return requiredInt(section, route, route, issues);
    }

    private static Integer requiredInt(Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, fullPath, "Missing required integer");
            return null;
        }
        if (!section.isInt(route)) {
            error(issues, fullPath, "Expected an integer value");
            return null;
        }
        return section.getInt(route);
    }

    private static Integer optionalInt(Section section, String route, String fullPath, int fallback, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            return fallback;
        }
        if (!section.isInt(route)) {
            error(issues, fullPath, "Expected an integer value");
            return fallback;
        }
        return section.getInt(route);
    }

    private static Long requiredLong(Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, fullPath, "Missing required integer");
            return null;
        }
        if (!section.isLong(route) && !section.isInt(route)) {
            error(issues, fullPath, "Expected an integer value");
            return null;
        }
        return section.getLong(route);
    }

    private static Boolean optionalBoolean(Section section, String route, String fullPath, boolean fallback, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            return fallback;
        }
        if (!section.isBoolean(route)) {
            error(issues, fullPath, "Expected a boolean value");
            return fallback;
        }
        return section.getBoolean(route);
    }

    private static BigDecimal requiredDecimal(Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, fullPath, "Missing required decimal");
            return null;
        }
        Object raw = section.get(route);
        if (!(raw instanceof Number) && !(raw instanceof String)) {
            error(issues, fullPath, "Expected a decimal value");
            return null;
        }
        try {
            return new BigDecimal(raw.toString()).stripTrailingZeros();
        } catch (NumberFormatException exception) {
            error(issues, fullPath, "Expected a finite decimal value");
            return null;
        }
    }

    private static Duration requiredPositiveDuration(Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        String raw = requiredString(section, route, fullPath, issues);
        if (raw == null) {
            return null;
        }
        try {
            return DurationParser.positive(raw, fullPath);
        } catch (IllegalArgumentException exception) {
            error(issues, fullPath, exception.getMessage());
            return null;
        }
    }

    private static Duration optionalPositiveDuration(Section section, String route, String fullPath, String fallback, List<ConfigurationIssue> issues) {
        String raw = section.contains(route) ? requiredString(section, route, fullPath, issues) : fallback;
        if (raw == null) {
            return null;
        }
        try {
            return DurationParser.positive(raw, fullPath);
        } catch (IllegalArgumentException exception) {
            error(issues, fullPath, exception.getMessage());
            return null;
        }
    }

    private static List<Duration> durationList(Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        List<String> rawValues = stringList(section, route, fullPath, issues);
        List<Duration> durations = new ArrayList<>();
        for (int index = 0; index < rawValues.size(); index++) {
            String raw = rawValues.get(index);
            if (raw == null) {
                continue;
            }
            try {
                durations.add(DurationParser.positive(raw, fullPath + "[" + index + "]"));
            } catch (IllegalArgumentException exception) {
                error(issues, fullPath + "[" + index + "]", exception.getMessage());
            }
        }
        return durations;
    }

    private static List<String> stringList(Section section, String route, String fullPath, List<ConfigurationIssue> issues) {
        if (!section.contains(route)) {
            error(issues, fullPath, "Missing required list");
            return new ArrayList<>();
        }
        if (!section.isList(route)) {
            error(issues, fullPath, "Expected a list");
            return new ArrayList<>();
        }
        List<?> rawList = section.getList(route);
        List<String> values = new ArrayList<>();
        for (int index = 0; index < rawList.size(); index++) {
            Object raw = rawList.get(index);
            if (!(raw instanceof String value)) {
                error(issues, fullPath + "[" + index + "]", "Expected a string value");
                values.add(null);
                continue;
            }
            if (value.isBlank()) {
                error(issues, fullPath + "[" + index + "]", "Cannot be empty");
                values.add(null);
                continue;
            }
            values.add(value.trim());
        }
        return values;
    }

    private static void error(List<ConfigurationIssue> issues, String path, String message) {
        issues.add(ConfigurationIssue.error(CONFIG_FILE, path, message));
    }

    private static List<ConfigurationIssue> errors(List<ConfigurationIssue> issues) {
        return issues.stream().filter(issue -> issue.severity() == ConfigurationIssue.Severity.ERROR).toList();
    }

    private static String normalize(String raw, String label, Pattern pattern) {
        String value = requireNotBlank(raw, label).toLowerCase(Locale.ROOT);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + raw);
        }
        return value;
    }

    private static String normalizeLanguage(String raw, String label) {
        String value = requireNotBlank(raw, label).toLowerCase(Locale.ROOT);
        if (!value.equals("es") && !value.equals("en")) {
            throw new IllegalArgumentException(label + " must be es or en");
        }
        return value;
    }

    private static String normalizeLanguageOrIssue(String raw, String path, List<ConfigurationIssue> issues) {
        if (raw == null) {
            return null;
        }
        String value = raw.toLowerCase(Locale.ROOT);
        if (!value.equals("es") && !value.equals("en")) {
            error(issues, path, "Must be es or en");
            return null;
        }
        return value;
    }

    private static String requireNotBlank(String raw, String label) {
        String value = Objects.requireNonNull(raw, label).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return value;
    }

    private static void validatePort(int port, String path) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(path + " must be between 1 and 65535");
        }
    }

    private static void requirePositive(Duration duration, String label) {
        Objects.requireNonNull(duration, label);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}
