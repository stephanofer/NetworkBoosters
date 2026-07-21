package com.stephanofer.networkboosters.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.calculation.BoostCalculation;
import com.stephanofer.networkboosters.api.calculation.BoostRequest;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.booster.ActivationMutationService;
import com.stephanofer.networkboosters.calculation.BoostCalculator;
import com.stephanofer.networkboosters.config.ConfigurationSnapshot;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import com.stephanofer.networkboosters.inventory.InventoryMutationService;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import com.stephanofer.networkboosters.transfer.BoosterTransferService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkBoostersServiceImplTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BoostRequest REQUEST = BoostRequest.of(
        PLAYER_ID,
        BoosterTarget.NETWORK_POINTS,
        BigDecimal.TEN,
        "skywars",
        "skywars-01"
    );

    @Test
    void calculateIfReadyReturnsEmptyWithoutTreatingMissingStateAsNeutral() {
        ConfigurationStore configurationStore = mock(ConfigurationStore.class);
        PlayerSnapshotCache snapshots = mock(PlayerSnapshotCache.class);
        when(snapshots.cached(PLAYER_ID)).thenReturn(Optional.empty());
        NetworkBoostersServiceImpl service = service(configurationStore, snapshots);

        assertTrue(service.calculateIfReady(REQUEST).isEmpty());

        verify(snapshots).cached(PLAYER_ID);
        verifyNoMoreInteractions(snapshots);
        verifyNoInteractions(configurationStore);
    }

    @Test
    void calculateIfReadyReturnsPresentNeutralCalculationForReadyPlayerWithoutApplicableBoosts() {
        ConfigurationStore configurationStore = configurationStore();
        PlayerSnapshotCache snapshots = mock(PlayerSnapshotCache.class);
        when(snapshots.cached(PLAYER_ID)).thenReturn(Optional.of(PlayerBoostSnapshot.empty(PLAYER_ID)));
        NetworkBoostersServiceImpl service = service(configurationStore, snapshots);

        Optional<BoostCalculation> result = service.calculateIfReady(REQUEST);

        assertTrue(result.isPresent());
        assertEquals(BigDecimal.ONE, result.orElseThrow().multiplier());
        assertEquals(BigDecimal.TEN, result.orElseThrow().finalAmount());
        verify(snapshots).cached(PLAYER_ID);
        verifyNoMoreInteractions(snapshots);
    }

    private static NetworkBoostersServiceImpl service(ConfigurationStore configurationStore, PlayerSnapshotCache snapshots) {
        return new NetworkBoostersServiceImpl(
            configurationStore,
            snapshots,
            new BoostCalculator(),
            mock(ActivationMutationService.class),
            mock(InventoryMutationService.class),
            mock(BoosterTransferService.class),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static ConfigurationStore configurationStore() {
        NetworkBoostersConfiguration configuration = mock(NetworkBoostersConfiguration.class);
        when(configuration.limits()).thenReturn(new NetworkBoostersConfiguration.Limits(BigDecimal.TEN));
        ConfigurationSnapshot snapshot = mock(ConfigurationSnapshot.class);
        when(snapshot.configuration()).thenReturn(configuration);
        ConfigurationStore store = mock(ConfigurationStore.class);
        when(store.requireCurrent()).thenReturn(snapshot);
        return store;
    }
}
