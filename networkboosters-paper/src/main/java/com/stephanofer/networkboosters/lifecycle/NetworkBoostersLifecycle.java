package com.stephanofer.networkboosters.lifecycle;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.Databases;
import com.hera.craftkit.database.TransactionIsolation;
import com.hera.craftkit.database.TransactionOptions;
import com.hera.craftkit.database.TransactionRetryPolicy;
import com.hera.craftkit.redis.RedisClient;
import com.hera.craftkit.redis.RedisClients;
import com.hera.craftkit.redis.RedisConfig;
import com.hera.craftkit.redis.RedisStartupMode;
import com.hera.craftkit.redis.RedisStatusRegistration;
import com.hera.craftkit.redis.RedisSubscription;
import com.hera.craftkit.zmenu.ZMenuIntegration;
import com.hera.craftkit.zmenu.ZMenus;
import com.stephanofer.networkboosters.NetworkBoostersPlugin;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import com.stephanofer.networkplayersettings.settings.event.PlayerSettingsReadyEvent;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.incendo.cloud.paper.PaperCommandManager;

public final class NetworkBoostersLifecycle implements Listener {

    private static final String METADATA_TABLE = "metadata";
    private static final String PROBE_KEY_PREFIX = "__infrastructure_probe_";

    private final NetworkBoostersPlugin plugin;
    private final PaperCommandManager.Bootstrapped<CommandSourceStack> commandManager;
    private final AtomicReference<LifecycleState> state = new AtomicReference<>(LifecycleState.NEW);
    private final AtomicLong generation = new AtomicLong();

    private NetworkBoostersConfiguration configuration;
    private PlayerSettingsService playerSettings;
    private Database database;
    private RedisClient redis;
    private RedisStatusRegistration redisStatusRegistration;
    private RedisSubscription redisProbeSubscription;
    private ZMenuIntegration zmenu;

    public NetworkBoostersLifecycle(
        NetworkBoostersPlugin plugin,
        PaperCommandManager.Bootstrapped<CommandSourceStack> commandManager
    ) {
        this.plugin = plugin;
        this.commandManager = commandManager;
    }

    public LifecycleState state() {
        return this.state.get();
    }

    public void enable() {
        if (!this.state.compareAndSet(LifecycleState.NEW, LifecycleState.STARTING)) {
            throw new IllegalStateException("NetworkBoosters lifecycle cannot enable from state " + this.state.get());
        }

        long started = System.nanoTime();
        long currentGeneration = this.generation.incrementAndGet();
        this.plugin.getLogger().info("Starting NetworkBoosters " + this.plugin.getPluginMeta().getVersion());

        try {
            this.commandManager.onEnable();
            this.configuration = this.loadConfiguration();
            this.playerSettings = this.resolvePlayerSettings();
            this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
            this.startDatabase();
            this.startRedis(currentGeneration);
            this.startZMenu();
            this.processAlreadyOnlinePlayers();

            this.state.set(LifecycleState.RUNNING);
            this.plugin.getLogger().info("NetworkBoosters enabled in " + elapsedMillis(started) + " ms");
        } catch (Throwable throwable) {
            this.state.set(LifecycleState.FAILED);
            this.plugin.getLogger().log(Level.SEVERE, "NetworkBoosters failed during startup", throwable);
            this.closeStartedResources();
            this.plugin.getServer().getPluginManager().disablePlugin(this.plugin);
        }
    }

    public void disable() {
        LifecycleState previous = this.state.getAndSet(LifecycleState.STOPPING);
        if (previous == LifecycleState.STOPPED || previous == LifecycleState.STOPPING) {
            return;
        }

        long started = System.nanoTime();
        this.generation.incrementAndGet();
        this.closeStartedResources();
        this.state.set(LifecycleState.STOPPED);
        this.plugin.getLogger().info("NetworkBoosters stopped in " + elapsedMillis(started) + " ms");
    }

    @EventHandler
    public void onPlayerSettingsReady(PlayerSettingsReadyEvent event) {
        LifecycleState currentState = this.state.get();
        if (currentState != LifecycleState.STARTING && currentState != LifecycleState.RUNNING) {
            return;
        }

        Player player = event.player();
        this.plugin.getLogger().fine(() -> "Player settings ready for " + player.getUniqueId()
            + " with language " + event.resolvedLanguage().code());
    }

