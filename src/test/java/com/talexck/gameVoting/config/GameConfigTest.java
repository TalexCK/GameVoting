package com.talexck.gameVoting.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GameConfigTest {

  @Test
  @DisplayName("未配置人数范围时应使用默认值")
  void shouldUseDefaultPlayerRange() {
    GameConfig config =
        new GameConfig("bedwars", "BedWars", List.of("desc"), Material.RED_BED, 0, "BedWars");

    assertEquals(1, config.getMinPlayers());
    assertEquals(50, config.getMaxPlayers());
    assertTrue(config.isAvailableForPlayerCount(1));
    assertTrue(config.isAvailableForPlayerCount(50));
    assertFalse(config.isAvailableForPlayerCount(51));
  }

  @Test
  @DisplayName("应正确判断自定义人数范围")
  void shouldRespectCustomPlayerRange() {
    GameConfig config =
        new GameConfig(
            "skywars",
            "SkyWars",
            List.of("desc"),
            Material.GRASS_BLOCK,
            0,
            "SkyWars",
            "1.21.1",
            2,
            8);

    assertFalse(config.isAvailableForPlayerCount(1));
    assertTrue(config.isAvailableForPlayerCount(2));
    assertTrue(config.isAvailableForPlayerCount(8));
    assertFalse(config.isAvailableForPlayerCount(9));
  }

  @Test
  @DisplayName("应保存准备阶段客户端版本范围")
  void shouldKeepReadyVersionRange() {
    GameConfig config =
        new GameConfig(
            "parkour",
            "Parkour",
            List.of("desc"),
            Material.FEATHER,
            0,
            "Parkour",
            null,
            "26.1",
            "26.2",
            1,
            12);

    assertEquals("26.1", config.getMinVersion());
    assertEquals("26.2", config.getMaxVersion());
  }

  @Test
  @DisplayName("Should keep solo games out of the voting catalog")
  void shouldPartitionSoloAndVotingGames() {
    GameConfig voting =
        new GameConfig("bedwars", "BedWars", List.of(), Material.RED_BED, 0, "BedWars");
    GameConfig solo =
        new GameConfig(
            "bingo",
            "Bingo",
            List.of(),
            Material.MAP,
            0,
            "Bingo",
            "26.2",
            "26.2",
            "26.2",
            1,
            2,
            true,
            "shared",
            "on_demand",
            2,
            10);

    assertEquals(List.of(voting), GamesConfigManager.ordinaryGames(List.of(voting, solo)));
    assertEquals(List.of(solo), GamesConfigManager.soloGames(List.of(voting, solo)));
  }

  @Test
  @DisplayName("Should only enforce solo capacity for solo games")
  void shouldOnlyEnforceSoloCapacityForSoloGames() {
    GameConfig voting = gameWithSoloCapacity(false, 8, 1);

    assertEquals(8, voting.getMinPlayers());
    assertEquals(1, voting.getSoloMaxPlayers());
    assertThrows(IllegalArgumentException.class, () -> gameWithSoloCapacity(true, 8, 1));
  }

  @Test
  @DisplayName("Should reject invalid player world combinations")
  void shouldRejectInvalidPlayerWorldCombinations() {
    assertThrows(
        IllegalArgumentException.class,
        () -> playerWorld(false, "on_demand", 2));
    assertThrows(
        IllegalArgumentException.class,
        () -> playerWorld(true, "on_demand", 3));
    assertThrows(
        IllegalArgumentException.class,
        () -> playerWorld(true, "always", 2));
  }

  @Test
  @DisplayName("Should publish a detached immutable catalog snapshot")
  void shouldCreateAnAtomicCatalogSnapshot() {
    GameConfig game =
        new GameConfig("bedwars", "BedWars", List.of(), Material.RED_BED, 0, "BedWars");
    List<GameConfig> source = new ArrayList<>();
    source.add(game);

    List<GameConfig> snapshot = GamesConfigManager.immutableCatalog(source);
    source.clear();

    assertEquals(List.of(game), snapshot);
    assertThrows(UnsupportedOperationException.class, () -> snapshot.add(game));
  }

  private static GameConfig playerWorld(boolean solo, String startup, int maxPlayers) {
    return new GameConfig(
        "puzzle",
        "Puzzle",
        List.of(),
        Material.MAP,
        0,
        "Puzzle",
        "26.2",
        "26.2",
        "26.2",
        1,
        2,
        solo,
        "player_world",
        startup,
        maxPlayers,
        10);
  }

  private static GameConfig gameWithSoloCapacity(
      boolean solo, int minPlayers, int soloMaxPlayers) {
    return new GameConfig(
        "game",
        "Game",
        List.of(),
        Material.MAP,
        0,
        "Game",
        "26.2",
        "26.2",
        "26.2",
        minPlayers,
        16,
        solo,
        "shared",
        "on_demand",
        soloMaxPlayers,
        10);
  }
}
