package com.stephanofer.networkboosters.menu.button;

import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public final class ClaimsButton extends PaginateButton {

    private final NetworkBoostersMenuCoordinator coordinator;
    private final int emptySlot;

    public ClaimsButton(NetworkBoostersMenuCoordinator coordinator, int emptySlot) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.emptySlot = emptySlot;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        List<BoosterClaim> claims = playerClaims(player);
        if (claims.isEmpty()) {
            if (this.emptySlot >= 0) {
                inventoryEngine.addItem(this.emptySlot, this.getCustomItemStack(player, false, this.coordinator.basePlaceholders(player)));
            }
            return;
        }
        ArrayList<Integer> slots = new ArrayList<>(this.getSlots());
        int start = Math.max(0, inventoryEngine.getPage() - 1) * slots.size();
        int end = Math.min(claims.size(), start + slots.size());
        for (int index = start; index < end; index++) {
            BoosterClaim claim = claims.get(index);
            Placeholders placeholders = this.coordinator.basePlaceholders(player);
            placeholders.register("claim_id", claim.claimId().toString());
            placeholders.register("booster_id", claim.boosterId().value());
            placeholders.register("amount", String.valueOf(claim.amount()));
            var itemButton = inventoryEngine.addItem(slots.get(index - start), this.getCustomItemStack(player, false, placeholders));
            if (itemButton != null) {
                itemButton.setClick(event -> this.coordinator.claim(player, claim));
            }
        }
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
        return playerClaims(player).size();
    }

    private List<BoosterClaim> playerClaims(Player player) {
        return this.coordinator.claims(player);
    }
}
