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
import com.stephanofer.networkboosters.api.NetworkBoostersService;
import com.stephanofer.networkboosters.NetworkBoostersPlugin;
import com.stephanofer.networkboosters.booster.ActivationMutationService;
import com.stephanofer.networkboosters.booster.ExpirationCoordinator;
import com.stephanofer.networkboosters.booster.PlayerPermissionProvider;
import com.stephanofer.networkboosters.calculation.BoostCalculator;
import com.stephanofer.networkboosters.capacity.InventoryCapacityResolver;
import com.stephanofer.networkboosters.capacity.ResolvedInventoryCapacity;
import com.stephanofer.networkboosters.command.NetworkBoostersCommandBridge;
import com.stephanofer.networkboosters.command.NetworkBoostersCommandRuntime;
import com.stephanofer.networkboosters.config.ConfigurationLoader;
import com.stephanofer.networkboosters.config.ConfigurationSnapshot;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import com.stephanofer.networkboosters.localization.LocalizationService;
import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import com.stephanofer.networkboosters.menu.loader.ClaimsButtonLoader;
import com.stephanofer.networkboosters.menu.loader.OwnedBoostersButtonLoader;
import com.stephanofer.networkboosters.menu.loader.SimpleMenuButtonLoader;
import com.stephanofer.networkboosters.menu.loader.TransferTargetsButtonLoader;
import com.stephanofer.networkboosters.placeholder.NetworkBoostersPlaceholderExpansion;
import com.stephanofer.networkboosters.event.BoosterEventDispatcher;
import com.stephanofer.networkboosters.inventory.InventoryMutationService;
import com.stephanofer.networkboosters.persistence.BoosterStorage;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import com.stephanofer.networkboosters.player.PlayerStateLoader;
import com.stephanofer.networkboosters.service.NetworkBoostersServiceImpl;
import com.stephanofer.networkboosters.synchronization.BoosterInvalidationCodec;
import com.stephanofer.networkboosters.synchronization.BoosterInvalidationPublisher;
import com.stephanofer.networkboosters.synchronization.BoosterInvalidationSubscriber;
import com.stephanofer.networkboosters.synchronization.DelegatingPostCommitSynchronizer;
import com.stephanofer.networkboosters.synchronization.PlayerInvalidationCoordinator;
import com.stephanofer.networkboosters.synchronization.PlayerRevisionReader;
import com.stephanofer.networkboosters.synchronization.RedisSynchronizationService;
import com.stephanofer.networkboosters.synchronization.RevisionReconciliationCoordinator;
import com.stephanofer.networkboosters.transfer.BoosterTransferService;
import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import com.stephanofer.networkplayersettings.settings.event.PlayerSettingsReadyEvent;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.incendo.cloud.paper.PaperCommandManager;

public final class NetworkBoostersLifecycle implements Listener, NetworkBoostersCommandRuntime {

    private static final String METADATA_TABLE = "metadata";
    private static final String PROBE_KEY_PREFIX = "__infrastructure_probe_";

    private final NetworkBoostersPlugin plugin;
    private final ComponentLogger logger;
    private final PaperCommandManager.Bootstrapped<CommandSourceStack> commandManager;
    private final NetworkBoostersCommandBridge commandBridge;
    private final AtomicReference<LifecycleState> state = new AtomicReference<>(LifecycleState.NEW);
    private final AtomicBoolean reloadInProgress = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final ConfigurationStore configurationStore = new ConfigurationStore();
    private final Clock clock = Clock.systemUTC();

    private PlayerSettingsService playerSettings;
    private LocalizationService localizationService;
    private Database database;
    private BoosterStorage boosterStorage;
    private PlayerSnapshotCache playerSnapshotCache;
    private ActivationMutationService activationMutationService;
    private BoosterTransferService transferService;
    private ExpirationCoordinator expirationCoordinator;
    private NetworkBoostersServiceImpl boostersService;
    private BoosterEventDispatcher eventDispatcher;
    private DelegatingPostCommitSynchronizer postCommitSynchronizer;
    private PlayerInvalidationCoordinator invalidationCoordinator;
    private BoosterInvalidationSubscriber invalidationSubscriber;
    private RevisionReconciliationCoordinator reconciliationCoordinator;
    private NetworkBoostersPlaceholderExpansion placeholderExpansion;
    private NetworkBoostersMenuCoordinator menuCoordinator;
    private RedisClient redis;
    private RedisStatusRegistration redisStatusRegistration;
    private RedisSubscription redisProbeSubscription;
    private ZMenuIntegration zmenu;

