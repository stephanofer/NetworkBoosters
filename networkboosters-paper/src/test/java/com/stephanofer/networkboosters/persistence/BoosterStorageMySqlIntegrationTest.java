package com.stephanofer.networkboosters.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.Databases;
import com.hera.craftkit.database.MigrationConfig;
import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterCategory;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.booster.TransferPolicy;
import com.stephanofer.networkboosters.api.request.InventoryGrantRequest;
import com.stephanofer.networkboosters.api.request.ActivationRequest;
import com.stephanofer.networkboosters.api.result.ActivationStatus;
import com.stephanofer.networkboosters.api.result.InventoryMutationStatus;
import com.stephanofer.networkboosters.api.result.TransferStatus;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.MutationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.api.source.TransferSource;
import com.stephanofer.networkboosters.config.ConfigurationSnapshot;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import com.stephanofer.networkboosters.config.booster.BoosterDefinitionRegistry;
import com.stephanofer.networkboosters.booster.ActivationMutationService;
import com.stephanofer.networkboosters.booster.PlayerPermissionProvider;
import com.stephanofer.networkboosters.event.BoosterEventDispatcher;
import com.stephanofer.networkboosters.inventory.InventoryMutationService;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import com.stephanofer.networkboosters.synchronization.PostCommitSynchronizer;
import com.stephanofer.networkboosters.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.bukkit.Server;

@EnabledIfEnvironmentVariable(named = "NETWORKBOOSTERS_TEST_MYSQL_HOST", matches = ".+")
class BoosterStorageMySqlIntegrationTest {

    private static Database database;
    private static BoosterStorage storage;

    @BeforeAll
    static void startDatabase() {
        database = Databases.mysql(DatabaseConfig.builder()
            .host(requiredEnvironment("NETWORKBOOSTERS_TEST_MYSQL_HOST"))
            .port(Integer.parseInt(requiredEnvironment("NETWORKBOOSTERS_TEST_MYSQL_PORT")))
            .database(requiredEnvironment("NETWORKBOOSTERS_TEST_MYSQL_DATABASE"))
            .username(requiredEnvironment("NETWORKBOOSTERS_TEST_MYSQL_USERNAME"))
            .password(requiredEnvironment("NETWORKBOOSTERS_TEST_MYSQL_PASSWORD"))
            .tablePrefix("networkboosters_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "_")
            .migration(MigrationConfig.builder()
                .classLoader(BoosterStorageMySqlIntegrationTest.class.getClassLoader())
                .build())
            .build());
        database.migrate().join();
        storage = new BoosterStorage(database, ignored -> {
        });
    }

    @AfterAll
    static void stopDatabase() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void onlyOneConcurrentMutationCanConsumeTheLastInventoryUnit() {
        UUID playerId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        storage.write(connection -> {
            storage.inventory().add(connection, playerId, boosterId, 1);
            return null;
        }).join();

        CompletableFuture<Boolean> first = storage.write(connection -> storage.inventory().decrementOne(connection, playerId, boosterId));
        CompletableFuture<Boolean> second = storage.write(connection -> storage.inventory().decrementOne(connection, playerId, boosterId));

        boolean firstResult = first.join();
        boolean secondResult = second.join();
        assertTrue(firstResult ^ secondResult);
        assertEquals(0, storage.loadSnapshot(playerId).join().ownedAmount(boosterId));
    }

    @Test
    void decrementDeletesTheRowWhenConsumingTheCompleteAmount() {
        UUID playerId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        storage.write(connection -> {
            storage.inventory().add(connection, playerId, boosterId, 3);
            assertTrue(storage.inventory().decrement(connection, playerId, boosterId, 3));
            assertTrue(storage.inventory().amount(connection, playerId, boosterId).isEmpty());
            return null;
        }).join();

        assertEquals(0, storage.loadSnapshot(playerId).join().ownedAmount(boosterId));
    }

    @Test
    void decrementKeepsAPositiveRowForPartialConsumption() {
        UUID playerId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        storage.write(connection -> {
            storage.inventory().add(connection, playerId, boosterId, 3);
            assertTrue(storage.inventory().decrement(connection, playerId, boosterId, 2));
            assertEquals(1, storage.inventory().amount(connection, playerId, boosterId).orElseThrow());
            return null;
        }).join();
    }

    @Test
    void decrementDoesNotChangeInsufficientInventory() {
        UUID playerId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        storage.write(connection -> {
            storage.inventory().add(connection, playerId, boosterId, 2);
            assertFalse(storage.inventory().decrement(connection, playerId, boosterId, 3));
            assertEquals(2, storage.inventory().amount(connection, playerId, boosterId).orElseThrow());
            return null;
        }).join();
    }

    @Test
    void twoConcurrentMutationsCanConsumeTwoInventoryUnits() {
        UUID playerId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        storage.write(connection -> {
            storage.inventory().add(connection, playerId, boosterId, 2);
            return null;
        }).join();

        CompletableFuture<Boolean> first = storage.write(connection -> storage.inventory().decrementOne(connection, playerId, boosterId));
        CompletableFuture<Boolean> second = storage.write(connection -> storage.inventory().decrementOne(connection, playerId, boosterId));

        assertTrue(first.join());
        assertTrue(second.join());
        assertEquals(0, storage.loadSnapshot(playerId).join().ownedAmount(boosterId));
    }

    @Test
    void inventoryCanTransferAmountAtomicallyInsideOneTransaction() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");

        storage.write(connection -> {
            storage.inventory().add(connection, senderId, boosterId, 5);
            long senderBefore = storage.inventory().amountForUpdate(connection, senderId, boosterId).orElseThrow();
            long recipientBefore = storage.inventory().amountForUpdate(connection, recipientId, boosterId).orElse(0L);

            assertTrue(storage.inventory().decrement(connection, senderId, boosterId, 3));
            storage.inventory().add(connection, recipientId, boosterId, 3);

            UUID transferId = UUID.randomUUID();
            Instant createdAt = Instant.now();
            storage.transfers().insert(connection, new TransferRepository.StoredTransfer(
                transferId,
                senderId,
                recipientId,
                boosterId,
                3,
                TransferSource.PLAYER_COMMAND,
                SourceReference.actor(senderId),
                createdAt,
                TransferStatus.TRANSFERRED
            ));

            assertEquals(senderBefore - 3, storage.inventory().amount(connection, senderId, boosterId).orElseThrow());
            assertEquals(recipientBefore + 3, storage.inventory().amount(connection, recipientId, boosterId).orElseThrow());
            assertTrue(storage.transfers().latestSuccessfulTransferAt(connection, senderId, boosterId).orElseThrow()
                .isAfter(createdAt.minus(Duration.ofSeconds(1))));
            return null;
        }).join();

        assertEquals(2, storage.loadSnapshot(senderId).join().ownedAmount(boosterId));
        assertEquals(3, storage.loadSnapshot(recipientId).join().ownedAmount(boosterId));
    }

