package com.stephanofer.networkboosters.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.Databases;
import com.hera.craftkit.database.MigrationConfig;
import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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

    private static ActiveBooster active(UUID playerId, UUID activationId) {
        Instant now = Instant.now();
        return new ActiveBooster(
            activationId,
            playerId,
            BoosterId.of("personal_points_x2"),
            BoosterTarget.NETWORK_PROGRESSION_POINTS,
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

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
