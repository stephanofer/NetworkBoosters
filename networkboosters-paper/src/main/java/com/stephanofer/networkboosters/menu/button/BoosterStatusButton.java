package com.stephanofer.networkboosters.menu.button;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class BoosterStatusButton extends Button {

    private final NetworkBoostersMenuCoordinator coordinator;

    public BoosterStatusButton(NetworkBoostersMenuCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        ItemStack item = super.getCustomItemStack(player, false, this.coordinator.statusPlaceholders(player));
        var timeline = this.coordinator.timeline(player);
        item.setType(timeline.stream().anyMatch(view -> view.active())
            ? Material.BEACON
            : timeline.isEmpty() ? Material.GRAY_DYE : Material.CLOCK);
        return item;
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);
        this.coordinator.openStatus(player);
    }
}
