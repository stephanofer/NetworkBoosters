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
import com.stephanofer.networkboosters.config.ConfigurationLoader;
import com.stephanofer.networkboosters.config.ConfigurationSnapshot;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import com.stephanofer.networkplayersettings.settings.event.PlayerSettingsReadyEvent;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.incendo.cloud.paper.PaperCommandManager;

public final class NetworkBoostersLifecycle implements Listener {

    private static final String METADATA_TABLE = "metadata";
    private static final String PROBE_KEY_PREFIX = "__infrastructure_probe_";

    private final NetworkBoostersPlugin plugin;
    private final ComponentLogger logger;
    private final PaperCommandManager.Bootstrapped<CommandSourceStack> commandManager;
    private final AtomicReference<LifecycleState> state = new AtomicReference<>(LifecycleState.NEW);
    private final AtomicLong generation = new AtomicLong();
    private final ConfigurationStore configurationStore = new ConfigurationStore();

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
        this.logger = plugin.getComponentLogger();
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
        this.logger.info("Starting NetworkBoosters {}", this.plugin.getPluginMeta().getVersion());

        try {
            this.commandManager.onEnable();
            this.loadConfiguration();
            this.playerSettings = this.resolvePlayerSettings();
            this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
            this.startDatabase();
            this.startRedis(currentGeneration);
            this.startZMenu();
            this.processAlreadyOnlinePlayers();

            this.state.set(LifecycleState.RUNNING);
            this.logger.info("NetworkBoosters enabled in {} ms", elapsedMillis(started));
        } catch (Throwable throwable) {
            this.state.set(LifecycleState.FAILED);
            this.logger.error("NetworkBoosters failed during startup", throwable);
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
        this.logger.info("NetworkBoosters stopped in {} ms", elapsedMillis(started));
    }

    @EventHandler
    public void onPlayerSettingsReady(PlayerSettingsReadyEvent event) {
        LifecycleState currentState = this.state.get();
        if (currentState != LifecycleState.STARTING && currentState != LifecycleState.RUNNING) {
            return;
        }

        Player player = event.player();
        this.logger.info("Player settings ready for {} with language {}", player.getUniqueId(), event.resolvedLanguage().code());
    }

    private void loadConfiguration() {
        long started = System.nanoTime();
        ConfigurationLoader loader = new ConfigurationLoader(this.plugin.getDataFolder(), this.plugin::getResource);
        ConfigurationSnapshot snapshot = this.configurationStore.initialize(loader.load());
        this.logger.info(
            "Configuration loaded in {} ms with {} booster definition(s)",
            elapsedMillis(started),
            snapshot.definitions().size()
        );
        snapshot.warnings().forEach(issue -> this.logger.warn("{}:{} - {}", issue.file(), issue.path(), issue.message()));
        if (!snapshot.definitionChanges().added().isEmpty()) {
            this.logger.info("Loaded new booster definitions: {}", snapshot.definitionChanges().added());
        }
    }

    private PlayerSettingsService resolvePlayerSettings() {
        PlayerSettingsService service = this.plugin.getServer().getServicesManager().load(PlayerSettingsService.class);
        if (service == null) {
            throw new IllegalStateException("NetworkPlayerSettings is loaded but PlayerSettingsService is not registered");
        }
        this.logger.info("NetworkPlayerSettings service resolved");
        return service;
    }

    private void startDatabase() {
        long started = System.nanoTime();
        DatabaseConfig databaseConfig = this.configuration().storage().toDatabaseConfig(this.plugin.getClass().getClassLoader());
        this.database = Databases.mysql(databaseConfig);
        this.database.migrate().join();
        this.runDatabaseProbe();
        this.logger.info("Database connected, migrated and verified in {} ms", elapsedMillis(started));
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
            .listener(event -> this.logger.warn(
                "Retrying DB transaction after attempt {}/{} in {} ms. SQLState={}, code={}",
                event.failedAttempt(),
                event.maxAttempts(),
                event.nextDelayMillis(),
                event.failure().getSQLState(),
                event.failure().getErrorCode()
            ))
            .build();

        return TransactionOptions.builder()
            .isolation(TransactionIsolation.READ_COMMITTED)
            .retryPolicy(retryPolicy)
            .build();
    }

    private void startRedis(long currentGeneration) {
        if (!this.configuration().redis().enabled()) {
            this.logger.warn("Redis is disabled in config.yml; cross-server invalidation will be unavailable");
            return;
        }
        long started = System.nanoTime();
        RedisConfig redisConfig = this.configuration().redis().toRedisConfig(this.configuration().serverId());
        this.redis = RedisClients.lettuce(redisConfig, RedisStartupMode.RECOVER);
        this.redisStatusRegistration = this.redis.observeOperationalStatus(status -> {
            if (this.generation.get() != currentGeneration || this.state.get() == LifecycleState.STOPPING) {
                return;
            }
            this.logger.info(
                "Redis status changed: {} command={} pubsub={} subscriptions={}/{}",
                status.state(),
                status.commandConnection(),
                status.pubSubConnection(),
                status.activeSubscriptions(),
                status.requestedSubscriptions()
            );
        });

        String token = UUID.randomUUID().toString();
        String channel = this.redis.channel("networkboosters", "infrastructure-probe");
        this.redisProbeSubscription = this.redis.subscriber().subscribe(channel, message -> {
            if (this.generation.get() == currentGeneration && token.equals(message.payload())) {
                this.logger.info("Redis infrastructure probe received");
            }
        });

        this.redisProbeSubscription.initialRegistration().thenCompose(ignored ->
            this.redis.publisher().publish(channel, token)
        ).whenComplete((subscribers, throwable) -> {
            if (this.generation.get() != currentGeneration || this.state.get() == LifecycleState.STOPPING) {
                return;
            }
            if (throwable != null) {
                this.logger.warn("Redis probe is degraded; MySQL-backed features remain allowed", throwable);
                return;
            }
            this.logger.info("Redis probe published to {} subscriber(s)", subscribers);
        });

        this.logger.info(
            "Redis client created in {} ms with state {}",
            elapsedMillis(started),
            this.redis.operationalStatus().state()
        );
    }

    private void startZMenu() {
        long started = System.nanoTime();
        this.zmenu = ZMenus.require(this.plugin);
        this.zmenu.bootstrap()
            .patterns("patterns")
            .inventories("inventories")
            .load();
        this.zmenu.reload();
        this.logger.info("zMenu integration loaded and reload verified in {} ms", elapsedMillis(started));
    }

    private void processAlreadyOnlinePlayers() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (this.playerSettings.isReady(player.getUniqueId())) {
                this.logger.info("Existing online player already settings-ready: {}", player.getUniqueId());
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
        this.configurationStore.clear();

        if (closeFailure != null) {
            this.logger.error("One or more resources failed to close", closeFailure);
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

    private NetworkBoostersConfiguration configuration() {
        return this.configurationStore.requireCurrent().configuration();
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
