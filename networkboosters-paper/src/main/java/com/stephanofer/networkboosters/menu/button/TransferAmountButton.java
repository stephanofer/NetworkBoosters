package com.stephanofer.networkboosters.menu.button;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jspecify.annotations.NonNull;

public final class TransferAmountButton extends Button {

    private final NetworkBoostersMenuCoordinator coordinator;
    private final long delta;

    public TransferAmountButton(NetworkBoostersMenuCoordinator coordinator, long delta) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.delta = delta;
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);
        this.coordinator.changeTransferAmount(player, this.delta);
    }
}
