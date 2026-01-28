package com.talexck.gameVoting.utils.message;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.utils.ColorUtil;
import com.talexck.gameVoting.utils.language.LanguageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Map;

/**
 * Utility class for sending colored messages to players.
 * Automatically applies color code conversion via ColorUtil.
 *
 * Supports:
 * - Single and multiple line messages
 * - Broadcasting to all players
 * - Permission-based broadcasting
 * - Optional debug logging
 */
public class MessageUtil {

    /**
     * Send a colored message to a player.
     *
     * @param player The player to send the message to
     * @param message The message (supports & codes and MiniMessage format)
     */
    public static void sendMessage(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }

        Component component = ColorUtil.colorize(message);
        player.sendMessage(component);

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Sent message to %s: %s",
                            player.getName(), ColorUtil.stripColors(message))
            );
        }
    }

    /**
     * Send multiple colored messages to a player (one per line).
     *
     * @param player The player to send messages to
     * @param messages The messages to send
     */
    public static void sendMessage(Player player, String... messages) {
        if (player == null || messages == null || messages.length == 0) {
            return;
        }

        Arrays.stream(messages)
                .filter(msg -> msg != null && !msg.isEmpty())
                .forEach(msg -> sendMessage(player, msg));
    }

    /**
     * Broadcast a colored message to all online players.
     *
     * @param message The message to broadcast
     */
    public static void broadcast(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        Component component = ColorUtil.colorize(message);
        Bukkit.getServer().sendMessage(component);

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Broadcast message: %s",
                            ColorUtil.stripColors(message))
            );
        }
    }

    /**
     * Broadcast a colored message to all online players with a specific permission.
     *
     * @param message The message to broadcast
     * @param permission The permission node required to see the message
     */
    public static void broadcast(String message, String permission) {
        if (message == null || message.isEmpty() || permission == null) {
            return;
        }

        Component component = ColorUtil.colorize(message);
        int recipients = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                player.sendMessage(component);
                recipients++;
            }
        }

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Broadcast message to %d players with permission '%s': %s",
                            recipients, permission, ColorUtil.stripColors(message))
            );
        }
    }

    /**
     * Send a colored message to a player with a prefix.
     *
     * @param player The player to send the message to
     * @param prefix The prefix to add before the message
     * @param message The message content
     */
    public static void sendMessageWithPrefix(Player player, String prefix, String message) {
        if (player == null || message == null) {
            return;
        }

        String fullMessage = (prefix != null ? prefix + " " : "") + message;
        sendMessage(player, fullMessage);
    }

    /**
     * Send a translated message to a player.
     *
     * @param player The player to send the message to
     * @param key The translation key (e.g., "voting.not_active")
     */
    public static void sendTranslated(Player player, String key) {
        String message = LanguageManager.getInstance().getMessage(key);
        sendMessage(player, message);
    }

    /**
     * Send a translated message to a player with placeholders.
     *
     * @param player The player to send the message to
     * @param key The translation key
     * @param placeholders Map of placeholder keys to values
     */
    public static void sendTranslated(Player player, String key, Map<String, String> placeholders) {
        String message = LanguageManager.getInstance().getMessage(key, placeholders);
        sendMessage(player, message);
    }

    /**
     * Broadcast a translated message to all online players.
     *
     * @param key The translation key
     */
    public static void broadcastTranslated(String key) {
        String message = LanguageManager.getInstance().getMessage(key);
        broadcast(message);
    }

    /**
     * Broadcast a translated message to all online players with placeholders.
     *
     * @param key The translation key
     * @param placeholders Map of placeholder keys to values
     */
    public static void broadcastTranslated(String key, Map<String, String> placeholders) {
        String message = LanguageManager.getInstance().getMessage(key, placeholders);
        broadcast(message);
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
