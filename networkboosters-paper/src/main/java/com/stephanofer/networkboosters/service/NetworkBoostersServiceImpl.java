package com.stephanofer.networkboosters.service;

import com.stephanofer.networkboosters.api.NetworkBoostersService;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.calculation.BoostCalculation;
import com.stephanofer.networkboosters.api.calculation.BoostRequest;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.request.ActivationRequest;
import com.stephanofer.networkboosters.api.request.BoosterTransferRequest;
import com.stephanofer.networkboosters.api.request.ClaimRequest;
import com.stephanofer.networkboosters.api.request.ClaimCreationRequest;
import com.stephanofer.networkboosters.api.request.DeactivationRequest;
import com.stephanofer.networkboosters.api.request.InventoryGrantRequest;
import com.stephanofer.networkboosters.api.request.InventoryRevokeRequest;
import com.stephanofer.networkboosters.api.request.InventorySetRequest;
import com.stephanofer.networkboosters.api.result.ActivationResult;
import com.stephanofer.networkboosters.api.result.ClaimResult;
import com.stephanofer.networkboosters.api.result.DeactivationResult;
import com.stephanofer.networkboosters.api.result.InventoryMutationResult;
import com.stephanofer.networkboosters.api.result.TransferResult;
import com.stephanofer.networkboosters.calculation.BoostCalculator;
import com.stephanofer.networkboosters.booster.ActivationMutationService;
import com.stephanofer.networkboosters.config.ConfigurationStore;
import com.stephanofer.networkboosters.inventory.InventoryMutationService;
import com.stephanofer.networkboosters.player.PlayerSnapshotCache;
import com.stephanofer.networkboosters.transfer.BoosterTransferService;
import java.time.Clock;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NetworkBoostersServiceImpl implements NetworkBoostersService {

    private final ConfigurationStore configurationStore;
    private final PlayerSnapshotCache snapshots;
    private final BoostCalculator calculator;
    private final ActivationMutationService activationMutations;
    private final InventoryMutationService inventoryMutations;
    private final BoosterTransferService transfers;
    private final Clock clock;

    public NetworkBoostersServiceImpl(
        ConfigurationStore configurationStore,
        PlayerSnapshotCache snapshots,
        BoostCalculator calculator,
        ActivationMutationService activationMutations,
        InventoryMutationService inventoryMutations,
        BoosterTransferService transfers,
        Clock clock
    ) {
        this.configurationStore = Objects.requireNonNull(configurationStore, "configurationStore");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.activationMutations = Objects.requireNonNull(activationMutations, "activationMutations");
        this.inventoryMutations = Objects.requireNonNull(inventoryMutations, "inventoryMutations");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<BoosterDefinition> definition(BoosterId boosterId) {
        return this.configurationStore.requireCurrent().definitions().find(boosterId);
    }

    @Override
    public Collection<BoosterDefinition> definitions() {
        return this.configurationStore.requireCurrent().definitions().definitions();
    }

    @Override
    public Optional<PlayerBoostSnapshot> cached(UUID playerId) {
        return this.snapshots.cached(playerId);
    }

    @Override
    public PlayerBoostSnapshot getCachedOrEmpty(UUID playerId) {
        return this.snapshots.getCachedOrEmpty(playerId);
    }

    @Override
    public CompletableFuture<PlayerBoostSnapshot> load(UUID playerId) {
        return this.snapshots.load(playerId);
    }

    @Override
    public CompletableFuture<PlayerBoostSnapshot> refresh(UUID playerId) {
        return this.snapshots.refresh(playerId);
    }

    @Override
    public boolean isReady(UUID playerId) {
        return this.snapshots.isReady(playerId);
    }

    @Override
    public BoostCalculation calculate(BoostRequest request) {
        return this.calculator.calculate(
            this.snapshots.cached(request.playerId()),
            request,
            this.clock.instant(),
            this.configurationStore.requireCurrent().configuration().limits().maximumMultiplier()
        );
    }

    @Override
    public Optional<BoostCalculation> calculateIfReady(BoostRequest request) {
        Objects.requireNonNull(request, "request");
        return this.snapshots.cached(request.playerId()).map(snapshot -> this.calculate(request, snapshot));
    }

    @Override
    public BoostCalculation calculate(BoostRequest request, PlayerBoostSnapshot snapshot) {
        return this.calculator.calculate(
            snapshot,
            request,
            this.clock.instant(),
            this.configurationStore.requireCurrent().configuration().limits().maximumMultiplier()
        );
    }

    @Override
    public CompletableFuture<ActivationResult> activate(ActivationRequest request) {
        return this.activationMutations.activate(request);
    }

    @Override
    public CompletableFuture<InventoryMutationResult> grant(InventoryGrantRequest request) {
        return this.inventoryMutations.grant(request);
    }

    @Override
    public CompletableFuture<InventoryMutationResult> revoke(InventoryRevokeRequest request) {
        return this.inventoryMutations.revoke(request);
    }

    @Override
    public CompletableFuture<InventoryMutationResult> setInventoryAmount(InventorySetRequest request) {
        return this.inventoryMutations.setInventoryAmount(request);
    }

    @Override
    public CompletableFuture<TransferResult> transfer(BoosterTransferRequest request) {
        return this.transfers.transfer(request);
    }

    @Override
    public CompletableFuture<ClaimResult> claim(ClaimRequest request) {
        return this.inventoryMutations.claim(request);
    }

    @Override
    public CompletableFuture<InventoryMutationResult> createClaim(ClaimCreationRequest request) {
        return this.inventoryMutations.createClaim(request);
    }

    @Override
    public CompletableFuture<DeactivationResult> deactivate(DeactivationRequest request) {
        return this.activationMutations.deactivate(request);
    }
}
