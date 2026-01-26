package com.talexck.gameVoting.utils.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ChestUI {
    private final String title;
    private final int rows;
    private final Map<Integer, ClickableItem> items;
    private final Inventory inventory;

    protected ChestUI(String title, int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Rows must be between 1 and 6");
        }

        this.title = title;
        this.rows = rows;
        this.items = new HashMap<>();
        this.inventory = Bukkit.createInventory(null, rows * 9, title);
    }

    public void setItem(int slot, ClickableItem item) {
        if (slot < 0 || slot >= rows * 9) {
            throw new IllegalArgumentException("Invalid slot: " + slot);
        }

        items.put(slot, item);
        inventory.setItem(slot, item.getItemStack());
    }

    public void fillBorder(ItemStack item) {
        int size = rows * 9;

        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            setItem(i, ClickableItem.of(item, player -> {}));
            if (rows > 1) {
                setItem(size - 9 + i, ClickableItem.of(item, player -> {}));
            }
        }

        // Left and right columns
        for (int i = 1; i < rows - 1; i++) {
            setItem(i * 9, ClickableItem.of(item, player -> {}));
            setItem(i * 9 + 8, ClickableItem.of(item, player -> {}));
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
        ChestUIListener.registerMenu(player, this);
    }

    public void close(Player player) {
        player.closeInventory();
        ChestUIListener.unregisterMenu(player);
    }

    public ClickableItem getItem(int slot) {
        return items.get(slot);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public String getTitle() {
        return title;
    }

    public int getRows() {
        return rows;
    }

    public static ChestUIBuilder builder() {
        return new ChestUIBuilder();
    }
}
