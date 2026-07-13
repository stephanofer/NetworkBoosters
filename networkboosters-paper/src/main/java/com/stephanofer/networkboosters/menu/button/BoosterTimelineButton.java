package com.stephanofer.networkboosters.menu.button;

import com.stephanofer.networkboosters.menu.BoosterTimelineView;
import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

public final class BoosterTimelineButton extends PaginateButton {

    private final NetworkBoostersMenuCoordinator coordinator;

    public BoosterTimelineButton(NetworkBoostersMenuCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        for (int slot : this.getSlots()) {
            inventoryEngine.removeItem(slot);
        }
        List<BoosterTimelineView> timeline = this.coordinator.timeline(player);
        ArrayList<Integer> slots = new ArrayList<>(this.getSlots());
        int start = Math.max(0, inventoryEngine.getPage() - 1) * slots.size();
        int end = Math.min(timeline.size(), start + slots.size());
        for (int index = start; index < end; index++) {
            BoosterTimelineView view = timeline.get(index);
            ItemStack item = this.getCustomItemStack(player, false, this.coordinator.timelinePlaceholders(player, view));
            item.setType(view.active() ? Material.HEAVY_CORE : Material.CLOCK);
            item.setAmount(1);
            inventoryEngine.addItem(slots.get(index - start), item);
        }
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
        return this.coordinator.timeline(player).size();
    }
}
