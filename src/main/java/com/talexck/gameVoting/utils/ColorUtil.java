package com.talexck.gameVoting.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Utility class for handling color codes and text formatting.
 * Converts legacy & color codes to Minecraft § codes.
 * 
 * Compatible with both legacy Paper (1.16.5) and modern Paper (1.20+).
 * Falls back to legacy color codes if MiniMessage is not available.
 */
public class ColorUtil {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final boolean MINI_MESSAGE_AVAILABLE;
    private static Object MINI_MESSAGE_INSTANCE;

    static {
        boolean available = false;
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            MINI_MESSAGE_INSTANCE = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
            available = true;
        } catch (ClassNotFoundException e) {
            MINI_MESSAGE_INSTANCE = null;
        }
        MINI_MESSAGE_AVAILABLE = available;
    }


    /**
     * Colorize a string by converting & codes to § codes and parsing MiniMessage format.
     *
     * Examples:
     * - "&aGreen &c&lRed Bold" → colored text
     * - "<#FF5555>Hex Color" → RGB color
     * - "<gradient:#FF0000:#0000FF>Gradient</gradient>" → gradient text
     *
     * @param message The message to colorize (can contain & codes or MiniMessage tags)
     * @return Adventure Component with colors applied
     */
    public static Component colorize(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        try {
            // First, convert & codes to § codes for legacy support
            String legacyConverted = message.replace('&', '§');

            // Check if MiniMessage is available and message contains MiniMessage tags
            if (MINI_MESSAGE_AVAILABLE && message.contains("<") && message.contains(">")) {
                // Parse as MiniMessage (supports hex, gradients, etc.)
                return ((net.kyori.adventure.text.minimessage.MiniMessage) MINI_MESSAGE_INSTANCE).deserialize(message);
            } else {
                // Parse as legacy format (§ codes)
                return LEGACY_SERIALIZER.deserialize(legacyConverted);
            }
        } catch (Exception e) {
            // Fallback to plain text if parsing fails
            return Component.text(message);
        }
    }

    /**
     * Colorize a string and return it as a plain text Component.
     * Same as colorize() but with a more explicit name.
     *
     * @param message The message to colorize
     * @return Adventure Component with colors applied
     */
    public static Component parse(String message) {
        return colorize(message);
    }

    /**
     * Strip all color codes from a string (both § and & codes).
     * Useful for logging or storing clean text.
     *
     * @param text The text to strip colors from
     * @return Plain text without color codes
     */
    public static String stripColors(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Remove § codes
        text = text.replaceAll("§[0-9a-fk-or]", "");
        // Remove & codes
        text = text.replaceAll("&[0-9a-fk-or]", "");
        // Remove MiniMessage tags (simple regex for common tags)
        text = text.replaceAll("<#[0-9A-Fa-f]{6}>", "");
        text = text.replaceAll("</?gradient[^>]*>", "");
        text = text.replaceAll("</?[a-z_]+>", "");

        return text;
    }

    /**
     * Convert a Component back to legacy format string with § codes.
     * Useful for storing colored text in configs.
     *
     * @param component The component to serialize
     * @return String with § color codes
     */
    public static String serialize(Component component) {
        if (component == null) {
            return "";
        }
        return LEGACY_SERIALIZER.serialize(component);
    }
}
