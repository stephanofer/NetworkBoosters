package com.stephanofer.networkboosters.menu.button;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import java.util.Objects;
import org.bukkit.entity.Player;

public final class TimelineEmptyStateButton extends Button {

    private final NetworkBoostersMenuCoordinator coordinator;

    public TimelineEmptyStateButton(NetworkBoostersMenuCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public boolean hasCustomRender() {
        return true;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        if (this.coordinator.timeline(player).isEmpty()) {
            inventoryEngine.addItem(this.getSlot(), this.getCustomItemStack(player, false, this.coordinator.basePlaceholders(player)));
        }
    }
}
