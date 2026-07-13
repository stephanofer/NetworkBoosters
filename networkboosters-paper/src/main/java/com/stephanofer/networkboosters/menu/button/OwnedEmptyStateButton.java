package com.stephanofer.networkboosters.menu.button;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jspecify.annotations.NonNull;

public final class OwnedEmptyStateButton extends Button {

    private final NetworkBoostersMenuCoordinator coordinator;
    private final Reason reason;

    public OwnedEmptyStateButton(NetworkBoostersMenuCoordinator coordinator, Reason reason) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        boolean show = switch (this.reason) {
            case NO_INVENTORY -> !this.coordinator.hasOwnedBoosters(player);
            case NO_FILTER_RESULTS -> this.coordinator.hasOwnedBoosters(player) && !this.coordinator.hasVisibleOwnedBoosters(player);
        };
        if (show) {
            inventoryEngine.addItem(this.getSlot(), this.getCustomItemStack(player, false, this.coordinator.basePlaceholders(player)));
        }
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        event.setCancelled(true);
    }

    public enum Reason {
        NO_INVENTORY,
        NO_FILTER_RESULTS
    }
}
