package com.stephanofer.networkboosters.menu.button;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.utils.Placeholders;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class ActivationPreviewButton extends Button {

    private final NetworkBoostersMenuCoordinator coordinator;

    public ActivationPreviewButton(NetworkBoostersMenuCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        Placeholders values = this.coordinator.basePlaceholders(player);
        this.coordinator.pendingActivation(player).ifPresent(pending -> values.register("booster_id", pending.boosterId().value()));
        return super.getCustomItemStack(player, false, values);
    }
}
