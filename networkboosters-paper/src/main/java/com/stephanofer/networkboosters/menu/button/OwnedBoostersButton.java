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
        List<OwnedBoosterView> views = this.coordinator.views(player);
        if (views.isEmpty()) {
            return;
        }
        int page = Math.max(0, inventoryEngine.getPage() - 1);
        ArrayList<Integer> slots = new ArrayList<>(this.getSlots());
        int start = page * slots.size();
        int end = Math.min(views.size(), start + slots.size());
        for (int index = start; index < end; index++) {
            OwnedBoosterView view = views.get(index);
            int slot = slots.get(index - start);
            Placeholders placeholders = this.coordinator.boosterPlaceholders(player, view);
            ItemStack itemStack = this.coordinator.applyDisplay(view, this.getCustomItemStack(player, false, placeholders));
            var itemButton = inventoryEngine.addItem(slot, itemStack);
            if (itemButton != null) {
                itemButton.setClick(event -> this.click(player, event, inventoryEngine, view));
            }
        }
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
        return this.coordinator.views(player).size();
    }

    private void click(Player player, InventoryClickEvent event, InventoryEngine inventory, OwnedBoosterView view) {
        event.setCancelled(true);
        if (view.definition().isEmpty()) {
            this.coordinator.update(player);
            return;
        }
        if (event.isRightClick()) {
            if (view.transferable()) {
                this.coordinator.beginTransfer(player, view.boosterId(), inventory.getPage());
            } else {
                this.coordinator.update(player);
            }
            return;
        }
        this.coordinator.beginActivation(player, view.boosterId(), inventory.getPage());
    }
}
