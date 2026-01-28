package com.talexck.gameVoting.api.hologram;

import org.bukkit.Location;

import java.util.List;

/**
 * Interface for hologram providers.
 * Allows abstraction between different hologram implementations.
 *
 * Implementations:
 * - DecentHologramsProvider (when DecentHolograms is available)
 * - NoOpHologramProvider (when DecentHolograms is not available)
 */
public interface HologramProvider {

    /**
     * Create a new persistent hologram at the specified location.
     *
     * @param id The unique identifier for the hologram (will be namespaced with "gamevoting_")
     * @param location The location where the hologram should appear
     * @param lines The lines of text to display (supports color codes)
     * @return true if the hologram was created successfully, false otherwise
     */
    boolean createHologram(String id, Location location, List<String> lines);

    /**
     * Update the lines of an existing hologram.
     *
     * @param id The unique identifier of the hologram
     * @param lines The new lines of text to display
     * @return true if the hologram was updated successfully, false otherwise
     */
    boolean updateLines(String id, List<String> lines);

    /**
     * Delete a hologram.
     *
     * @param id The unique identifier of the hologram
     * @return true if the hologram was deleted successfully, false otherwise
     */
    boolean deleteHologram(String id);

    /**
     * Check if a hologram exists.
     *
     * @param id The unique identifier of the hologram
     * @return true if the hologram exists, false otherwise
     */
    boolean exists(String id);

    /**
     * Delete all holograms created by this plugin.
     * Used during plugin shutdown or cleanup.
     *
     * @return The number of holograms deleted
     */
    int deleteAll();

    /**
     * Check if this provider is available and functional.
     *
     * @return true if the provider is available, false otherwise
     */
    boolean isAvailable();
}
