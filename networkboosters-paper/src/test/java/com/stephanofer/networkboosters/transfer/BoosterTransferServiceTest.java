package com.stephanofer.networkboosters.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import com.stephanofer.networkboosters.api.booster.BoosterCategory;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.booster.TransferPolicy;
import com.stephanofer.networkboosters.api.request.BoosterTransferRequest;
import com.stephanofer.networkboosters.api.result.TransferStatus;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.api.source.TransferSource;
import com.stephanofer.networkboosters.config.ConfigurationSnapshot;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import com.stephanofer.networkboosters.config.booster.BoosterDefinitionRegistry;
import com.stephanofer.networkboosters.persistence.BoosterStorage;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import com.stephanofer.networkboosters.synchronization.PostCommitSynchronizer;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BoosterTransferServiceTest {

    @Test
    void uuidLockOrderingUsesCanonicalUnsignedBinaryOrder() {
        UUID highSignedMostBits = new UUID(Long.MIN_VALUE, 0);
        UUID lowMostBits = new UUID(1, 0);
        UUID highSignedLeastBits = new UUID(0, Long.MIN_VALUE);
        UUID lowLeastBits = new UUID(0, 1);

        assertTrue(BoosterTransferService.compareUuidBinary(lowMostBits, highSignedMostBits) < 0);
        assertTrue(BoosterTransferService.compareUuidBinary(lowLeastBits, highSignedLeastBits) < 0);

        ArrayList<UUID> values = new ArrayList<>(List.of(highSignedMostBits, lowMostBits, highSignedLeastBits, lowLeastBits));
        values.sort(BoosterTransferService::compareUuidBinary);

        assertTrue(isSorted(values, BoosterTransferService::compareUuidBinary));
    }

    @Test
    void revalidatesRecipientAvailabilityImmediatelyBeforeWriting() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        BoosterStorage storage = mock(BoosterStorage.class);
        PlayerSnapshotCache snapshots = mock(PlayerSnapshotCache.class);
        ConfigurationStore configurationStore = configurationStore(boosterId);
        Server server = mock(Server.class);
        Plugin plugin = mock(Plugin.class);
        Player sender = onlinePlayer();
        Player recipient = onlinePlayer();
        when(server.getPlayer(senderId)).thenReturn(sender);
        when(server.getPlayer(recipientId)).thenReturn(recipient).thenReturn((Player) null);
        when(snapshots.isReady(senderId)).thenReturn(true);
        when(snapshots.isReady(recipientId)).thenReturn(true);

        BoosterTransferService service = new BoosterTransferService(storage, snapshots, configurationStore, server, plugin, PostCommitSynchronizer.noop());
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            assertEquals(
                TransferStatus.RECIPIENT_NOT_ONLINE,
                service.transfer(request(senderId, recipientId, boosterId)).join().status()
            );
        }

        verifyNoInteractions(storage);
    }

    @Test
    void rejectsTransactionThatWasQueuedBeforeServiceClose() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        BoosterId boosterId = BoosterId.of("personal_points_x2");
        BoosterStorage storage = mock(BoosterStorage.class);
        PlayerSnapshotCache snapshots = mock(PlayerSnapshotCache.class);
        ConfigurationStore configurationStore = configurationStore(boosterId);
        Server server = mock(Server.class);
        Plugin plugin = mock(Plugin.class);
        Player sender = onlinePlayer();
        Player recipient = onlinePlayer();
        when(server.getPlayer(senderId)).thenReturn(sender);
        when(server.getPlayer(recipientId)).thenReturn(recipient);
        when(snapshots.isReady(senderId)).thenReturn(true);
        when(snapshots.isReady(recipientId)).thenReturn(true);

        BoosterTransferService service = new BoosterTransferService(storage, snapshots, configurationStore, server, plugin, PostCommitSynchronizer.noop());
        when(storage.write(any())).thenAnswer(invocation -> {
            service.close();
            BoosterStorage.TransactionalOperation<?> operation = invocation.getArgument(0);
            return CompletableFuture.completedFuture(operation.execute(null));
        });

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            assertEquals(
                TransferStatus.SERVICE_UNAVAILABLE,
                service.transfer(request(senderId, recipientId, boosterId)).join().status()
            );
        }

        verify(snapshots, never()).publish(any());
    }

    private static ConfigurationStore configurationStore(BoosterId boosterId) {
        NetworkBoostersConfiguration configuration = mock(NetworkBoostersConfiguration.class);
        when(configuration.inventoryLimits()).thenReturn(new NetworkBoostersConfiguration.InventoryLimits(30, List.of()));
        ConfigurationSnapshot snapshot = mock(ConfigurationSnapshot.class);
        when(snapshot.configuration()).thenReturn(configuration);
        when(snapshot.definitions()).thenReturn(new BoosterDefinitionRegistry(Map.of(boosterId, definition(boosterId))));
        ConfigurationStore store = mock(ConfigurationStore.class);
        when(store.requireCurrent()).thenReturn(snapshot);
        return store;
    }

    private static Player onlinePlayer() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private static BoosterTransferRequest request(UUID senderId, UUID recipientId, BoosterId boosterId) {
        return new BoosterTransferRequest(
            senderId,
            recipientId,
            boosterId,
            1,
            TransferSource.PLAYER_COMMAND,
            SourceReference.actor(senderId)
        );
    }

    private static BoosterDefinition definition(BoosterId boosterId) {
        return new BoosterDefinition(
            boosterId,
            BoosterTarget.NETWORK_POINTS,
            BigDecimal.valueOf(2),
            Duration.ofHours(1),
            BoosterScope.personalGlobal(),
            ActivationGroup.of("personal-points"),
            ConflictPolicy.QUEUE,
            ActivationRequirements.NONE,
            new TransferPolicy(true, 1, 10, Duration.ZERO, Optional.empty()),
            true,
            0,
            BoosterCategory.of("points")
        );
    }

    private static boolean isSorted(List<UUID> values, Comparator<UUID> comparator) {
        for (int index = 1; index < values.size(); index++) {
            if (comparator.compare(values.get(index - 1), values.get(index)) > 0) {
                return false;
            }
        }
        return true;
    }
}
