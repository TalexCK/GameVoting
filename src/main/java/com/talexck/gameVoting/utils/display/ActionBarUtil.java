package com.talexck.gameVoting.utils.display;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Utility class for sending action bar messages to players.
 * Action bars appear above the player's hotbar.
 *
 * Supports:
 * - Colored messages (& codes and MiniMessage format)
 * - Simple one-line text display
 * - Optional debug logging
 */
public class ActionBarUtil {

    /**
     * Send an action bar message to a player.
     * The message appears above the hotbar and fades after a few seconds.
     *
     * @param player The player to send the action bar to
     * @param message The message to display (supports & codes and MiniMessage format)
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }

        Component component = ColorUtil.colorize(message);
        player.sendActionBar(component);

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Sent action bar to %s: %s",
                            player.getName(), ColorUtil.stripColors(message))
            );
        }
    }

    /**
     * Send an action bar message to multiple players.
     *
     * @param message The message to display
     * @param players The players to send the action bar to
     */
    public static void sendActionBar(String message, Player... players) {
        if (message == null || message.isEmpty() || players == null) {
            return;
        }

        Component component = ColorUtil.colorize(message);
        for (Player player : players) {
            if (player != null) {
                player.sendActionBar(component);
            }
        }

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Sent action bar to %d players: %s",
                            players.length, ColorUtil.stripColors(message))
            );
        }
    }

    /**
     * Clear the action bar for a player by sending an empty message.
     *
     * @param player The player to clear the action bar for
     */
    public static void clearActionBar(Player player) {
        if (player == null) {
            return;
        }

        player.sendActionBar(Component.empty());

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Cleared action bar for %s", player.getName())
            );
        }
    }

    /**
     * Check if debug logging is enabled in the config.
     *
     * @return true if debug mode is enabled
     */
    private static boolean isDebugEnabled() {
        try {
            return GameVoting.getInstance().getConfig().getBoolean("debug", false);
        } catch (Exception e) {
            return false;
        }
    }
}
