package com.talexck.gameVoting.ui;

import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.utils.ColorUtil;
import com.talexck.gameVoting.utils.gui.ClickableItem;
import com.talexck.gameVoting.utils.gui.ChestUI;
import com.talexck.gameVoting.utils.gui.ChestUIListener;
import com.talexck.gameVoting.utils.message.MessageUtil;
import com.talexck.gameVoting.voting.VotingSession;
import com.talexck.gameVoting.voting.VoteResult;
import com.talexck.gameVoting.utils.display.ActionBarUtil;
import org.bukkit.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom voting UI with border, pagination, and close button.
 * Layout: 6 rows with border, content area in center (28 items per page),
 * and navigation buttons at bottom (prev, close, next).
 */
public class VotingUI extends ChestUI {
    private static final int ROWS = 6;
    private static final int ITEMS_PER_PAGE = 28; // 7 columns × 4 rows

    // Content area slots (excluding border)
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,  // Row 1
        19, 20, 21, 22, 23, 24, 25,  // Row 2
        28, 29, 30, 31, 32, 33, 34,  // Row 3
        37, 38, 39, 40, 41, 42, 43   // Row 4
    };

    // Navigation slots
    private static final int PREV_SLOT = 46;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 52;

    private final Player player;
    private final GamesConfigManager gamesManager;
    private final List<GameConfig> games;
    private int currentPage;

    public VotingUI(Player player, GamesConfigManager gamesManager) {
        super(ColorUtil.stripColors(com.talexck.gameVoting.utils.language.LanguageManager.getInstance().getMessage("ui.voting_title")), ROWS);
        this.player = player;
        this.gamesManager = gamesManager;
        this.games = gamesManager.getGames();
        this.currentPage = 0;

        setupUI();
    }

    /**
     * Set up the UI with border, content, and navigation.
     */
    private void setupUI() {
        // Add border
        createBorder();

        // Add content items
        updateContent();

        // Add navigation buttons
        updateNavigation();
    }

    /**
     * Create the border around the UI.
     */
    private void createBorder() {
        ItemStack borderItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = borderItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            borderItem.setItemMeta(meta);
        }

        // Top row (0-8)
        for (int i = 0; i < 9; i++) {
            setItem(i, ClickableItem.of(borderItem, p -> {}));
        }

        // Bottom row (45-53)
        for (int i = 45; i < 54; i++) {
            // Skip navigation slots
            if (i != PREV_SLOT && i != CLOSE_SLOT && i != NEXT_SLOT) {
                setItem(i, ClickableItem.of(borderItem, p -> {}));
            }
        }

        // Left and right columns
        for (int row = 1; row < 5; row++) {
            int leftSlot = row * 9;
            int rightSlot = row * 9 + 8;
            setItem(leftSlot, ClickableItem.of(borderItem, p -> {}));
            setItem(rightSlot, ClickableItem.of(borderItem, p -> {}));
        }
    }

    /**
     * Update the content area with game items for the current page.
     */
    private void updateContent() {
        // Clear content area
        for (int slot : CONTENT_SLOTS) {
            getInventory().setItem(slot, new ItemStack(Material.AIR));
        }

        // Calculate page bounds
        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, games.size());

        // Add game items
        for (int i = start; i < end; i++) {
            GameConfig game = games.get(i);
            int slotIndex = i - start;
            int slot = CONTENT_SLOTS[slotIndex];

            ItemStack item = createGameItem(game);
            setItem(slot, ClickableItem.of(item, p -> handleVote(game)));
        }
    }

    /**
     * Create an item stack for a game.
     *
     * @param game The game configuration
     * @return The item stack
     */
    private ItemStack createGameItem(GameConfig game) {
        ItemStack item = new ItemStack(game.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Set display name
            meta.displayName(ColorUtil.colorize(game.getName()));

            // Set lore (description + vote indicator)
            List<Component> lore = new ArrayList<>();
            for (String line : game.getDescription()) {
                lore.add(ColorUtil.colorize(line));
            }

            // Add voting indicator if player voted for this game
            VotingSession session = VotingSession.getInstance();
            boolean voted = session.hasVotedFor(player, game.getId());
            int voteCount = session.getPlayerVoteCount(player);

            var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();

            lore.add(Component.text(""));
            if (voted) {
                lore.add(ColorUtil.colorize(langManager.getMessage("ui.voted_indicator")));
            } else {
                if (voteCount < 3) {
                    lore.add(ColorUtil.colorize(langManager.getMessage("ui.click_to_vote")));
                } else {
                    lore.add(ColorUtil.colorize(langManager.getMessage("ui.vote_limit_reached")));
                }
            }
            lore.add(Component.text(""));
            Map<String, String> votePlaceholders = new HashMap<>();
            votePlaceholders.put("count", String.valueOf(voteCount));
            lore.add(ColorUtil.colorize(langManager.getMessage("ui.your_votes", votePlaceholders)));

            meta.lore(lore);

            // Set custom model data if specified
            if (game.getCustomModelData() > 0) {
                meta.setCustomModelData(game.getCustomModelData());
            }

            // Add enchantment glint if player voted for this game
            if (voted) {
                meta.addEnchant(Enchantment.MENDING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Handle a player voting for a game.
     *
     * @param game The game being voted for
     */
    private void handleVote(GameConfig game) {
        VotingSession session = VotingSession.getInstance();
        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();

        if (!session.isActive()) {
            MessageUtil.sendMessage(player, langManager.getMessage("ui.voting_inactive"));
            player.closeInventory();
            return;
        }

        // Record the vote (toggle behavior)
        VoteResult result = session.vote(player, game);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("game", game.getName());

        // Handle result and send feedback
        switch (result) {
            case ADDED:
                MessageUtil.sendMessage(player, langManager.getMessage("ui.vote_added", placeholders));
                ActionBarUtil.sendActionBar(player, langManager.getMessage("ui.vote_added", placeholders));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                break;
            case REMOVED:
                MessageUtil.sendMessage(player, langManager.getMessage("ui.vote_removed", placeholders));
                ActionBarUtil.sendActionBar(player, langManager.getMessage("ui.vote_removed", placeholders));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                break;
            case LIMIT_REACHED:
                MessageUtil.sendMessage(player, langManager.getMessage("ui.vote_limit"));
                ActionBarUtil.sendActionBar(player, langManager.getMessage("ui.vote_limit"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                break;
            case SESSION_INACTIVE:
                MessageUtil.sendMessage(player, langManager.getMessage("ui.voting_inactive"));
                player.closeInventory();
                return;
        }

        // Refresh the UI to show updated vote indicator
        updateContent();
        player.updateInventory();
    }

    /**
     * Update the navigation buttons.
     */
    private void updateNavigation() {
        int totalPages = getTotalPages();
        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();

        // Previous button
        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta meta = prevButton.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtil.colorize(langManager.getMessage("ui.prev_page")));
                prevButton.setItemMeta(meta);
            }
            setItem(PREV_SLOT, ClickableItem.of(prevButton, p -> {
                currentPage--;
                updateContent();
                updateNavigation();
                p.updateInventory();
            }));
        } else {
            ItemStack disabled = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = disabled.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(" "));
                disabled.setItemMeta(meta);
            }
            setItem(PREV_SLOT, ClickableItem.of(disabled, p -> {}));
        }

        // Close button
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(ColorUtil.colorize(langManager.getMessage("ui.close_button")));
            closeButton.setItemMeta(closeMeta);
        }
        setItem(CLOSE_SLOT, ClickableItem.of(closeButton, Player::closeInventory));

        // Next button
        if (currentPage < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta meta = nextButton.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtil.colorize(langManager.getMessage("ui.next_page")));
                nextButton.setItemMeta(meta);
            }
            setItem(NEXT_SLOT, ClickableItem.of(nextButton, p -> {
                currentPage++;
                updateContent();
                updateNavigation();
                p.updateInventory();
            }));
        } else {
            ItemStack disabled = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = disabled.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(" "));
                disabled.setItemMeta(meta);
            }
            setItem(NEXT_SLOT, ClickableItem.of(disabled, p -> {}));
        }
    }

    /**
     * Get the total number of pages.
     *
     * @return The page count
     */
    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil((double) games.size() / ITEMS_PER_PAGE));
    }
}
