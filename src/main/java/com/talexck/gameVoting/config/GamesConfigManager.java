package com.talexck.gameVoting.config;

import com.talexck.gameVoting.GameVoting;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manager for loading and managing game configurations from games.yml.
 */
public class GamesConfigManager {
    private static final int DEFAULT_MIN_PLAYERS = 1;
    private static final int DEFAULT_MAX_PLAYERS = 50;

    private final GameVoting plugin;
    private final File configFile;
    private FileConfiguration config;
    private final List<GameConfig> games;

    public GamesConfigManager(GameVoting plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "games.yml");
        this.games = new ArrayList<>();

        loadConfig();
        loadGames();
    }

    /**
     * Load or create the games.yml configuration file.
     */
    private void loadConfig() {
        // Create plugin data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Copy default config from resources if it doesn't exist
        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource("games.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                    plugin.getLogger().info("Created default games.yml configuration");
                } else {
                    // If resource doesn't exist, create a basic default
                    createDefaultConfig();
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to copy default games.yml: " + e.getMessage());
                createDefaultConfig();
            }
        }

        // Load the configuration
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Create a default games.yml configuration programmatically.
     */
    private void createDefaultConfig() {
        config = new YamlConfiguration();

        // Create default games list
        List<Object> defaultGames = new ArrayList<>();

        // BedWars
        ConfigurationSection bedwars = config.createSection("temp_bedwars");
        bedwars.set("id", "bedwars");
        bedwars.set("name", "&e&lBedWars");
        bedwars.set("description", List.of(
            "&7Classic bed defense game",
            "&7Protect your bed and destroy enemy beds"
        ));
        bedwars.set("material", "RED_BED");
        bedwars.set("custom-model-data", 0);
        bedwars.set("version", "1.20.1");
        bedwars.set("wait-for-bridge-ready", true);
        bedwars.set("expected-startup-seconds", 120);
        bedwars.set("min_player", DEFAULT_MIN_PLAYERS);
        bedwars.set("max_player", DEFAULT_MAX_PLAYERS);
        defaultGames.add(bedwars);

        // SkyWars
        ConfigurationSection skywars = config.createSection("temp_skywars");
        skywars.set("id", "skywars");
        skywars.set("name", "&b&lSkyWars");
        skywars.set("description", List.of(
            "&7Battle on floating islands",
            "&7Last player standing wins"
        ));
        skywars.set("material", "GRASS_BLOCK");
        skywars.set("custom-model-data", 0);
        skywars.set("version", "1.20.1");
        skywars.set("wait-for-bridge-ready", true);
        skywars.set("expected-startup-seconds", 120);
        skywars.set("min_player", DEFAULT_MIN_PLAYERS);
        skywars.set("max_player", DEFAULT_MAX_PLAYERS);
        defaultGames.add(skywars);

        config.set("games", defaultGames);

        try {
            config.save(configFile);
            plugin.getLogger().info("Created default games.yml configuration");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save default games.yml: " + e.getMessage());
        }
    }

    /**
     * Load all game configurations from the config file.
     */
    private void loadGames() {
        games.clear();

        if (!config.contains("games")) {
            plugin.getLogger().warning("No games defined in games.yml");
            return;
        }

        List<?> gamesList = config.getList("games");
        if (gamesList == null || gamesList.isEmpty()) {
            plugin.getLogger().warning("Games list is empty in games.yml");
            return;
        }

        for (Object obj : gamesList) {
            ConfigurationSection section;
            
            // Handle both Map and ConfigurationSection types
            if (obj instanceof Map) {
                // Convert Map to ConfigurationSection
                section = config.createSection("temp_" + System.nanoTime());
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) obj;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    section.set(entry.getKey(), entry.getValue());
                }
            } else if (obj instanceof ConfigurationSection) {
                section = (ConfigurationSection) obj;
            } else {
                plugin.getLogger().warning("Invalid game configuration entry type: " + obj.getClass().getName());
                continue;
            }

            try {
                String id = section.getString("id");
                String name = section.getString("name");
                List<String> description = section.getStringList("description");
                String materialName = section.getString("material", "STONE");
                int customModelData = section.getInt("custom-model-data", 0);
                String cloudnetTask = section.getString("cloudnet-task");
                String version = section.getString("version");
                boolean waitForBridgeReady = parseBooleanConfig(section, "wait-for-bridge-ready", true, id);
                int expectedStartupSeconds = Math.max(1, parseIntConfig(section, "expected-startup-seconds", 120, id));
                int minPlayers = Math.max(1, parseIntConfig(section, "min_player", DEFAULT_MIN_PLAYERS, id));
                int maxPlayers = parseIntConfig(section, "max_player", DEFAULT_MAX_PLAYERS, id);
                if (!waitForBridgeReady && !section.contains("expected-startup-seconds")) {
                    plugin.getLogger().warning("Game '" + id + "' disabled bridge-ready but missing expected-startup-seconds, defaulting to 120");
                }
                if (maxPlayers < minPlayers) {
                    plugin.getLogger().warning("Game '" + id + "' has max_player < min_player, adjusting max_player to " + minPlayers);
                    maxPlayers = minPlayers;
                }

                // Validate required fields
                if (id == null || id.isEmpty()) {
                    plugin.getLogger().warning("Game missing ID, skipping");
                    continue;
                }

                if (name == null || name.isEmpty()) {
                    plugin.getLogger().warning("Game '" + id + "' missing name, skipping");
                    continue;
                }

                // Parse material
                Material material;
                try {
                    material = Material.valueOf(materialName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material '" + materialName + "' for game '" + id + "', using STONE");
                    material = Material.STONE;
                }

                // Create game config
                GameConfig game = new GameConfig(
                    id,
                    name,
                    description,
                    material,
                    customModelData,
                    cloudnetTask,
                    version,
                    waitForBridgeReady,
                    expectedStartupSeconds,
                    minPlayers,
                    maxPlayers
                );
                games.add(game);

                plugin.getLogger().info("Loaded game: " + id + " (" + name + ")" +
                    (cloudnetTask != null ? " [CloudNet Task: " + cloudnetTask + "]" : ""));

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load game configuration: " + e.getMessage());
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("Loaded " + games.size() + " game(s) from configuration");
    }

    /**
     * Get all loaded game configurations.
     *
     * @return List of game configurations
     */
    public List<GameConfig> getGames() {
        return new ArrayList<>(games);
    }

    /**
     * Get all games that are votable for the current lobby size.
     *
     * @param playerCount current online player count
     * @return votable games
     */
    public List<GameConfig> getAvailableGames(int playerCount) {
        return games.stream()
                .filter(game -> game.isAvailableForPlayerCount(playerCount))
                .toList();
    }

    /**
     * Get a game configuration by its ID.
     *
     * @param id The game ID
     * @return The game configuration, or null if not found
     */
    public GameConfig getGame(String id) {
        return games.stream()
                .filter(game -> game.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Whether a game can be voted for with the current lobby size.
     *
     * @param gameId game id
     * @param playerCount current online player count
     * @return true if available
     */
    public boolean isGameAvailable(String gameId, int playerCount) {
        GameConfig game = getGame(gameId);
        return game != null && game.isAvailableForPlayerCount(playerCount);
    }

    /**
     * Reload the games configuration from disk.
     */
    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
        loadGames();
        plugin.getLogger().info("Reloaded games configuration");
    }

    /**
     * Get the number of loaded games.
     *
     * @return The game count
     */
    public int getGameCount() {
        return games.size();
    }

    /**
     * Get the number of votable games for the current lobby size.
     *
     * @param playerCount current online player count
     * @return votable game count
     */
    public int getAvailableGameCount(int playerCount) {
        return getAvailableGames(playerCount).size();
    }

    /**
     * Parse boolean from config path with string compatibility.
     * Accepts true/false boolean and "true"/"false" string values.
     */
    private boolean parseBooleanConfig(ConfigurationSection section, String path, boolean defaultValue, String gameId) {
        if (!section.contains(path)) {
            plugin.getLogger().warning("Game '" + gameId + "' missing " + path + ", defaulting to " + defaultValue);
            return defaultValue;
        }

        Object value = section.get(path);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if ("true".equalsIgnoreCase(normalized)) {
                return true;
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return false;
            }
        }

        plugin.getLogger().warning("Game '" + gameId + "' has invalid " + path + "='" + value + "', defaulting to " + defaultValue);
        return defaultValue;
    }

    /**
     * Parse integer from config path with string compatibility.
     * Accepts numeric values and numeric strings.
     */
    private int parseIntConfig(ConfigurationSection section, String path, int defaultValue, String gameId) {
        if (!section.contains(path)) {
            return defaultValue;
        }

        Object value = section.get(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                // fall through to warning below
            }
        }

        plugin.getLogger().warning("Game '" + gameId + "' has invalid " + path + "='" + value + "', defaulting to " + defaultValue);
        return defaultValue;
    }
}
