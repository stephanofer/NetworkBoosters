package com.stephanofer.networkboosters.menu.button;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import com.stephanofer.networkboosters.menu.OwnedBoosterView;
import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

public final class OwnedBoostersButton extends PaginateButton {

    private final NetworkBoostersMenuCoordinator coordinator;

    public OwnedBoostersButton(NetworkBoostersMenuCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        for (int slot : this.getSlots()) {
            inventoryEngine.removeItem(slot);
        }
        int page = Math.max(1, inventoryEngine.getPage());
        List<OwnedBoosterView> views = this.coordinator.pageViews(player, page, this.getSlots().size());
        if (views.isEmpty()) {
            return;
        }
        ArrayList<Integer> slots = new ArrayList<>(this.getSlots());
        for (int index = 0; index < views.size(); index++) {
            OwnedBoosterView view = views.get(index);
            int slot = slots.get(index);
            Placeholders placeholders = this.coordinator.boosterPlaceholders(player, view);
            ItemStack itemStack = this.coordinator.applyDisplay(view, this.getCustomItemStack(player, false, placeholders));
            itemStack.setAmount(1);
            var itemButton = inventoryEngine.addItem(slot, itemStack);
            if (itemButton != null) {
                itemButton.setClick(event -> this.click(player, event, inventoryEngine, view));
            }
        }
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
        return this.coordinator.visibleUnitCount(player);
    }

    private void click(Player player, InventoryClickEvent event, InventoryEngine inventory, OwnedBoosterView view) {
        event.setCancelled(true);
        if (view.definition().isEmpty()) {
            this.coordinator.update(player);
            return;
        }
        if (!event.isLeftClick()) {
            return;
        }
        this.coordinator.beginActivation(player, view.boosterId(), inventory.getPage());
    }
}
