package com.talexck.gameVoting.utils.display;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Utility class for sending titles to players.
 * Titles appear in the center of the player's screen.
 *
 * Supports:
 * - Main title and subtitle
 * - Custom fade-in, stay, and fade-out timings
 * - Color codes and MiniMessage format
 * - Clear title functionality
 */
public class TitleUtil {

    // Default timings (in ticks): 10 fade-in, 70 stay, 20 fade-out
    private static final int DEFAULT_FADE_IN = 10;
    private static final int DEFAULT_STAY = 70;
    private static final int DEFAULT_FADE_OUT = 20;

    /**
     * Send a title to a player with default timings.
     *
     * @param player The player to send the title to
     * @param title The main title text (null for no title)
     * @param subtitle The subtitle text (null for no subtitle)
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, DEFAULT_FADE_IN, DEFAULT_STAY, DEFAULT_FADE_OUT);
    }

    /**
     * Send a title to a player with custom timings.
     *
     * @param player The player to send the title to
     * @param title The main title text (null for no title)
     * @param subtitle The subtitle text (null for no subtitle)
     * @param fadeIn Fade-in time in ticks (20 ticks = 1 second)
     * @param stay Stay time in ticks
     * @param fadeOut Fade-out time in ticks
     */
    public static void sendTitle(Player player, String title, String subtitle,
                                   int fadeIn, int stay, int fadeOut) {
        if (player == null) {
            return;
        }

        Component titleComponent = (title != null && !title.isEmpty())
                ? ColorUtil.colorize(title)
                : Component.empty();

        Component subtitleComponent = (subtitle != null && !subtitle.isEmpty())
                ? ColorUtil.colorize(subtitle)
                : Component.empty();

        Title titleObject = Title.title(
                titleComponent,
                subtitleComponent,
                Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),  // Convert ticks to milliseconds
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L)
                )
        );

        player.showTitle(titleObject);

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Sent title to %s: '%s' | '%s'",
                            player.getName(),
                            ColorUtil.stripColors(title != null ? title : ""),
                            ColorUtil.stripColors(subtitle != null ? subtitle : ""))
            );
        }
    }

    /**
     * Send a title to a player with custom timings and clear existing title first.
     *
     * @param player The player to send the title to
     * @param title The main title text
     * @param subtitle The subtitle text
     * @param fadeIn Fade-in time in ticks
     * @param stay Stay time in ticks
     * @param fadeOut Fade-out time in ticks
     * @param clearExisting If true, clears any existing title before showing new one
     */
    public static void sendTitle(Player player, String title, String subtitle,
                                   int fadeIn, int stay, int fadeOut, boolean clearExisting) {
        if (clearExisting) {
            clearTitle(player);
        }
        sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * Send only a main title (no subtitle) to a player with default timings.
     *
     * @param player The player to send the title to
     * @param title The main title text
     */
    public static void sendTitle(Player player, String title) {
        sendTitle(player, title, null);
    }

    /**
     * Send only a subtitle (no main title) to a player with default timings.
     *
     * @param player The player to send the subtitle to
     * @param subtitle The subtitle text
     */
    public static void sendSubtitle(Player player, String subtitle) {
        sendTitle(player, null, subtitle);
    }

    /**
     * Clear any active title from a player's screen.
     *
     * @param player The player to clear the title from
     */
    public static void clearTitle(Player player) {
        if (player == null) {
            return;
        }

        player.clearTitle();

        if (isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Cleared title for %s", player.getName())
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
