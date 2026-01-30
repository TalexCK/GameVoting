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
        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.insufficient_players_name")));
            meta.setLore(Arrays.asList(
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.insufficient_players_lore_1")),
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.insufficient_players_lore_2")),
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.insufficient_players_lore_3")),
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.insufficient_players_lore_4"))
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
        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.start_voting_name")));
            meta.setLore(Arrays.asList(
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.start_voting_lore_1")),
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.start_voting_lore_2")),
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.start_voting_lore_3"))
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
        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
        ItemStack item;
        ItemMeta meta;

        if (isReady) {
            item = new ItemStack(Material.GRAY_DYE);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.already_ready_start_name")));
                meta.setLore(Arrays.asList(
                    ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.already_ready_start_lore_1")),
                    ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.already_ready_start_lore_2")),
                    ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.already_ready_start_lore_3"))
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
        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.voting_item_name")));
            meta.setLore(Arrays.asList(
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.voting_item_lore_1")),
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.voting_item_lore_2")),
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.voting_item_lore_3"))
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
        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.ready_item_name")));
            meta.setLore(Arrays.asList(
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.ready_item_lore_1")),
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.ready_item_lore_2")),
                ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.ready_item_lore_3"))
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
        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
        ItemStack item;
        ItemMeta meta;

        if (isReady) {
            item = new ItemStack(Material.LIME_DYE);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.already_ready_name")));
                meta.setLore(Arrays.asList(
                    ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.already_ready_lore_1")),
                    ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.already_ready_lore_2")),
                    ChatColor.translateAlternateColorCodes('&', langManager.getMessage("item.already_ready_lore_3"))
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