    public NetworkBoostersLifecycle(
        NetworkBoostersPlugin plugin,
        PaperCommandManager.Bootstrapped<CommandSourceStack> commandManager,
        NetworkBoostersCommandBridge commandBridge
    ) {
        this.plugin = plugin;
        this.logger = plugin.getComponentLogger();
        this.commandManager = commandManager;
        this.commandBridge = commandBridge;
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
            this.localizationService = new LocalizationService(this.configurationStore, this.playerSettings);
            this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
            this.startDatabase();
            this.startPlayerState();
            this.startRedis(currentGeneration);
            this.startZMenu();
            this.startPlaceholderApi();
            this.processAlreadyOnlinePlayers();

            this.commandBridge.bind(this);
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
        this.loadPlayerSnapshot(player.getUniqueId(), this.generation.get());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (this.playerSnapshotCache != null) {
            this.playerSnapshotCache.unload(event.getPlayer().getUniqueId());
        }
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

    @Override
    public NetworkBoostersPlugin plugin() {
        return this.plugin;
    }

    @Override
    public org.bukkit.Server server() {
        return this.plugin.getServer();
    }

    @Override
    public NetworkBoostersService service() {
        return this.boostersService;
    }

    @Override
    public ConfigurationStore configurationStore() {
        return this.configurationStore;
    }

    @Override
    public LocalizationService localization() {
        return this.localizationService;
    }

    @Override
    public PlayerSettingsService playerSettings() {
        return this.playerSettings;
    }

    @Override
    public Clock clock() {
        return this.clock;
    }

    @Override
    public ResolvedInventoryCapacity capacity(Player player) {
        return new InventoryCapacityResolver().resolve(
            this.configuration().inventoryLimits().fallback(),
            this.configuration().inventoryLimits().tiers(),
            player::hasPermission
        );
    }

    @Override
    public CompletableFuture<Long> persistedRevision(UUID playerId) {
        BoosterStorage storage = this.boosterStorage;
        if (storage == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Storage is not available"));
        }
        return storage.revisions(List.of(playerId)).thenApply(revisions -> revisions.getOrDefault(playerId, 0L));
    }

    @Override
    public CompletableFuture<ReloadReport> reload() {
        if (this.state.get() != LifecycleState.RUNNING) {
            return CompletableFuture.completedFuture(new ReloadReport(false, false, "Lifecycle is not running", 0, 0));
        }
        if (!this.reloadInProgress.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(new ReloadReport(false, false, "A reload is already in progress", 0, 0));
        }
        return CompletableFuture.supplyAsync(this::reloadNow)
            .whenComplete((ignored, failure) -> this.reloadInProgress.set(false));
    }

    private ReloadReport reloadNow() {
        try {
            ConfigurationLoader loader = new ConfigurationLoader(this.plugin.getDataFolder(), this.plugin::getResource);
            ConfigurationSnapshot candidate = loader.load(this.configurationStore.requireCurrent());
            if (this.state.get() != LifecycleState.RUNNING) {
                return new ReloadReport(false, false, "Lifecycle stopped during reload", 0, 0);
            }
            if (candidate.configurationChanges().requiresRestart()) {
                return new ReloadReport(
                    false,
                    true,
                    String.join(", ", candidate.configurationChanges().restartRequiredPaths()),
                    candidate.definitions().size(),
                    candidate.warnings().size()
                );
            }
            ConfigurationSnapshot published = this.configurationStore.replace(candidate);
            published.warnings().forEach(issue -> this.logger.warn("{}:{} - {}", issue.file(), issue.path(), issue.message()));
            this.logger.info(
                "NetworkBoosters reloaded with {} booster definition(s) and {} warning(s)",
                published.definitions().size(),
                published.warnings().size()
            );
            return new ReloadReport(true, false, "", published.definitions().size(), published.warnings().size());
        } catch (IllegalArgumentException exception) {
            return new ReloadReport(false, true, exception.getMessage(), 0, 0);
        } catch (Throwable throwable) {
            this.logger.warn("NetworkBoosters reload failed; previous configuration remains active", throwable);
            return new ReloadReport(false, false, throwable.getMessage(), 0, 0);
        }
    }

    @Override
    public String redisStatus() {
        if (!this.configuration().redis().enabled()) {
            return "disabled";
        }
        if (this.redis == null) {
            return "unavailable";
        }
        return this.redis.operationalStatus().state().name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean openMenu(Player player) {
        if (this.menuCoordinator == null || this.state.get() != LifecycleState.RUNNING) {
            return false;
        }
        this.menuCoordinator.openMain(player);
        return true;
    }

    @Override
    public boolean isRunning() {
        return this.state.get() == LifecycleState.RUNNING
            && this.boostersService != null
            && this.localizationService != null
            && this.playerSettings != null;
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
        this.boosterStorage = new BoosterStorage(this.database, event -> this.logger.warn(
            "Retrying NetworkBoosters DB transaction after attempt {}/{} in {} ms. SQLState={}, code={}",
            event.failedAttempt(),
            event.maxAttempts(),
            event.nextDelayMillis(),
            event.failure().getSQLState(),
            event.failure().getErrorCode()
        ));
        this.logger.info("Database connected, migrated and verified in {} ms", elapsedMillis(started));
    }

    private void startPlayerState() {
        long started = System.nanoTime();
        PlayerStateLoader stateLoader = new PlayerStateLoader();
        this.playerSnapshotCache = new PlayerSnapshotCache(stateLoader);
        this.eventDispatcher = new BoosterEventDispatcher(this.plugin, this.logger, this.configuration().serverId());
        this.postCommitSynchronizer = new DelegatingPostCommitSynchronizer();
        this.activationMutationService = new ActivationMutationService(
            this.boosterStorage,
            this.playerSnapshotCache,
            this.configurationStore,
            new PlayerPermissionProvider(this.plugin.getServer(), this.plugin),
            this.eventDispatcher,
            this.postCommitSynchronizer
        );
        this.transferService = new BoosterTransferService(
            this.boosterStorage,
            this.playerSnapshotCache,
            this.configurationStore,
            this.plugin.getServer(),
            this.plugin,
            this.postCommitSynchronizer
        );
        stateLoader.initializeReconciler(this.activationMutationService::reconcilePlayerState);
        this.boostersService = new NetworkBoostersServiceImpl(
            this.configurationStore,
            this.playerSnapshotCache,
            new BoostCalculator(),
            this.activationMutationService,
            new InventoryMutationService(this.boosterStorage, this.playerSnapshotCache, this.configurationStore, this.plugin.getServer(), this.postCommitSynchronizer),
            this.transferService,
            this.clock
        );
        this.plugin.getServer().getServicesManager().register(
            NetworkBoostersService.class,
            this.boostersService,
            this.plugin,
            ServicePriority.Normal
        );
        this.expirationCoordinator = new ExpirationCoordinator(
            this.plugin,
            this.logger,
            this.configurationStore,
            this.activationMutationService
        );
        this.expirationCoordinator.start();
        this.logger.info("NetworkBoosters service registered in {} ms", elapsedMillis(started));
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
        this.invalidationCoordinator = new PlayerInvalidationCoordinator(this.playerSnapshotCache, this.eventDispatcher, this.logger);
        this.reconciliationCoordinator = new RevisionReconciliationCoordinator(
            this.plugin,
            this.playerSnapshotCache,
            new PlayerRevisionReader(this.boosterStorage),
            this.invalidationCoordinator,
            this.configurationStore,
            this.logger,
            this.configuration().serverId()
        );
        if (!this.configuration().redis().enabled()) {
            this.logger.warn("Redis is disabled in config.yml; cross-server invalidation will be unavailable");
            this.postCommitSynchronizer.setDelegate(new RedisSynchronizationService(
                this.playerSnapshotCache,
                this.eventDispatcher,
                null,
                this.configuration().serverId()
            ));
            this.reconciliationCoordinator.start();
            this.reconciliationCoordinator.observe(null);
            return;
        }
        long started = System.nanoTime();
        RedisConfig redisConfig = this.configuration().redis().toRedisConfig(this.configuration().serverId());
        this.redis = RedisClients.lettuce(redisConfig, RedisStartupMode.RECOVER);
        BoosterInvalidationCodec invalidationCodec = new BoosterInvalidationCodec();
        String invalidationChannel = this.redis.channel("networkboosters", "player-state-changed");
        BoosterInvalidationPublisher invalidationPublisher = new BoosterInvalidationPublisher(
            this.redis,
            invalidationChannel,
            this.configuration().serverId(),
            Clock.systemUTC(),
            invalidationCodec,
            this.logger
        );
        this.invalidationSubscriber = new BoosterInvalidationSubscriber(
            this.redis,
            invalidationChannel,
            this.configuration().serverId(),
            invalidationCodec,
            this.invalidationCoordinator,
            this.logger
        );
        this.postCommitSynchronizer.setDelegate(new RedisSynchronizationService(
            this.playerSnapshotCache,
            this.eventDispatcher,
            invalidationPublisher,
            this.configuration().serverId()
        ));
        this.reconciliationCoordinator.start();
        this.reconciliationCoordinator.observe(this.redis);
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
        this.menuCoordinator = new NetworkBoostersMenuCoordinator(this, this.zmenu);
        this.zmenu.bootstrap()
            .buttons(registry -> {
                registry.button(new OwnedBoostersButtonLoader(this.plugin, this.menuCoordinator));
                registry.button(new TransferTargetsButtonLoader(this.plugin, this.menuCoordinator));
                registry.button(new ClaimsButtonLoader(this.plugin, this.menuCoordinator));
                registry.button(SimpleMenuButtonLoader.summary(this.plugin, this.menuCoordinator));
                registry.button(SimpleMenuButtonLoader.filter(this.plugin, this.menuCoordinator));
                registry.button(SimpleMenuButtonLoader.sort(this.plugin, this.menuCoordinator));
                registry.button(SimpleMenuButtonLoader.openClaims(this.plugin, this.menuCoordinator));
                registry.button(SimpleMenuButtonLoader.activationPreview(this.plugin, this.menuCoordinator));
                registry.button(SimpleMenuButtonLoader.activationConfirm(this.plugin, this.menuCoordinator));
                registry.button(SimpleMenuButtonLoader.transferPreview(this.plugin, this.menuCoordinator));
                registry.button(SimpleMenuButtonLoader.transferAmount(this.plugin, this.menuCoordinator));
                registry.button(SimpleMenuButtonLoader.transferConfirm(this.plugin, this.menuCoordinator));
            })
            .defaultInventories(
                "inventories/boosters.yml",
                "inventories/booster-confirm.yml",
                "inventories/booster-transfer-target.yml",
                "inventories/booster-transfer.yml",
                "inventories/booster-claims.yml"
            )
            .defaultPatterns("patterns/pagination.yml")
            .patterns("patterns")
            .inventories("inventories")
            .load();
        this.plugin.getServer().getPluginManager().registerEvents(this.menuCoordinator, this.plugin);
        this.logger.info("zMenu integration loaded and reload verified in {} ms", elapsedMillis(started));
    }

    private void startPlaceholderApi() {
        if (!this.configuration().placeholderApi().enabled()) {
            this.logger.info("PlaceholderAPI integration is disabled in config.yml");
            return;
        }
        if (!this.plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.logger.info("PlaceholderAPI is not installed or not enabled; skipping NetworkBoosters expansion");
            return;
        }
        this.placeholderExpansion = new NetworkBoostersPlaceholderExpansion(this);
        if (this.placeholderExpansion.register()) {
            this.logger.info("PlaceholderAPI expansion registered");
        } else {
            this.logger.warn("PlaceholderAPI expansion registration returned false");
            this.placeholderExpansion = null;
        }
    }

    private void processAlreadyOnlinePlayers() {
        long currentGeneration = this.generation.get();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (this.playerSettings.isReady(player.getUniqueId())) {
                this.logger.info("Existing online player already settings-ready: {}", player.getUniqueId());
                this.loadPlayerSnapshot(player.getUniqueId(), currentGeneration);
            }
        }
    }

    private void loadPlayerSnapshot(UUID playerId, long capturedGeneration) {
        PlayerSnapshotCache cache = this.playerSnapshotCache;
        if (cache == null || this.state.get() == LifecycleState.STOPPING) {
            return;
        }
        cache.load(playerId).whenComplete((snapshot, failure) -> {
            if (this.generation.get() != capturedGeneration || this.state.get() == LifecycleState.STOPPING) {
                return;
            }
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (this.generation.get() != capturedGeneration || this.state.get() == LifecycleState.STOPPING) {
                    return;
                }
                if (failure != null) {
                    this.logger.error("Failed to load boosters snapshot for {}", playerId, failure);
                    return;
                }
                Player player = this.plugin.getServer().getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    this.logger.info("Boosters snapshot ready for {} at revision {}", playerId, snapshot.revision());
                    if (this.eventDispatcher != null) {
                        this.eventDispatcher.callReady(player, snapshot);
                    }
                }
            });
        });
    }

    private void closeStartedResources() {
        HandlerList.unregisterAll(this);
        this.plugin.getServer().getScheduler().cancelTasks(this.plugin);

        RuntimeException closeFailure = null;
        closeFailure = close("NetworkBoosters service", closeFailure, () -> {
            this.commandBridge.clear(this);
            if (this.placeholderExpansion != null) {
                this.placeholderExpansion.unregister();
                this.placeholderExpansion = null;
            }
            if (this.boostersService != null) {
                this.plugin.getServer().getServicesManager().unregister(NetworkBoostersService.class, this.boostersService);
                this.boostersService = null;
            }
        });
        closeFailure = close("NetworkBoosters expiration coordinator", closeFailure, () -> {
            if (this.expirationCoordinator != null) {
                this.expirationCoordinator.close();
                this.expirationCoordinator = null;
            }
        });
        closeFailure = close("NetworkBoosters reconciliation coordinator", closeFailure, () -> {
            if (this.reconciliationCoordinator != null) {
                this.reconciliationCoordinator.close();
                this.reconciliationCoordinator = null;
            }
        });
        closeFailure = close("NetworkBoosters invalidation subscriber", closeFailure, () -> {
            if (this.invalidationSubscriber != null) {
                this.invalidationSubscriber.close();
                this.invalidationSubscriber = null;
            }
        });
        closeFailure = close("NetworkBoosters invalidation coordinator", closeFailure, () -> {
            if (this.invalidationCoordinator != null) {
                this.invalidationCoordinator.close();
                this.invalidationCoordinator = null;
            }
        });
        closeFailure = close("NetworkBoosters event dispatcher", closeFailure, () -> {
            if (this.eventDispatcher != null) {
                this.eventDispatcher.close();
                this.eventDispatcher = null;
            }
        });
        closeFailure = close("NetworkBoosters activation mutations", closeFailure, () -> {
            if (this.activationMutationService != null) {
                this.activationMutationService.close();
                this.activationMutationService = null;
            }
        });
        closeFailure = close("NetworkBoosters transfer service", closeFailure, () -> {
            if (this.transferService != null) {
                this.transferService.close();
                this.transferService = null;
            }
        });
        closeFailure = close("NetworkBoosters menu coordinator", closeFailure, () -> {
            if (this.menuCoordinator != null) {
                HandlerList.unregisterAll(this.menuCoordinator);
                this.menuCoordinator.close();
                this.menuCoordinator = null;
            }
        });
        closeFailure = close("Player snapshot cache", closeFailure, () -> {
            if (this.playerSnapshotCache != null) {
                this.playerSnapshotCache.close();
                this.playerSnapshotCache = null;
            }
        });
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

        this.boosterStorage = null;
        this.zmenu = null;
        this.localizationService = null;
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
