package com.stephanofer.networkboosters.menu.button;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jspecify.annotations.NonNull;

public final class ClaimsEmptyStateButton extends Button {

    private final NetworkBoostersMenuCoordinator coordinator;

    public ClaimsEmptyStateButton(NetworkBoostersMenuCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        if (!this.coordinator.hasPendingClaims(player)) {
            inventoryEngine.addItem(this.getSlot(), this.getCustomItemStack(player, false, this.coordinator.basePlaceholders(player)));
        }
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        event.setCancelled(true);
    }
}
