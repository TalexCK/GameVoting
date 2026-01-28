package com.talexck.gameVoting.utils.hologram;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.api.hologram.HologramProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Arrays;
import java.util.List;

/**
 * Singleton manager for holograms.
 * Provides a simplified API for creating and managing persistent holograms.
 *
 * Features:
 * - Automatic provider selection (DecentHolograms or NoOp)
 * - Simplified hologram creation with varargs support
 * - Persistent holograms (saved to DecentHolograms config)
 * - Graceful degradation when DecentHolograms is not available
 */
public class HologramManager {

    private static HologramManager instance;
    private static boolean initialized = false;

    private HologramProvider provider;

    private HologramManager() {
        // Private constructor for singleton
    }

    /**
     * Initialize the HologramManager with the appropriate provider.
     * Should be called in the plugin's onEnable() method.
     *
     * @param plugin The plugin instance
     */
    public static void initialize(GameVoting plugin) {
        if (instance == null) {
            instance = new HologramManager();
        }

        // Check if DecentHolograms is available
        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") != null) {
            instance.provider = new DecentHologramsProvider();

            if (instance.provider.isAvailable()) {
                plugin.getLogger().info("DecentHolograms integration enabled!");
                initialized = true;
            } else {
                plugin.getLogger().warning("DecentHolograms found but not available, using NoOp provider");
                instance.provider = new NoOpHologramProvider();
                initialized = true;
            }
        } else {
            plugin.getLogger().info("DecentHolograms not found - hologram features disabled");
            instance.provider = new NoOpHologramProvider();
            initialized = true;
        }
    }

    /**
     * Get the singleton instance of HologramManager.
     *
     * @return The HologramManager instance, or null if not initialized
     */
    public static HologramManager getInstance() {
        if (!initialized) {
            throw new IllegalStateException("HologramManager has not been initialized! Call initialize() first.");
        }
        return instance;
    }

    /**
     * Check if the HologramManager has been initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Create a new persistent hologram at the specified location.
     *
     * @param id The unique identifier for the hologram
     * @param location The location where the hologram should appear
     * @param lines The lines of text to display (varargs for convenience)
     * @return true if the hologram was created successfully
     */
    public boolean createHologram(String id, Location location, String... lines) {
        return createHologram(id, location, Arrays.asList(lines));
    }

    /**
     * Create a new persistent hologram at the specified location.
     *
     * @param id The unique identifier for the hologram
     * @param location The location where the hologram should appear
     * @param lines The lines of text to display
     * @return true if the hologram was created successfully
     */
    public boolean createHologram(String id, Location location, List<String> lines) {
        if (provider == null) {
            return false;
        }

        boolean result = provider.createHologram(id, location, lines);

        if (result && isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Created hologram '%s' at %s with %d lines",
                            id, formatLocation(location), lines.size())
            );
        }

        return result;
    }

    /**
     * Update the lines of an existing hologram.
     *
     * @param id The unique identifier of the hologram
     * @param lines The new lines of text to display (varargs for convenience)
     * @return true if the hologram was updated successfully
     */
    public boolean updateLines(String id, String... lines) {
        return updateLines(id, Arrays.asList(lines));
    }

    /**
     * Update the lines of an existing hologram.
     *
     * @param id The unique identifier of the hologram
     * @param lines The new lines of text to display
     * @return true if the hologram was updated successfully
     */
    public boolean updateLines(String id, List<String> lines) {
        if (provider == null) {
            return false;
        }

        boolean result = provider.updateLines(id, lines);

        if (result && isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Updated hologram '%s' with %d lines", id, lines.size())
            );
        }

        return result;
    }

    /**
     * Delete a hologram.
     *
     * @param id The unique identifier of the hologram
     * @return true if the hologram was deleted successfully
     */
    public boolean deleteHologram(String id) {
        if (provider == null) {
            return false;
        }

        boolean result = provider.deleteHologram(id);

        if (result && isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Deleted hologram '%s'", id)
            );
        }

        return result;
    }

    /**
     * Check if a hologram exists.
     *
     * @param id The unique identifier of the hologram
     * @return true if the hologram exists
     */
    public boolean exists(String id) {
        return provider != null && provider.exists(id);
    }

    /**
     * Shutdown the manager and optionally delete all holograms.
     * Called on plugin disable.
     *
     * Note: Since holograms are persistent, they are NOT deleted on shutdown
     * unless explicitly requested. To clean up all holograms, call deleteAll() first.
     */
    public void shutdown() {
        if (isDebugEnabled() && provider != null) {
            GameVoting.getInstance().getLogger().info("[DEBUG] HologramManager shutdown complete");
        }
        // Do not delete holograms on shutdown - they are persistent
        // If cleanup is needed, call deleteAll() explicitly before shutdown
    }

    /**
     * Delete all holograms created by this plugin.
     * WARNING: This will permanently remove all GameVoting holograms from the config.
     *
     * @return The number of holograms deleted
     */
    public int deleteAll() {
        if (provider == null) {
            return 0;
        }

        int deleted = provider.deleteAll();

        if (deleted > 0 && isDebugEnabled()) {
            GameVoting.getInstance().getLogger().info(
                    String.format("[DEBUG] Deleted %d holograms", deleted)
            );
        }

        return deleted;
    }

    /**
     * Check if the hologram provider is available and functional.
     *
     * @return true if holograms can be created
     */
    public boolean isAvailable() {
        return provider != null && provider.isAvailable();
    }

    /**
     * Format a location for logging.
     *
     * @param location The location to format
     * @return Formatted location string
     */
    private String formatLocation(Location location) {
        if (location == null) {
            return "null";
        }
        return String.format("%s (%.1f, %.1f, %.1f)",
                location.getWorld() != null ? location.getWorld().getName() : "unknown",
                location.getX(), location.getY(), location.getZ());
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
