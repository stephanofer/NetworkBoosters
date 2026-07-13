package com.stephanofer.networkboosters.api;

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
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface NetworkBoostersService {

    Optional<BoosterDefinition> definition(BoosterId boosterId);

    Collection<BoosterDefinition> definitions();

    Optional<PlayerBoostSnapshot> cached(UUID playerId);

    PlayerBoostSnapshot getCachedOrEmpty(UUID playerId);

    CompletableFuture<PlayerBoostSnapshot> load(UUID playerId);

    CompletableFuture<PlayerBoostSnapshot> refresh(UUID playerId);

    boolean isReady(UUID playerId);

    /**
     * Synchronous, thread-safe and I/O-free. Consumers must not expect this method to load state.
     */
    BoostCalculation calculate(BoostRequest request);

    CompletableFuture<ActivationResult> activate(ActivationRequest request);

    CompletableFuture<InventoryMutationResult> grant(InventoryGrantRequest request);

    CompletableFuture<InventoryMutationResult> revoke(InventoryRevokeRequest request);

    CompletableFuture<InventoryMutationResult> setInventoryAmount(InventorySetRequest request);

    CompletableFuture<TransferResult> transfer(BoosterTransferRequest request);

    CompletableFuture<ClaimResult> claim(ClaimRequest request);

    CompletableFuture<InventoryMutationResult> createClaim(ClaimCreationRequest request);

    CompletableFuture<DeactivationResult> deactivate(DeactivationRequest request);
}
