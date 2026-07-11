package com.talexck.gameVoting.velocity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulerGameCatalogClientTest {

  @Test
  void parsesSchedulerGameWithDescriptionAndMetadata() throws Exception {
    String record =
        String.join(
            "\t",
            "20",
            encode("bedwar"),
            encode("bedwar"),
            encode("&b&l起床战争"),
            encode("&7保护你的床并摧毁敌人的床\u001f&f摧毁全部敌方队伍"),
            encode("BLUE_BED"),
            "0",
            encode(""),
            encode("1.21.11"),
            encode("26.2"),
            "2",
            "8",
            "false",
            encode("shared"),
            encode("on_demand"),
            "8",
            "10");

    BridgeConfig.GameEntry game = SchedulerGameCatalogClient.parseGameRecord(record);

    assertEquals("bedwar", game.id());
    assertEquals("&b&l起床战争", game.name());
    assertEquals(
        List.of("&7保护你的床并摧毁敌人的床", "&f摧毁全部敌方队伍"),
        game.introLines());
    assertEquals(
        List.of(
            "&f支持版本：&e1.21.11 &7- &e26.2",
            "&f人数：&e2 &7- &e8",
            "&f类型：&e投票游戏"),
        game.ruleLines());
  }

  @Test
  void parsesPlayerWorldSoloType() throws Exception {
    String record =
        String.join(
            "\t",
            "200",
            encode("puzzle"),
            encode("puzzle-server"),
            encode("Puzzle"),
            encode("Solve the map"),
            encode("COMPASS"),
            "0",
            encode("1.21.10"),
            encode(""),
            encode(""),
            "1",
            "2",
            "true",
            encode("player_world"),
            encode("on_demand"),
            "2",
            "10");

    BridgeConfig.GameEntry game = SchedulerGameCatalogClient.parseGameRecord(record);

    assertTrue(game.aliases().contains("puzzle-server"));
    assertEquals("&f类型：&e独立存档 Solo", game.ruleLines().get(2));
  }

  @Test
  void rejectsMalformedSchedulerRecord() {
    assertThrows(
        IOException.class,
        () -> SchedulerGameCatalogClient.parseGameRecord("invalid\trecord"));
  }

  @Test
  void disablesSchedulerCatalogWithoutCompleteEnvironment() {
    assertTrue(SchedulerGameCatalogClient.create("", "token").isEmpty());
    assertTrue(SchedulerGameCatalogClient.create("http://127.0.0.1:25566", "").isEmpty());
  }

  private static String encode(String value) {
    return Base64.getEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
