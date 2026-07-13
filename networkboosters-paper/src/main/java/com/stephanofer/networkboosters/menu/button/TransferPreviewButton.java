package com.stephanofer.networkboosters.menu.button;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.utils.Placeholders;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class TransferPreviewButton extends Button {

    private final NetworkBoostersMenuCoordinator coordinator;

    public TransferPreviewButton(NetworkBoostersMenuCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        Placeholders values = this.coordinator.basePlaceholders(player);
        this.coordinator.pendingTransfer(player).ifPresent(pending -> {
            values.register("booster_id", pending.boosterId().value());
            values.register("amount", String.valueOf(pending.amount()));
            Player recipient = player.getServer().getPlayer(pending.recipientId());
            values.register("target", recipient == null ? "?" : recipient.getName());
        });
        return super.getCustomItemStack(player, false, values);
    }
}
