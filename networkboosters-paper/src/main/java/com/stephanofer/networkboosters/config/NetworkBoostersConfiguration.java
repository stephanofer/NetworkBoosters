package com.stephanofer.networkboosters.config;

import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.ExistingSchemaStrategy;
import com.hera.craftkit.database.ExecutorConfig;
import com.hera.craftkit.database.MigrationConfig;
import com.hera.craftkit.database.PoolConfig;
import com.hera.craftkit.redis.RedisConfig;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import java.time.Duration;
import java.util.Locale;

public record NetworkBoostersConfiguration(
    String serverId,
    String gameId,
    Storage storage,
    Redis redis
) {

    public static NetworkBoostersConfiguration load(YamlDocument config) {
        int version = config.getInt("config-version", 0);
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported config-version: " + version);
        }

        Section server = requiredSection(config, "server");
        Section storage = requiredSection(config, "storage");
        Section pool = requiredSection(storage, "pool");
        Section redis = requiredSection(config, "redis");

        String serverId = requiredString(server, "id");
        String gameId = requiredString(server, "game-id");

        Storage storageConfig = new Storage(
            requiredString(storage, "host"),
            port(storage.getInt("port", 3306), "storage.port"),
            requiredString(storage, "database"),
            requiredString(storage, "username"),
            storage.getString("password", ""),
            storage.getString("table-prefix", ""),
            positiveInt(pool.getInt("maximum-size", 10), "storage.pool.maximum-size"),
            nonNegativeInt(pool.getInt("minimum-idle", 2), "storage.pool.minimum-idle"),
            duration(pool.getString("connection-timeout", "10s"), "storage.pool.connection-timeout"),
            duration(pool.getString("validation-timeout", "5s"), "storage.pool.validation-timeout"),
            duration(pool.getString("shutdown-timeout", "10s"), "storage.pool.shutdown-timeout")
        );

        Redis redisConfig = new Redis(
            requiredString(redis, "host"),
            port(redis.getInt("port", 6379), "redis.port"),
            nonNegativeInt(redis.getInt("database", 0), "redis.database"),
            redis.getString("username", ""),
            redis.getString("password", ""),
            redis.getBoolean("ssl", false),
            redis.getBoolean("verify-peer", true),
            requiredString(redis, "key-prefix"),
            requiredString(redis, "environment"),
            duration(redis.getString("command-timeout", "3s"), "redis.command-timeout"),
            duration(redis.getString("connect-timeout", "3s"), "redis.connect-timeout"),
            duration(redis.getString("shutdown-timeout", "5s"), "redis.shutdown-timeout"),
            redis.getBoolean("auto-reconnect", true)
        );

        return new NetworkBoostersConfiguration(serverId, gameId, storageConfig, redisConfig);
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

        public DatabaseConfig toDatabaseConfig(ClassLoader classLoader) {
            if (minimumIdle > maximumPoolSize) {
                throw new IllegalArgumentException("storage.pool.minimum-idle cannot be greater than maximum-size");
            }

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
        boolean autoReconnect
    ) {

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

    private static Section requiredSection(Section parent, String path) {
        Section section = parent.getSection(path, null);
        if (section == null) {
            throw new IllegalArgumentException("Missing configuration section: " + path);
        }
        return section;
    }

    private static String requiredString(Section section, String path) {
        String value = section.getString(path, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing configuration value: " + path);
        }
        return value.trim();
    }

    private static int port(int value, String path) {
        if (value < 1 || value > 65_535) {
            throw new IllegalArgumentException(path + " must be between 1 and 65535");
        }
        return value;
    }

    private static int positiveInt(int value, String path) {
        if (value < 1) {
            throw new IllegalArgumentException(path + " must be positive");
        }
        return value;
    }

    private static int nonNegativeInt(int value, String path) {
        if (value < 0) {
            throw new IllegalArgumentException(path + " cannot be negative");
        }
        return value;
    }

    private static Duration duration(String raw, String path) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(path + " cannot be empty");
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        long multiplier;
        String number;
        if (normalized.endsWith("ms")) {
            multiplier = 1L;
            number = normalized.substring(0, normalized.length() - 2);
        } else if (normalized.endsWith("s")) {
            multiplier = 1_000L;
            number = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("m")) {
            multiplier = 60_000L;
            number = normalized.substring(0, normalized.length() - 1);
        } else {
            throw new IllegalArgumentException(path + " must use ms, s, or m suffix");
        }

        try {
            long value = Long.parseLong(number);
            if (value <= 0) {
                throw new IllegalArgumentException(path + " must be positive");
            }
            return Duration.ofMillis(Math.multiplyExact(value, multiplier));
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(path + " has invalid duration: " + raw, exception);
        }
    }
}