    @Test
    void inventoryCanTransferTheCompleteAmountWithoutPersistingZero() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");

        storage.write(connection -> {
            storage.inventory().add(connection, senderId, boosterId, 1);
            assertTrue(storage.inventory().decrementOne(connection, senderId, boosterId));
            storage.inventory().add(connection, recipientId, boosterId, 1);
            assertTrue(storage.inventory().amount(connection, senderId, boosterId).isEmpty());
            return null;
        }).join();

        assertEquals(0, storage.loadSnapshot(senderId).join().ownedAmount(boosterId));
        assertEquals(1, storage.loadSnapshot(recipientId).join().ownedAmount(boosterId));
    }

    @Test
    void activationConsumesTheOnlyInventoryUnit() {
        UUID playerId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        storage.write(connection -> {
            storage.inventory().add(connection, playerId, boosterId, 1);
            return null;
        }).join();
        ActivationMutationService service = activationService(playerId, configurationStore(boosterId));

        var result = service.activate(new ActivationRequest(
            playerId,
            boosterId,
            ActivationSource.PLAYER_COMMAND,
            SourceReference.actor(playerId)
        )).join();

        assertEquals(ActivationStatus.ACTIVATED, result.status());
        assertEquals(0, result.remainingInventoryAmount());
        var snapshot = storage.loadSnapshot(playerId).join();
        assertEquals(0, snapshot.ownedAmount(boosterId));
        assertEquals(boosterId, snapshot.activeBoosters().values().iterator().next().boosterId());
    }

    @Test
    void schemaRejectsTwoActiveBoostersInTheSamePlayerGroup() {
        UUID playerId = UUID.randomUUID();
        ActiveBooster first = active(playerId, UUID.randomUUID());
        ActiveBooster second = active(playerId, UUID.randomUUID());

        CompletionException exception = assertThrows(CompletionException.class, () -> storage.write(connection -> {
            storage.activations().insertActive(connection, first);
            storage.activations().insertActive(connection, second);
            return null;
        }).join());

        assertFalse(exception.getCause() == null);
        assertTrue(storage.loadSnapshot(playerId).join().activeBoosters().isEmpty());
    }

    @Test
    void manualAdminGrantsWithoutExternalReferenceAreIndependentMutations() {
        UUID playerId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        InventoryMutationService service = inventoryService(boosterId, PostCommitSynchronizer.noop());
        SourceReference manualReference = new SourceReference(Optional.empty(), Optional.empty(), Optional.of("test-server"));

        assertEquals(InventoryMutationStatus.GRANTED, service.grant(new InventoryGrantRequest(
            playerId,
            boosterId,
            1,
            MutationSource.ADMIN_COMMAND,
            manualReference,
            false
        )).join().status());
        assertEquals(InventoryMutationStatus.GRANTED, service.grant(new InventoryGrantRequest(
            playerId,
            boosterId,
            1,
            MutationSource.ADMIN_COMMAND,
            manualReference,
            false
        )).join().status());

        assertEquals(2, storage.loadSnapshot(playerId).join().ownedAmount(boosterId));
    }

    @Test
    void externalReferenceStillKeepsSystemGrantIdempotent() {
        UUID playerId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        InventoryMutationService service = inventoryService(boosterId, PostCommitSynchronizer.noop());
        SourceReference externalReference = new SourceReference(Optional.empty(), Optional.of("reward-transaction-1"), Optional.of("test-server"));

        assertEquals(InventoryMutationStatus.GRANTED, service.grant(new InventoryGrantRequest(
            playerId,
            boosterId,
            1,
            MutationSource.SYSTEM,
            externalReference,
            false
        )).join().status());
        assertEquals(InventoryMutationStatus.DUPLICATE_REQUEST, service.grant(new InventoryGrantRequest(
            playerId,
            boosterId,
            1,
            MutationSource.SYSTEM,
            externalReference,
            false
        )).join().status());

        assertEquals(1, storage.loadSnapshot(playerId).join().ownedAmount(boosterId));
    }

    @Test
    void postCommitPublicationFailureDoesNotOverrideCommittedGrantResult() {
        UUID playerId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        InventoryMutationService service = inventoryService(boosterId, mutation -> {
            throw new IllegalStateException("post commit unavailable");
        });

        assertEquals(InventoryMutationStatus.GRANTED, service.grant(new InventoryGrantRequest(
            playerId,
            boosterId,
            1,
            MutationSource.ADMIN_COMMAND,
            SourceReference.none(),
            false
        )).join().status());
        assertEquals(1, storage.loadSnapshot(playerId).join().ownedAmount(boosterId));
    }

    private static InventoryMutationService inventoryService(BoosterId boosterId, PostCommitSynchronizer postCommit) {
        PlayerSnapshotCache snapshots = mock(PlayerSnapshotCache.class);
        when(snapshots.isReady(org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn(true);
        return new InventoryMutationService(
            storage,
            snapshots,
            configurationStore(boosterId),
            mock(Server.class),
            postCommit
        );
    }

    private static ActivationMutationService activationService(UUID playerId, ConfigurationStore configurationStore) {
        PlayerSnapshotCache snapshots = mock(PlayerSnapshotCache.class);
        when(snapshots.isReady(playerId)).thenReturn(true);
        when(snapshots.getCachedOrEmpty(playerId)).thenReturn(com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot.empty(playerId));
        PlayerPermissionProvider permissions = mock(PlayerPermissionProvider.class);
        when(permissions.satisfies(any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        BoosterEventDispatcher events = mock(BoosterEventDispatcher.class);
        when(events.callPreActivate(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        return new ActivationMutationService(
            storage,
            snapshots,
            configurationStore,
            permissions,
            events,
            PostCommitSynchronizer.noop()
        );
    }

    private static ConfigurationStore configurationStore(BoosterId boosterId) {
        NetworkBoostersConfiguration configuration = mock(NetworkBoostersConfiguration.class);
        when(configuration.inventoryLimits()).thenReturn(new NetworkBoostersConfiguration.InventoryLimits(30, java.util.List.of()));
        when(configuration.activation()).thenReturn(new NetworkBoostersConfiguration.Activation(
            Duration.ofDays(7),
            10,
            Duration.ofSeconds(1),
            100,
            java.util.List.of()
        ));
        ConfigurationSnapshot snapshot = mock(ConfigurationSnapshot.class);
        when(snapshot.configuration()).thenReturn(configuration);
        when(snapshot.definitions()).thenReturn(new BoosterDefinitionRegistry(java.util.Map.of(boosterId, definition(boosterId))));
        ConfigurationStore store = mock(ConfigurationStore.class);
        when(store.requireCurrent()).thenReturn(snapshot);
        return store;
    }

    private static ActiveBooster active(UUID playerId, UUID activationId) {
        Instant now = Instant.now();
        return new ActiveBooster(
            activationId,
            playerId,
            BoosterId.of("personal_points_x2"),
            BoosterTarget.NETWORK_POINTS,
            BigDecimal.valueOf(2),
            ActivationGroup.of("personal-points"),
            ConflictPolicy.QUEUE,
            BoosterScope.personalGlobal(),
            ActivationRequirements.NONE,
            now,
            now.plusSeconds(3600),
            ActivationSource.SYSTEM,
            SourceReference.none()
        );
    }

    private static BoosterDefinition definition(BoosterId boosterId) {
        return new BoosterDefinition(
            boosterId,
            BoosterTarget.NETWORK_POINTS,
            BigDecimal.valueOf(2),
            Duration.ofHours(2),
            BoosterScope.personalGlobal(),
            ActivationGroup.of("personal-points"),
            ConflictPolicy.QUEUE,
            ActivationRequirements.NONE,
            new TransferPolicy(true, 1, 5, Duration.ZERO, Optional.empty()),
            true,
            100,
            BoosterCategory.of("points")
        );
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
