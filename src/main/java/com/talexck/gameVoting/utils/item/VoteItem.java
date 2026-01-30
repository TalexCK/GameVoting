package com.talexck.gameVoting.utils.item;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

/**
 * Utility class for managing the vote hotbar item.
 * The item is placed in slot 8 (9th slot) and cannot be dropped.
 */
public class VoteItem {
    private static final int VOTE_ITEM_SLOT = 8; // 9th slot (0-indexed)
    private static NamespacedKey VOTE_ITEM_KEY;

    /**
     * Initialize the vote item system.
     *
     * @param plugin Plugin instance
     */
    public static void initialize(Plugin plugin) {
        VOTE_ITEM_KEY = new NamespacedKey(plugin, "vote_item");
    }

    /**
     * Give the insufficient players item to a player (red redstone block).
     *
     * @param player The player to give the item to
     */
    public static void giveInsufficientPlayersItem(Player player) {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&c&l✖ &4Insufficient Players"));
            meta.setLore(Arrays.asList(
                ChatColor.translateAlternateColorCodes('&', "&7Not enough players online"),
                ChatColor.translateAlternateColorCodes('&', "&7"),
                ChatColor.translateAlternateColorCodes('&', "&eRequires at least &66 players"),
                ChatColor.translateAlternateColorCodes('&', "&eAdmin can use &6/vote start &eto begin")
            ));

            // Add glowing effect
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Mark as insufficient players item
            meta.getPersistentDataContainer().set(VOTE_ITEM_KEY, PersistentDataType.STRING, "insufficient_players");

            item.setItemMeta(meta);
        }

        // Place in slot 8 (9th slot)
        player.getInventory().setItem(VOTE_ITEM_SLOT, item);
    }
    
    /**
     * Give the start voting trigger item to a player (green emerald).
     *
     * @param player The player to give the item to
     */
    public static void giveStartVotingItem(Player player) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&l✓ &2Start Voting"));
            meta.setLore(Arrays.asList(
                ChatColor.translateAlternateColorCodes('&', "&7Right-click to mark yourself ready"),
                ChatColor.translateAlternateColorCodes('&', "&7"),
                ChatColor.translateAlternateColorCodes('&', "&eVoting starts when all players are ready!")
            ));

            // Add glowing effect
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Mark as start voting item
            meta.getPersistentDataContainer().set(VOTE_ITEM_KEY, PersistentDataType.STRING, "start_voting");

            item.setItemMeta(meta);
        }

        // Place in slot 8 (9th slot)
        player.getInventory().setItem(VOTE_ITEM_SLOT, item);
    }

    /**
     * Update the start voting item to show "already ready" state.
     *
     * @param player The player whose item to update
     * @param isReady Whether the player is ready
     */
    public static void updateStartVotingItem(Player player, boolean isReady) {
        ItemStack item;
        ItemMeta meta;

        if (isReady) {
            item = new ItemStack(Material.GRAY_DYE);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&7&l✓ &8Ready to Start"));
                meta.setLore(Arrays.asList(
                    ChatColor.translateAlternateColorCodes('&', "&7You are ready!"),
                    ChatColor.translateAlternateColorCodes('&', "&7"),
                    ChatColor.translateAlternateColorCodes('&', "&7Waiting for other players...")
                ));
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                meta.getPersistentDataContainer().set(VOTE_ITEM_KEY, PersistentDataType.STRING, "start_voting");
                item.setItemMeta(meta);
            }
        } else {
            // Give back the normal start voting item
            giveStartVotingItem(player);
            return;
        }

        player.getInventory().setItem(VOTE_ITEM_SLOT, item);
    }
    
    /**
     * Give the voting item to a player (before voting starts).
     *
     * @param player The player to give the item to
     */
    public static void giveVotingItem(Player player) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&l➤ &6Game Voting"));
            meta.setLore(Arrays.asList(
                ChatColor.translateAlternateColorCodes('&', "&7Right-click to open voting menu"),
                ChatColor.translateAlternateColorCodes('&', "&7"),
                ChatColor.translateAlternateColorCodes('&', "&eClick to vote for games!")
            ));

            // Add glowing effect
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Mark as vote item
            meta.getPersistentDataContainer().set(VOTE_ITEM_KEY, PersistentDataType.STRING, "vote");

            item.setItemMeta(meta);
        }

        // Place in slot 8 (9th slot)
        player.getInventory().setItem(VOTE_ITEM_SLOT, item);
    }

    /**
     * Give the ready item to a player (after voting ends).
     *
     * @param player The player to give the item to
     */
    public static void giveReadyItem(Player player) {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&7&l✓ &8Ready Up"));
            meta.setLore(Arrays.asList(
                ChatColor.translateAlternateColorCodes('&', "&7Right-click to mark yourself as ready"),
                ChatColor.translateAlternateColorCodes('&', "&7"),
                ChatColor.translateAlternateColorCodes('&', "&eGame will start when everyone is ready!")
            ));

            // Add glowing effect
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Mark as ready item
            meta.getPersistentDataContainer().set(VOTE_ITEM_KEY, PersistentDataType.STRING, "ready");

            item.setItemMeta(meta);
        }

        // Place in slot 8 (9th slot)
        player.getInventory().setItem(VOTE_ITEM_SLOT, item);
    }

    /**
     * Update the ready item to show "already ready" state.
     *
     * @param player The player whose item to update
     */
    public static void updateReadyItem(Player player, boolean isReady) {
        ItemStack item;
        ItemMeta meta;

        if (isReady) {
            item = new ItemStack(Material.LIME_DYE);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&l✓ &2Already Ready"));
                meta.setLore(Arrays.asList(
                    ChatColor.translateAlternateColorCodes('&', "&aYou are ready!"),
                    ChatColor.translateAlternateColorCodes('&', "&7"),
                    ChatColor.translateAlternateColorCodes('&', "&7Waiting for other players...")
                ));
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                meta.getPersistentDataContainer().set(VOTE_ITEM_KEY, PersistentDataType.STRING, "ready");
                item.setItemMeta(meta);
            }
        } else {
            // Give back the normal ready item
            giveReadyItem(player);
            return;
        }

        player.getInventory().setItem(VOTE_ITEM_SLOT, item);
    }

    /**
     * Remove the vote item from a player.
     *
     * @param player The player to remove the item from
     */
    public static void removeVoteItem(Player player) {
        player.getInventory().setItem(VOTE_ITEM_SLOT, null);
    }

    /**
     * Check if an item is a vote item.
     *
     * @param item The item to check
     * @return true if the item is a vote item
     */
    public static boolean isVoteItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(VOTE_ITEM_KEY, PersistentDataType.STRING);
    }

    /**
     * Get the type of vote item.
     *
     * @param item The item to check
     * @return "vote", "ready", or null if not a vote item
     */
    public static String getVoteItemType(ItemStack item) {
        if (!isVoteItem(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        return meta.getPersistentDataContainer().get(VOTE_ITEM_KEY, PersistentDataType.STRING);
    }

    /**
     * Get the vote item slot number.
     *
     * @return Slot number (0-indexed)
     */
    public static int getVoteItemSlot() {
        return VOTE_ITEM_SLOT;
    }
}
