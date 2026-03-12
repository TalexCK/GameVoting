package com.talexck.gameVoting.config;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a game configuration that can be voted on.
 */
public class GameConfig {
    private static final int DEFAULT_MIN_PLAYERS = 1;
    private static final int DEFAULT_MAX_PLAYERS = 50;

    private final String id;
    private final String name;
    private final List<String> description;
    private final Material material;
    private final int customModelData;
    private final String cloudnetTask;
    private final String version;
    private final boolean waitForBridgeReady;
    private final int expectedStartupSeconds;
    private final int minPlayers;
    private final int maxPlayers;

    public GameConfig(String id, String name, List<String> description, Material material, int customModelData, String cloudnetTask) {
        this(id, name, description, material, customModelData, cloudnetTask, null, true, 120, DEFAULT_MIN_PLAYERS, DEFAULT_MAX_PLAYERS);
    }

    public GameConfig(String id, String name, List<String> description, Material material, int customModelData, String cloudnetTask, String version) {
        this(id, name, description, material, customModelData, cloudnetTask, version, true, 120, DEFAULT_MIN_PLAYERS, DEFAULT_MAX_PLAYERS);
    }

    public GameConfig(
            String id,
            String name,
            List<String> description,
            Material material,
            int customModelData,
            String cloudnetTask,
            String version,
            boolean waitForBridgeReady,
            int expectedStartupSeconds
    ) {
        this(id, name, description, material, customModelData, cloudnetTask, version, waitForBridgeReady, expectedStartupSeconds,
            DEFAULT_MIN_PLAYERS, DEFAULT_MAX_PLAYERS);
    }

    public GameConfig(
            String id,
            String name,
            List<String> description,
            Material material,
            int customModelData,
            String cloudnetTask,
            String version,
            boolean waitForBridgeReady,
            int expectedStartupSeconds,
            int minPlayers,
            int maxPlayers
    ) {
        this.id = id;
        this.name = name;
        this.description = new ArrayList<>(description);
        this.material = material;
        this.customModelData = customModelData;
        this.cloudnetTask = cloudnetTask;
        this.version = version;
        this.waitForBridgeReady = waitForBridgeReady;
        this.expectedStartupSeconds = Math.max(1, expectedStartupSeconds);
        this.minPlayers = Math.max(1, minPlayers);
        this.maxPlayers = Math.max(this.minPlayers, maxPlayers);
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

    /**
     * Get the expected client version for this game.
     *
     * @return Expected version string, null if unrestricted
     */
    public String getVersion() {
        return version;
    }

    /**
     * Whether teleport should wait for bridge-ready state.
     *
     * @return true to wait for bridge-ready, false to use expected startup seconds
     */
    public boolean isWaitForBridgeReady() {
        return waitForBridgeReady;
    }

    /**
     * Expected startup seconds used when bridge-ready waiting is disabled.
     *
     * @return startup delay seconds, minimum 1
     */
    public int getExpectedStartupSeconds() {
        return expectedStartupSeconds;
    }

    /**
     * Minimum lobby players required before this game becomes votable.
     *
     * @return minimum player count
     */
    public int getMinPlayers() {
        return minPlayers;
    }

    /**
     * Maximum lobby players allowed before this game becomes unavailable.
     *
     * @return maximum player count
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Whether the current lobby size can vote for this game.
     *
     * @param playerCount current online player count
     * @return true when within configured range
     */
    public boolean isAvailableForPlayerCount(int playerCount) {
        return playerCount >= minPlayers && playerCount <= maxPlayers;
    }

    @Override
    public String toString() {
        return "GameConfig{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", material=" + material +
                ", cloudnetTask='" + cloudnetTask + '\'' +
                ", version='" + version + '\'' +
                ", waitForBridgeReady=" + waitForBridgeReady +
                ", expectedStartupSeconds=" + expectedStartupSeconds +
                ", minPlayers=" + minPlayers +
                ", maxPlayers=" + maxPlayers +
                '}';
    }
}
