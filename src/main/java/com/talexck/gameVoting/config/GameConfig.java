package com.talexck.gameVoting.config;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a game configuration that can be voted on.
 */
public class GameConfig {
    private final String id;
    private final String name;
    private final List<String> description;
    private final Material material;
    private final int customModelData;
    private final String cloudnetTask;

    public GameConfig(String id, String name, List<String> description, Material material, int customModelData, String cloudnetTask) {
        this.id = id;
        this.name = name;
        this.description = new ArrayList<>(description);
        this.material = material;
        this.customModelData = customModelData;
        this.cloudnetTask = cloudnetTask;
    }

    /**
     * Get the unique identifier for this game.
     *
     * @return The game ID
     */
    public String getId() {
        return id;
    }

    /**
     * Get the display name of the game (supports color codes).
     *
     * @return The game name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the description lines for the game.
     *
     * @return List of description lines
     */
    public List<String> getDescription() {
        return new ArrayList<>(description);
    }

    /**
     * Get the material for the game's icon.
     *
     * @return The icon material
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * Get the custom model data for the icon (0 if not using custom models).
     *
     * @return The custom model data value
     */
    public int getCustomModelData() {
        return customModelData;
    }

    /**
     * Get the CloudNet task name for this game.
     *
     * @return The CloudNet task name, or null if not configured
     */
    public String getCloudnetTask() {
        return cloudnetTask;
    }

    @Override
    public String toString() {
        return "GameConfig{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", material=" + material +
                ", cloudnetTask='" + cloudnetTask + '\'' +
                '}';
    }
}
