package com.talexck.gameVoting.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/** Represents a game configuration that can be voted on. */
public class GameConfig {
  private static final int DEFAULT_MIN_PLAYERS = 1;
  private static final int DEFAULT_MAX_PLAYERS = 50;

  private final String id;
  private final String name;
  private final List<String> description;
  private final Material material;
  private final int customModelData;
  private final String serverId;
  private final String version;
  private final String minVersion;
  private final String maxVersion;
  private final int minPlayers;
  private final int maxPlayers;
  private final boolean solo;
  private final String soloMode;
  private final String soloStartup;
  private final int soloMaxPlayers;
  private final int soloRetentionDays;

  public GameConfig(
      String id,
      String name,
      List<String> description,
      Material material,
      int customModelData,
      String serverId) {
    this(
        id,
        name,
        description,
        material,
        customModelData,
        serverId,
        null,
        null,
        null,
        DEFAULT_MIN_PLAYERS,
        DEFAULT_MAX_PLAYERS,
        false,
        "shared",
        "on_demand",
        DEFAULT_MAX_PLAYERS,
        10);
  }

  public GameConfig(
      String id,
      String name,
      List<String> description,
      Material material,
      int customModelData,
      String serverId,
      String version) {
    this(
        id,
        name,
        description,
        material,
        customModelData,
        serverId,
        version,
        null,
        null,
        DEFAULT_MIN_PLAYERS,
        DEFAULT_MAX_PLAYERS,
        false,
        "shared",
        "on_demand",
        DEFAULT_MAX_PLAYERS,
        10);
  }

  public GameConfig(
      String id,
      String name,
      List<String> description,
      Material material,
      int customModelData,
      String serverId,
      String version,
      int minPlayers,
      int maxPlayers) {
    this(
        id,
        name,
        description,
        material,
        customModelData,
        serverId,
        version,
        null,
        null,
        minPlayers,
        maxPlayers,
        false,
        "shared",
        "on_demand",
        Math.max(1, Math.max(minPlayers, maxPlayers)),
        10);
  }

  public GameConfig(
      String id,
      String name,
      List<String> description,
      Material material,
      int customModelData,
      String serverId,
      String version,
      String minVersion,
      String maxVersion,
      int minPlayers,
      int maxPlayers) {
    this(
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
        false,
        "shared",
        "on_demand",
        Math.max(1, Math.max(minPlayers, maxPlayers)),
        10);
  }

  public GameConfig(
      String id,
      String name,
      List<String> description,
      Material material,
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
    this.id = id;
    this.name = name;
    this.description = new ArrayList<>(description);
    this.material = material;
    this.customModelData = customModelData;
    this.serverId = serverId;
    this.version = version;
    this.minVersion = minVersion;
    this.maxVersion = maxVersion;
    this.minPlayers = Math.max(1, minPlayers);
    this.maxPlayers = Math.max(this.minPlayers, maxPlayers);
    this.solo = solo;
    this.soloMode = normalizeChoice(soloMode, "solo mode", "shared", "player_world");
    this.soloStartup = normalizeChoice(soloStartup, "solo startup", "always", "on_demand");
    if (solo && soloMaxPlayers < this.minPlayers) {
      throw new IllegalArgumentException("solo max players must not be below min players");
    }
    if (soloRetentionDays < 1) {
      throw new IllegalArgumentException("solo retention days must be positive");
    }
    if (this.soloMode.equals("player_world") && !solo) {
      throw new IllegalArgumentException("player_world mode requires solo to be enabled");
    }
    if (this.soloMode.equals("player_world") && soloMaxPlayers > 2) {
      throw new IllegalArgumentException("player_world mode supports at most two players");
    }
    if (this.soloMode.equals("player_world") && !this.soloStartup.equals("on_demand")) {
      throw new IllegalArgumentException("player_world mode requires on_demand startup");
    }
    this.soloMaxPlayers = soloMaxPlayers;
    this.soloRetentionDays = soloRetentionDays;
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
   * Get the Scheduler server name for this game.
   *
   * @return The Scheduler server name, or null if not configured
   */
  public String getServerId() {
    return serverId;
  }

  /**
   * Get the expected client version for this game.
   *
   * @return Expected version string, null if unrestricted
   */
  public String getVersion() {
    return version;
  }

  public String getMinVersion() {
    return minVersion;
  }

  public String getMaxVersion() {
    return maxVersion;
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

  public boolean isSolo() {
    return solo;
  }

  public String getSoloMode() {
    return soloMode;
  }

  public String getSoloStartup() {
    return soloStartup;
  }

  public int getSoloMaxPlayers() {
    return soloMaxPlayers;
  }

  public int getSoloRetentionDays() {
    return soloRetentionDays;
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
    return "GameConfig{"
        + "id='"
        + id
        + '\''
        + ", name='"
        + name
        + '\''
        + ", material="
        + material
        + ", serverId='"
        + serverId
        + '\''
        + ", version='"
        + version
        + '\''
        + ", minVersion='"
        + minVersion
        + '\''
        + ", maxVersion='"
        + maxVersion
        + '\''
        + ", minPlayers="
        + minPlayers
        + ", maxPlayers="
        + maxPlayers
        + ", solo="
        + solo
        + ", soloMode='"
        + soloMode
        + '\''
        + ", soloStartup='"
        + soloStartup
        + '\''
        + ", soloMaxPlayers="
        + soloMaxPlayers
        + ", soloRetentionDays="
        + soloRetentionDays
        + '}';
  }

  private static String normalizeChoice(String value, String name, String first, String second) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must be " + first + " or " + second);
    }
    String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
    if (!normalized.equals(first) && !normalized.equals(second)) {
      throw new IllegalArgumentException(name + " must be " + first + " or " + second);
    }
    return normalized;
  }
}
