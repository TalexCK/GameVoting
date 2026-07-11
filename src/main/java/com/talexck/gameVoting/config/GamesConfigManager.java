package com.talexck.gameVoting.config;

import com.schedulerbridge.common.SchedulerGameDefinition;
import com.talexck.gameVoting.GameVoting;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class GamesConfigManager {
  private static final int DEFAULT_MIN_PLAYERS = 1;
  private static final int DEFAULT_MAX_PLAYERS = 50;
  private final GameVoting plugin;
  private final File configFile;
  private final boolean schedulerMode;
  private volatile List<GameConfig> catalog = List.of();

  public GamesConfigManager(GameVoting plugin) {
    this.plugin = plugin;
    this.configFile = new File(plugin.getDataFolder(), "games.yml");
    String mode = plugin.getConfig().getString("game-config-mode", "scheduler");
    if (!mode.equalsIgnoreCase("scheduler") && !mode.equalsIgnoreCase("file")) {
      throw new IllegalStateException("game-config-mode must be scheduler or file");
    }
    schedulerMode = mode.equalsIgnoreCase("scheduler");
    reload()
        .exceptionally(
            error -> {
              plugin.getLogger().severe("Failed to load game catalog: " + rootMessage(error));
              return null;
            });
  }

  public CompletableFuture<Integer> reload() {
    if (schedulerMode) {
      return plugin
          .getServerScheduler()
          .games()
          .thenApply(definitions -> replaceGames(loadSchedulerGames(definitions), "Scheduler"));
    }
    try {
      return CompletableFuture.completedFuture(replaceGames(loadFileGames(), "games.yml"));
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(error);
    }
  }

  public boolean isSchedulerMode() {
    return schedulerMode;
  }

  public List<GameConfig> getGames() {
    return ordinaryGames(catalog);
  }

  public List<GameConfig> getSoloGames() {
    return soloGames(catalog);
  }

  public List<GameConfig> getAvailableGames(int playerCount) {
    return getGames().stream().filter(game -> game.isAvailableForPlayerCount(playerCount)).toList();
  }

  public GameConfig getGame(String id) {
    return getGames().stream()
        .filter(game -> game.getId().equalsIgnoreCase(id))
        .findFirst()
        .orElse(null);
  }

  public GameConfig getSoloGame(String id) {
    return getSoloGames().stream()
        .filter(game -> game.getId().equalsIgnoreCase(id))
        .findFirst()
        .orElse(null);
  }

  public boolean isGameAvailable(String gameId, int playerCount) {
    GameConfig game = getGame(gameId);
    return game != null && game.isAvailableForPlayerCount(playerCount);
  }

  public int getGameCount() {
    return getGames().size();
  }

  public int getAvailableGameCount(int playerCount) {
    return getAvailableGames(playerCount).size();
  }

  private List<GameConfig> loadSchedulerGames(List<SchedulerGameDefinition> definitions) {
    List<GameConfig> loaded = new ArrayList<>();
    for (SchedulerGameDefinition definition : definitions) {
      loaded.add(
          createGame(
              definition.id(),
              definition.name(),
              definition.description(),
              definition.material(),
              definition.customModelData(),
              definition.serverId(),
              definition.version(),
              definition.minVersion(),
              definition.maxVersion(),
              definition.minPlayers(),
              definition.maxPlayers(),
              definition.solo(),
              definition.soloMode(),
              definition.soloStartup(),
              definition.soloMaxPlayers(),
              definition.soloRetentionDays()));
    }
    return loaded;
  }

  private List<GameConfig> loadFileGames() {
    if (!configFile.isFile()) {
      throw new IllegalStateException("games.yml is required in file mode");
    }
    FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
    List<?> entries = config.getList("games", Collections.emptyList());
    List<GameConfig> loaded = new ArrayList<>();
    for (Object entry : entries) {
      if (!(entry instanceof Map<?, ?> values)) {
        throw new IllegalStateException("games.yml contains an invalid game entry");
      }
      String id = requiredString(values, "id");
      String name = requiredString(values, "name");
      String serverId = requiredString(values, "server-id");
      loaded.add(
          createGame(
              id,
              name,
              stringList(values.get("description")),
              stringValue(values.get("material"), "STONE"),
              intValue(values.get("custom-model-data"), 0),
              serverId,
              nullableString(values.get("version")),
              nullableString(values.get("minVersion")),
              nullableString(values.get("maxVersion")),
              intValue(values.get("min_player"), DEFAULT_MIN_PLAYERS),
              intValue(values.get("max_player"), DEFAULT_MAX_PLAYERS),
              booleanValue(values.get("solo"), false),
              stringValue(values.get("solo-mode"), "shared"),
              stringValue(values.get("solo-startup"), "on_demand"),
              intValue(values.get("solo-max-players"), DEFAULT_MAX_PLAYERS),
              intValue(values.get("solo-retention-days"), 10)));
    }
    return loaded;
  }

  private GameConfig createGame(
      String id,
      String name,
      List<String> description,
      String materialName,
      int customModelData,
      String serverId,
      String version,
      String minVersion,
      String maxVersion,
      int minPlayers,
      int maxPlayers,
      boolean solo,
      String soloMode,
      String soloStartup,
      int soloMaxPlayers,
      int soloRetentionDays) {
    Material material = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
    if (material == null) {
      plugin
          .getLogger()
          .warning("Invalid material '" + materialName + "' for game '" + id + "', using STONE");
      material = Material.STONE;
    }
    return new GameConfig(
        id,
        name,
        description,
        material,
        customModelData,
        serverId,
        version,
        minVersion,
        maxVersion,
        minPlayers,
        maxPlayers,
        solo,
        soloMode,
        soloStartup,
        soloMaxPlayers,
        soloRetentionDays);
  }

  private int replaceGames(List<GameConfig> loaded, String source) {
    catalog = immutableCatalog(loaded);
    for (GameConfig game : loaded) {
      plugin
          .getLogger()
          .info(
              "Loaded game: "
                  + game.getId()
                  + " ("
                  + game.getName()
                  + ") [Scheduler Server: "
                  + game.getServerId()
                  + "]");
    }
    plugin.getLogger().info("Loaded " + loaded.size() + " game(s) from " + source);
    return loaded.size();
  }

  private static String requiredString(Map<?, ?> values, String key) {
    String value = nullableString(values.get(key));
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("game entry is missing " + key);
    }
    return value;
  }

  private static String stringValue(Object value, String defaultValue) {
    String text = nullableString(value);
    return text == null ? defaultValue : text;
  }

  private static String nullableString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static int intValue(Object value, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException error) {
      throw new IllegalStateException("game entry contains an invalid integer", error);
    }
  }

  private static boolean booleanValue(Object value, boolean defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    String text = String.valueOf(value).trim();
    if (text.equalsIgnoreCase("true")) {
      return true;
    }
    if (text.equalsIgnoreCase("false")) {
      return false;
    }
    throw new IllegalStateException("game entry contains an invalid boolean");
  }

  private static List<String> stringList(Object value) {
    if (!(value instanceof List<?> values)) {
      return Collections.emptyList();
    }
    return values.stream().map(String::valueOf).toList();
  }

  private static String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }

  static List<GameConfig> ordinaryGames(List<GameConfig> games) {
    return games.stream().filter(game -> !game.isSolo()).toList();
  }

  static List<GameConfig> soloGames(List<GameConfig> games) {
    return games.stream().filter(GameConfig::isSolo).toList();
  }

  static List<GameConfig> immutableCatalog(List<GameConfig> games) {
    return List.copyOf(games);
  }
}
