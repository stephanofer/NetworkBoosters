package com.stephanofer.networkboosters.menu;

import com.stephanofer.networkboosters.menu.button.ClaimsEmptyStateButton;
import com.stephanofer.networkboosters.menu.button.OwnedEmptyStateButton;
import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class MenuEmptyStateButtonTest {

    @Test
    void ownedEmptyStateUsesCustomRendering() {
        OwnedEmptyStateButton button = new OwnedEmptyStateButton(
            mock(NetworkBoostersMenuCoordinator.class),
            OwnedEmptyStateButton.Reason.NO_INVENTORY
        );

        assertTrue(button.hasCustomRender());
    }

    @Test
    void showsOwnedEmptyStateOnlyWhenInventoryIsEmpty() {
        NetworkBoostersMenuCoordinator coordinator = mock(NetworkBoostersMenuCoordinator.class);
        Player player = mock(Player.class);
        InventoryEngine inventory = mock(InventoryEngine.class);
        when(coordinator.basePlaceholders(player)).thenReturn(new Placeholders());
        OwnedEmptyStateButton button = new OwnedEmptyStateButton(coordinator, OwnedEmptyStateButton.Reason.NO_INVENTORY);
        configure(button);

        button.onRender(player, inventory);

        verify(inventory).addItem(eq(22), any());
    }

    @Test
    void showsFilterEmptyStateOnlyWhenOwnedBoostersHaveNoVisibleResults() {
        NetworkBoostersMenuCoordinator coordinator = mock(NetworkBoostersMenuCoordinator.class);
        Player player = mock(Player.class);
        InventoryEngine inventory = mock(InventoryEngine.class);
        when(coordinator.hasOwnedBoosters(player)).thenReturn(true);
        when(coordinator.hasVisibleOwnedBoosters(player)).thenReturn(false);
        when(coordinator.basePlaceholders(player)).thenReturn(new Placeholders());
        OwnedEmptyStateButton button = new OwnedEmptyStateButton(coordinator, OwnedEmptyStateButton.Reason.NO_FILTER_RESULTS);
        configure(button);

        button.onRender(player, inventory);

        verify(inventory).addItem(eq(22), any());
    }

    @Test
    void doesNotShowFilterEmptyStateWhenFilterHasResults() {
        NetworkBoostersMenuCoordinator coordinator = mock(NetworkBoostersMenuCoordinator.class);
        Player player = mock(Player.class);
        InventoryEngine inventory = mock(InventoryEngine.class);
        when(coordinator.hasOwnedBoosters(player)).thenReturn(true);
        when(coordinator.hasVisibleOwnedBoosters(player)).thenReturn(true);
        OwnedEmptyStateButton button = new OwnedEmptyStateButton(coordinator, OwnedEmptyStateButton.Reason.NO_FILTER_RESULTS);
        configure(button);

        button.onRender(player, inventory);

        verify(inventory, never()).addItem(eq(22), any());
    }

    @Test
    void claimsEmptyStateUsesCustomRenderingAndOnlyShowsWithoutClaims() {
        NetworkBoostersMenuCoordinator coordinator = mock(NetworkBoostersMenuCoordinator.class);
        Player player = mock(Player.class);
        InventoryEngine emptyInventory = mock(InventoryEngine.class);
        InventoryEngine populatedInventory = mock(InventoryEngine.class);
        when(coordinator.basePlaceholders(player)).thenReturn(new Placeholders());
        ClaimsEmptyStateButton button = new ClaimsEmptyStateButton(coordinator);
        configure(button);

        assertTrue(button.hasCustomRender());
        button.onRender(player, emptyInventory);
        when(coordinator.hasPendingClaims(player)).thenReturn(true);
        button.onRender(player, populatedInventory);

        verify(emptyInventory).addItem(eq(22), any());
        verify(populatedInventory, never()).addItem(eq(22), any());
    }

    private static void configure(Button button) {
        MenuItemStack menuItemStack = mock(MenuItemStack.class);
        when(menuItemStack.build(any(Player.class), eq(false), any(Placeholders.class))).thenReturn(mock(ItemStack.class));
        button.setSlot(22);
        button.setItemStack(menuItemStack);
    }
}