    private NetworkBoostersConfiguration loadConfiguration() {
        long started = System.nanoTime();
        File configFile = new File(this.plugin.getDataFolder(), "config.yml");
        try (InputStream defaults = this.plugin.getResource("config.yml")) {
            if (defaults == null) {
                throw new IllegalStateException("Missing embedded config.yml resource");
            }

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
            NetworkBoostersConfiguration loaded = NetworkBoostersConfiguration.load(document);
            this.plugin.getLogger().info("Configuration loaded with BoostedYAML in " + elapsedMillis(started) + " ms");
            return loaded;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load config.yml with BoostedYAML", exception);
        }
    }

    private PlayerSettingsService resolvePlayerSettings() {
        PlayerSettingsService service = this.plugin.getServer().getServicesManager().load(PlayerSettingsService.class);
        if (service == null) {
            throw new IllegalStateException("NetworkPlayerSettings is loaded but PlayerSettingsService is not registered");
        }
        this.plugin.getLogger().info("NetworkPlayerSettings service resolved");
        return service;
    }

    private void startDatabase() {
        long started = System.nanoTime();
        DatabaseConfig databaseConfig = this.configuration.storage().toDatabaseConfig(this.plugin.getClass().getClassLoader());
        this.database = Databases.mysql(databaseConfig);
        this.database.migrate().join();
        this.runDatabaseProbe();
        this.plugin.getLogger().info("Database connected, migrated and verified in " + elapsedMillis(started) + " ms");
    }

    private void runDatabaseProbe() {
        String key = PROBE_KEY_PREFIX + UUID.randomUUID();
        String table = this.database.table(METADATA_TABLE);

        try {
            this.database.transaction(this.retryingTransactionOptions(), connection -> {
                insertProbe(connection, table, key, "rollback");
                if (!probeExists(connection, table, key)) {
                    throw new SQLException("Inserted probe row was not visible inside its transaction");
                }
                throw new SQLException("Intentional rollback probe");
            }).join();
            throw new IllegalStateException("Rollback probe committed unexpectedly");
        } catch (CompletionException exception) {
            if (!(rootCause(exception) instanceof SQLException)) {
                throw exception;
            }
        }

        boolean rollbackClean = this.database.query(connection -> probeExists(connection, table, key)).join();
        if (rollbackClean) {
            throw new IllegalStateException("Rollback probe row persisted unexpectedly");
        }

        this.database.transaction(this.retryingTransactionOptions(), connection -> {
            insertProbe(connection, table, key, "commit");
            return null;
        }).join();

        boolean committed = this.database.query(connection -> probeExists(connection, table, key)).join();
        if (!committed) {
            throw new IllegalStateException("Commit probe row was not persisted");
        }

        this.database.update(connection -> deleteProbe(connection, table, key)).join();
    }

    private TransactionOptions retryingTransactionOptions() {
        TransactionRetryPolicy retryPolicy = TransactionRetryPolicy.builder()
            .maxAttempts(3)
            .initialDelayMillis(25)
            .maxDelayMillis(250)
            .multiplier(2.0)
            .jitterFactor(0.25)
            .classifier(com.hera.craftkit.database.SqlRetryClassifier.mysqlTransient())
            .listener(event -> this.plugin.getLogger().warning(
                "Retrying DB transaction after attempt " + event.failedAttempt()
                    + "/" + event.maxAttempts()
                    + " in " + event.nextDelayMillis()
                    + " ms. SQLState=" + event.failure().getSQLState()
                    + ", code=" + event.failure().getErrorCode()
            ))
            .build();

        return TransactionOptions.builder()
            .isolation(TransactionIsolation.READ_COMMITTED)
            .retryPolicy(retryPolicy)
            .build();
    }

