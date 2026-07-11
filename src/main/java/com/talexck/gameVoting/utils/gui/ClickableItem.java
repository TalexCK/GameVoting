package com.talexck.gameVoting.utils.gui;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ClickableItem {
  private final ItemStack itemStack;
  private final BiConsumer<Player, InventoryClickEvent> clickHandler;

  private ClickableItem(ItemStack itemStack, BiConsumer<Player, InventoryClickEvent> clickHandler) {
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
    return of(item, handler);
  }

  public static ClickableItem of(
      Material material, String name, List<String> lore, Consumer<Player> handler) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(name);
      meta.setLore(lore);
      item.setItemMeta(meta);
    }
    return of(item, handler);
  }

  public static ClickableItem of(ItemStack item, Consumer<Player> handler) {
    return new ClickableItem(item, (player, event) -> handler.accept(player));
  }

  public static ClickableItem of(ItemStack item, BiConsumer<Player, InventoryClickEvent> handler) {
    return new ClickableItem(item, handler);
  }

  public static ClickableItem empty() {
    return new ClickableItem(new ItemStack(Material.AIR), (player, event) -> {});
  }

  public ItemStack getItemStack() {
    return itemStack;
  }

  public void onClick(Player player, InventoryClickEvent event) {
    if (clickHandler != null) {
      clickHandler.accept(player, event);
    }
  }

  public boolean hasClickHandler() {
    return clickHandler != null;
  }
}
