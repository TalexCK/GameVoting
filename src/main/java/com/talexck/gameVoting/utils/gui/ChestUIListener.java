package com.talexck.gameVoting.utils.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChestUIListener implements Listener {
    private static final Map<UUID, ChestUI> activeMenus = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ChestUI menu = activeMenus.get(player.getUniqueId());

        if (menu == null) {
            return;
        }

        // Check if the inventory being clicked is our custom menu
        if (!event.getInventory().equals(menu.getInventory())) {
            return;
        }

        // Cancel the event to prevent item removal
        event.setCancelled(true);

        // Get the clicked slot
        int slot = event.getRawSlot();

        // Ignore clicks outside the inventory
        if (slot < 0 || slot >= menu.getInventory().getSize()) {
            return;
        }

        // Get the item at the clicked slot
        ClickableItem item = menu.getItem(slot);

        if (item != null && item.hasClickHandler()) {
            item.onClick(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        ChestUI menu = activeMenus.get(player.getUniqueId());

        if (menu == null) {
            return;
        }

        // Check if the inventory being closed is our custom menu
        if (event.getInventory().equals(menu.getInventory())) {
            activeMenus.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ChestUI menu = activeMenus.get(player.getUniqueId());

        if (menu == null) {
            return;
        }

        // Check if any of the dragged slots are in our custom menu
        if (event.getInventory().equals(menu.getInventory())) {
            event.setCancelled(true);
        }
    }

    public static void registerMenu(Player player, ChestUI menu) {
        activeMenus.put(player.getUniqueId(), menu);
    }

    public static void unregisterMenu(Player player) {
        activeMenus.remove(player.getUniqueId());
    }

    public static void clearAll() {
        activeMenus.clear();
    }
}