    private void startRedis(long currentGeneration) {
        long started = System.nanoTime();
        RedisConfig redisConfig = this.configuration.redis().toRedisConfig(this.configuration.serverId());
        this.redis = RedisClients.lettuce(redisConfig, RedisStartupMode.RECOVER);
        this.redisStatusRegistration = this.redis.observeOperationalStatus(status -> {
            if (this.generation.get() != currentGeneration || this.state.get() == LifecycleState.STOPPING) {
                return;
            }
            this.plugin.getLogger().info("Redis status changed: " + status.state()
                + " command=" + status.commandConnection()
                + " pubsub=" + status.pubSubConnection()
                + " subscriptions=" + status.activeSubscriptions() + "/" + status.requestedSubscriptions());
        });

        String token = UUID.randomUUID().toString();
        String channel = this.redis.channel("networkboosters", "infrastructure-probe");
        this.redisProbeSubscription = this.redis.subscriber().subscribe(channel, message -> {
            if (this.generation.get() == currentGeneration && token.equals(message.payload())) {
                this.plugin.getLogger().fine("Redis infrastructure probe received");
            }
        });

        this.redisProbeSubscription.initialRegistration().thenCompose(ignored ->
            this.redis.publisher().publish(channel, token)
        ).whenComplete((subscribers, throwable) -> {
            if (this.generation.get() != currentGeneration || this.state.get() == LifecycleState.STOPPING) {
                return;
            }
            if (throwable != null) {
                this.plugin.getLogger().log(Level.WARNING, "Redis probe is degraded; MySQL-backed features remain allowed", throwable);
                return;
            }
            this.plugin.getLogger().info("Redis probe published to " + subscribers + " subscriber(s)");
        });

        this.plugin.getLogger().info("Redis client created in " + elapsedMillis(started) + " ms with state "
            + this.redis.operationalStatus().state());
    }

    private void startZMenu() {
        long started = System.nanoTime();
        this.zmenu = ZMenus.require(this.plugin);
        this.zmenu.bootstrap()
            .patterns("patterns")
            .inventories("inventories")
            .load();
        this.zmenu.reload();
        this.plugin.getLogger().info("zMenu integration loaded and reload verified in " + elapsedMillis(started) + " ms");
    }

    private void processAlreadyOnlinePlayers() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (this.playerSettings.isReady(player.getUniqueId())) {
                this.plugin.getLogger().fine(() -> "Existing online player already settings-ready: " + player.getUniqueId());
            }
        }
    }

    private void closeStartedResources() {
        HandlerList.unregisterAll(this);
        this.plugin.getServer().getScheduler().cancelTasks(this.plugin);

        RuntimeException closeFailure = null;
        closeFailure = close("Redis probe subscription", closeFailure, () -> {
            if (this.redisProbeSubscription != null) {
                this.redisProbeSubscription.close();
                this.redisProbeSubscription = null;
            }
        });
        closeFailure = close("Redis status observer", closeFailure, () -> {
            if (this.redisStatusRegistration != null) {
                this.redisStatusRegistration.close();
                this.redisStatusRegistration = null;
            }
        });
        closeFailure = close("Redis", closeFailure, () -> {
            if (this.redis != null) {
                this.redis.close();
                this.redis = null;
            }
        });
        closeFailure = close("Database", closeFailure, () -> {
            if (this.database != null) {
                this.database.close();
                this.database = null;
            }
        });

        this.zmenu = null;
        this.playerSettings = null;
        this.configuration = null;

        if (closeFailure != null) {
            this.plugin.getLogger().log(Level.SEVERE, "One or more resources failed to close", closeFailure);
        }
    }

    private RuntimeException close(String resourceName, RuntimeException previous, ThrowingRunnable closeOperation) {
        try {
            closeOperation.run();
            return previous;
        } catch (Exception exception) {
            RuntimeException failure = new RuntimeException("Failed to close " + resourceName, exception);
            if (previous != null) {
                previous.addSuppressed(failure);
                return previous;
            }
            return failure;
        }
    }

    private static void insertProbe(Connection connection, String table, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + table + " (key_name, value_text) VALUES (?, ?)"
        )) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private static boolean probeExists(Connection connection, String table, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT key_name FROM " + table + " WHERE key_name = ? FOR UPDATE"
        )) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static int deleteProbe(Connection connection, String table, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM " + table + " WHERE key_name = ?"
        )) {
            statement.setString(1, key);
            return statement.executeUpdate();
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
