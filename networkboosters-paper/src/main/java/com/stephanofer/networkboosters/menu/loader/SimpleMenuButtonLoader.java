package com.stephanofer.networkboosters.menu.loader;

import com.stephanofer.networkboosters.menu.NetworkBoostersMenuCoordinator;
import com.stephanofer.networkboosters.menu.button.ActivationConfirmButton;
import com.stephanofer.networkboosters.menu.button.ActivationPreviewButton;
import com.stephanofer.networkboosters.menu.button.ClaimsEmptyStateButton;
import com.stephanofer.networkboosters.menu.button.FilterButton;
import com.stephanofer.networkboosters.menu.button.MenuSummaryButton;
import com.stephanofer.networkboosters.menu.button.BoosterStatusButton;
import com.stephanofer.networkboosters.menu.button.OpenClaimsButton;
import com.stephanofer.networkboosters.menu.button.OwnedEmptyStateButton;
import com.stephanofer.networkboosters.menu.button.SortButton;
import com.stephanofer.networkboosters.menu.button.TransferAmountButton;
import com.stephanofer.networkboosters.menu.button.TransferConfirmButton;
import com.stephanofer.networkboosters.menu.button.TransferPreviewButton;
import com.stephanofer.networkboosters.menu.button.TimelineEmptyStateButton;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SimpleMenuButtonLoader extends ButtonLoader {

    private final NetworkBoostersMenuCoordinator coordinator;
    private final ButtonFactory factory;

    private SimpleMenuButtonLoader(Plugin plugin, String name, NetworkBoostersMenuCoordinator coordinator, ButtonFactory factory) {
        super(plugin, name);
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public static SimpleMenuButtonLoader summary(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_SUMMARY", coordinator, (menu, ignored, path) -> new MenuSummaryButton(menu));
    }

    public static SimpleMenuButtonLoader status(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_STATUS", coordinator, (menu, ignored, path) -> new BoosterStatusButton(menu));
    }

    public static SimpleMenuButtonLoader timelineEmpty(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_EMPTY_TIMELINE", coordinator, (menu, ignored, path) -> new TimelineEmptyStateButton(menu));
    }

    public static SimpleMenuButtonLoader filter(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_FILTER", coordinator, (menu, ignored, path) -> new FilterButton(menu));
    }

    public static SimpleMenuButtonLoader sort(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_SORT", coordinator, (menu, ignored, path) -> new SortButton(menu));
    }

    public static SimpleMenuButtonLoader openClaims(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_OPEN_CLAIMS", coordinator, (menu, ignored, path) -> new OpenClaimsButton(menu));
    }

    public static SimpleMenuButtonLoader ownedEmpty(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_EMPTY_OWNED", coordinator, (menu, ignored, path) -> new OwnedEmptyStateButton(menu, OwnedEmptyStateButton.Reason.NO_INVENTORY));
    }

    public static SimpleMenuButtonLoader ownedFilterEmpty(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_EMPTY_FILTER", coordinator, (menu, ignored, path) -> new OwnedEmptyStateButton(menu, OwnedEmptyStateButton.Reason.NO_FILTER_RESULTS));
    }

    public static SimpleMenuButtonLoader claimsEmpty(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_EMPTY_CLAIMS", coordinator, (menu, ignored, path) -> new ClaimsEmptyStateButton(menu));
    }

    public static SimpleMenuButtonLoader activationPreview(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_ACTIVATION_PREVIEW", coordinator, (menu, ignored, path) -> new ActivationPreviewButton(menu));
    }

    public static SimpleMenuButtonLoader activationConfirm(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_ACTIVATION_CONFIRM", coordinator, (menu, ignored, path) -> new ActivationConfirmButton(menu));
    }

    public static SimpleMenuButtonLoader transferPreview(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_TRANSFER_PREVIEW", coordinator, (menu, ignored, path) -> new TransferPreviewButton(menu));
    }

    public static SimpleMenuButtonLoader transferConfirm(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_TRANSFER_CONFIRM", coordinator, (menu, ignored, path) -> new TransferConfirmButton(menu));
    }

    public static SimpleMenuButtonLoader transferAmount(Plugin plugin, NetworkBoostersMenuCoordinator coordinator) {
        return new SimpleMenuButtonLoader(plugin, "NETWORKBOOSTERS_TRANSFER_AMOUNT", coordinator, (menu, configuration, path) -> new TransferAmountButton(menu, configuration.getLong(path + "delta", 1)));
    }

    @Override
    public @Nullable Button load(@NotNull YamlConfiguration configuration, @NotNull String path, @NotNull DefaultButtonValue defaultButtonValue) {
        return this.factory.load(this.coordinator, configuration, path);
    }

    @FunctionalInterface
    private interface ButtonFactory {
        Button load(NetworkBoostersMenuCoordinator coordinator, YamlConfiguration configuration, String path);
    }
}
