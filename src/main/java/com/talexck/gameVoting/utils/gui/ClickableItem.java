package com.talexck.gameVoting.utils.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.Consumer;

public class ClickableItem {
    private final ItemStack itemStack;
    private final Consumer<Player> clickHandler;

    private ClickableItem(ItemStack itemStack, Consumer<Player> clickHandler) {
        this.itemStack = itemStack;
        this.clickHandler = clickHandler;
    }

    public static ClickableItem of(Material material, String name, Consumer<Player> handler) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return new ClickableItem(item, handler);
    }

    public static ClickableItem of(Material material, String name, List<String> lore, Consumer<Player> handler) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return new ClickableItem(item, handler);
    }

    public static ClickableItem of(ItemStack item, Consumer<Player> handler) {
        return new ClickableItem(item, handler);
    }

    public static ClickableItem empty() {
        return new ClickableItem(new ItemStack(Material.AIR), player -> {});
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void onClick(Player player) {
        if (clickHandler != null) {
            clickHandler.accept(player);
        }
    }

    public boolean hasClickHandler() {
        return clickHandler != null;
    }
}
