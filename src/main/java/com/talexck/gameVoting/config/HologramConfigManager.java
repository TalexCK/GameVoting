package com.talexck.gameVoting.config;

import com.talexck.gameVoting.utils.hologram.HologramLocation;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manager for hologram location configuration.
 * Handles saving and loading hologram locations from config.yml.
 */
public class HologramConfigManager {
    private final Plugin plugin;
    private final List<HologramLocation> locations;

    public HologramConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.locations = new ArrayList<>();
        loadLocations();
    }

    /**
     * Load hologram locations from config.
     */
    public void loadLocations() {
        locations.clear();
        FileConfiguration config = plugin.getConfig();
        
        List<String> serializedLocations = config.getStringList("holograms.locations");
        for (String serialized : serializedLocations) {
            HologramLocation location = HologramLocation.deserialize(serialized);
            if (location != null) {
                locations.add(location);
            } else {
                plugin.getLogger().warning("Failed to parse hologram location: " + serialized);
            }
        }
        
        plugin.getLogger().info("Loaded " + locations.size() + " hologram locations");
    }

    /**
     * Save hologram locations to config.
     */
    public void saveLocations() {
        FileConfiguration config = plugin.getConfig();
        
        List<String> serializedLocations = locations.stream()
            .map(HologramLocation::serialize)
            .collect(Collectors.toList());
        
        config.set("holograms.locations", serializedLocations);
        plugin.saveConfig();
        
        plugin.getLogger().info("Saved " + locations.size() + " hologram locations");
    }

    /**
     * Add a hologram location.
     *
     * @param location Location to add
     * @return Index of the added location (0-based)
     */
    public int addLocation(HologramLocation location) {
        locations.add(location);
        saveLocations();
        return locations.size() - 1;
    }

    /**
     * Remove a hologram location by index.
     *
     * @param index Index to remove (0-based)
     * @return true if removed successfully
     */
    public boolean removeLocation(int index) {
        if (index < 0 || index >= locations.size()) {
            return false;
        }
        locations.remove(index);
        saveLocations();
        return true;
    }

    /**
     * Get a hologram location by index.
     *
     * @param index Index (0-based)
     * @return HologramLocation or null if invalid index
     */
    public HologramLocation getLocation(int index) {
        if (index < 0 || index >= locations.size()) {
            return null;
        }
        return locations.get(index);
    }

    /**
     * Get all hologram locations.
     *
     * @return List of all locations
     */
    public List<HologramLocation> getAllLocations() {
        return new ArrayList<>(locations);
    }

    /**
     * Get the number of hologram locations.
     *
     * @return Location count
     */
    public int getLocationCount() {
        return locations.size();
    }

    /**
     * Clear all hologram locations.
     */
    public void clearLocations() {
        locations.clear();
        saveLocations();
    }

    /**
     * Reload hologram locations from configuration.
     */
    public void reload() {
        loadLocations();
        plugin.getLogger().info("Reloaded hologram configuration");
    }
}
