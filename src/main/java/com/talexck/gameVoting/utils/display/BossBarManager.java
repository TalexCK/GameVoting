package com.talexck.gameVoting.utils.display;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.utils.ColorUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for player boss bars.
 * Handles creation, display, update, and cleanup of boss bars.
 *
 * Features:
 * - Per-player boss bar tracking
 * - Timed boss bars with automatic removal
 * - Thread-safe operations
 * - Auto-cleanup on player disconnect
 * - Color and style support
 */
public class BossBarManager {

    private static BossBarManager instance;

    // Map of player UUID to their active boss bar
    private final Map<UUID, BossBar> activeBars;
    // Map of player UUID to scheduled removal task for timed boss bars
    private final Map<UUID, BukkitTask> scheduledTasks;

    private BossBarManager() {
        this.activeBars = new ConcurrentHashMap<>();
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    /**
     * Get the singleton instance of BossBarManager.
     *
     * @return The BossBarManager instance
     */
    public static BossBarManager getInstance() {
        if (instance == null) {
            instance = new BossBarManager();
        }
        return instance;
    }

    /**
     * Show a boss bar to a player with specified text, color, and progress.
     *
     * @param player The player to show the boss bar to
     * @param text The boss bar text (supports & codes and MiniMessage)
     * @param color The boss bar color
     * @param progress The progress (0.0 to 1.0)
     */
    public void showBar(Player player, String text, BossBar.Color color, float progress) {
        if (player == null || text == null) {
            return;
        }

        // Remove existing boss bar if present
        removeBar(player);

        // Create new boss bar
        Component component = ColorUtil.colorize(text);
        BossBar bossBar = BossBar.bossBar(
                component,
                Math.max(0.0f, Math.min(1.0f, progress)), // Clamp between 0 and 1
                color,
                BossBar.Overlay.PROGRESS
        );

        // Show boss bar to player
        player.showBossBar(bossBar);
        activeBars.put(player.getUniqueId(), bossBar);

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Showed boss bar to %s: %s (%.0f%%)",
                            player.getName(), ColorUtil.stripColors(text), progress * 100)
            );
        }
    }

    /**
     * Show a timed boss bar that automatically removes after a duration.
     *
     * @param player The player to show the boss bar to
     * @param text The boss bar text
     * @param color The boss bar color
     * @param durationSeconds Duration in seconds before auto-removal
     */
    public void showTimedBar(Player player, String text, BossBar.Color color, int durationSeconds) {
        if (player == null || text == null || durationSeconds <= 0) {
            return;
        }

        // Show boss bar with full progress
        showBar(player, text, color, 1.0f);

        // Schedule automatic removal
        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                GameVoting.getInstance(),
                () -> removeBar(player),
                durationSeconds * 20L // Convert seconds to ticks
        );

        scheduledTasks.put(player.getUniqueId(), task);

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Showed timed boss bar to %s for %d seconds",
                            player.getName(), durationSeconds)
            );
        }
    }

    /**
     * Update an existing boss bar's text and progress for a player.
     *
     * @param player The player whose boss bar to update
     * @param text The new boss bar text
     * @param progress The new progress (0.0 to 1.0)
     */
    public void updateBar(Player player, String text, float progress) {
        if (player == null) {
            return;
        }

        BossBar bossBar = activeBars.get(player.getUniqueId());
        if (bossBar == null) {
            // No active boss bar, create a new one
            showBar(player, text, BossBar.Color.WHITE, progress);
            return;
        }

        // Update existing boss bar
        if (text != null) {
            bossBar.name(ColorUtil.colorize(text));
        }
        bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Updated boss bar for %s: %.0f%%",
                            player.getName(), progress * 100)
            );
        }
    }

    /**
     * Update only the progress of a player's boss bar.
     *
     * @param player The player whose boss bar to update
     * @param progress The new progress (0.0 to 1.0)
     */
    public void updateProgress(Player player, float progress) {
        if (player == null) {
            return;
        }

        BossBar bossBar = activeBars.get(player.getUniqueId());
        if (bossBar != null) {
            bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
        }
    }

    /**
     * Update only the text of a player's boss bar.
     *
     * @param player The player whose boss bar to update
     * @param text The new boss bar text
     */
    public void updateText(Player player, String text) {
        if (player == null || text == null) {
            return;
        }

        BossBar bossBar = activeBars.get(player.getUniqueId());
        if (bossBar != null) {
            bossBar.name(ColorUtil.colorize(text));
        }
    }

    /**
     * Remove the boss bar from a player.
     *
     * @param player The player to remove the boss bar from
     */
    public void removeBar(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // Cancel any scheduled removal task
        BukkitTask task = scheduledTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        // Hide and remove boss bar
        BossBar bossBar = activeBars.remove(uuid);
        if (bossBar != null) {
            player.hideBossBar(bossBar);

            if (isDebugEnabled()) {
                GameVoting.getInstance().getLogger().info(
                        String.format("[DEBUG] Removed boss bar from %s", player.getName())
                );
            }
        }
    }

    /**
     * Check if a player has an active boss bar.
     *
     * @param player The player to check
     * @return true if the player has an active boss bar
     */
    public boolean hasBar(Player player) {
        return player != null && activeBars.containsKey(player.getUniqueId());
    }

    /**
     * Shutdown the manager and clean up all active boss bars.
     * Called on plugin disable.
     */
    public void shutdown() {
        // Cancel all scheduled tasks
        scheduledTasks.values().forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        scheduledTasks.clear();

        // Remove all boss bars
        for (Player player : Bukkit.getOnlinePlayers()) {
            BossBar bossBar = activeBars.get(player.getUniqueId());
            if (bossBar != null) {
                player.hideBossBar(bossBar);
            }
        }
        activeBars.clear();

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info("[DEBUG] BossBarManager shutdown complete");
        }
    }

    /**
     * Check if debug logging is enabled in the config.
     *
     * @return true if debug mode is enabled
     */
    private boolean isDebugEnabled() {
        try {
            return GameVoting.getInstance().getConfig().getBoolean("debug", false);
        } catch (Exception e) {
            return false;
        }
    }
}
