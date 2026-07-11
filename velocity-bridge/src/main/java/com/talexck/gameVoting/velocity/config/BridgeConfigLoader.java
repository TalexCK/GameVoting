package com.talexck.gameVoting.velocity.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

public final class BridgeConfigLoader {

  private static final String CONFIG_FILE_NAME = "config.yml";
  private static final List<String> FALLBACK_HELP_LINES =
      List.of(
          "&e/game <game> &7- &f查看游戏介绍与规则",
          "&e/hub &7- &f返回大厅服",
          "&e/lobby &7- &f返回大厅服",
          "&e/spawn &7- &f回到出生点");
  private static final List<BridgeConfig.PermissionHelpSection> FALLBACK_PERMISSION_SECTIONS =
      List.of(
          new BridgeConfig.PermissionHelpSection(
              "gamevoting.vote",
              List.of(
                  "&e/vote &7- &f打开投票菜单（或查看投票帮助）",
                  "&e/vote join [game] &7- &f加入当前游戏或指定游戏",
                  "&e/vote ready &7- &f在准备阶段标记已就绪")),
          new BridgeConfig.PermissionHelpSection(
              "gamevoting.vote.admin",
              List.of(
                  "&6[投票管理]",
                  "&e/vote start [duration] &7- &f开启投票（人数不足时需管理员）",
                  "&e/vote stop &7- &f结束当前投票并展示结果",
                  "&e/vote forcestart <game-id> &7- &f跳过投票直接开局",
                  "&e/vote gamestart &7- &f由本轮发起者强制开局",
                  "&e/vote stopgame <game-id> &7- &f关闭指定游戏的在线实例",
                  "&e/vote gamelist &7- &f查看各游戏在线实例",
                  "&e/vote session stop &7- &f强制停止当前会话",
                  "&e/vote reload &7- &f重载投票插件配置",
                  "&e/vote holograms create|list|remove <id> &7- &f管理投票全息图")),
          new BridgeConfig.PermissionHelpSection(
              "gamevoting.vote.lock",
              List.of(
                  "&6[投票锁定]",
                  "&e/vote lock <玩家> &7- &f锁定该玩家下一次有效投票",
                  "&e/vote unlock <玩家> &7- &f取消该玩家的投票锁定")));
  private static final String DEFAULT_CONFIG =
      """
      # GameVoting Velocity 命令配置
      games: {}

      help:
        default:
          - "&e/game <game> &7- &f查看游戏介绍与规则"
          - "&e/hub &7- &f返回大厅服"
          - "&e/lobby &7- &f返回大厅服"
          - "&e/spawn &7- &f回到出生点"
        permission_sections:
          - permission: "gamevoting.vote"
            lines:
              - "&e/vote &7- &f打开投票菜单（或查看投票帮助）"
              - "&e/vote join [game] &7- &f加入当前游戏或指定游戏"
              - "&e/vote ready &7- &f在准备阶段标记已就绪"
          - permission: "gamevoting.vote.admin"
            lines:
              - "&6[投票管理]"
              - "&e/vote start [duration] &7- &f开启投票（人数不足时需管理员）"
              - "&e/vote stop &7- &f结束当前投票并展示结果"
              - "&e/vote forcestart <game-id> &7- &f跳过投票直接开局"
              - "&e/vote gamestart &7- &f由本轮发起者强制开局"
              - "&e/vote stopgame <game-id> &7- &f关闭指定游戏的在线实例"
              - "&e/vote gamelist &7- &f查看各游戏在线实例"
              - "&e/vote session stop &7- &f强制停止当前会话"
              - "&e/vote reload &7- &f重载投票插件配置"
              - "&e/vote holograms create|list|remove <id> &7- &f管理投票全息图"
          - permission: "gamevoting.vote.lock"
            lines:
              - "&6[投票锁定]"
              - "&e/vote lock <玩家> &7- &f锁定该玩家下一次有效投票"
              - "&e/vote unlock <玩家> &7- &f取消该玩家的投票锁定"
      """;

  private final Path configFile;
  private final Yaml yaml = new Yaml();

  public BridgeConfigLoader(Path dataDirectory) {
    this.configFile = dataDirectory.resolve(CONFIG_FILE_NAME);
  }

  public BridgeConfig load() throws IOException {
    createDefaultConfigIfMissing();
    try (InputStream input = Files.newInputStream(configFile)) {
      Object raw = yaml.load(input);
      Map<String, Object> root = toMap(raw);
      return parse(root);
    }
  }

  private void createDefaultConfigIfMissing() throws IOException {
    Files.createDirectories(configFile.getParent());
    if (Files.exists(configFile)) {
      return;
    }

    try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
      writer.write(DEFAULT_CONFIG);
    }
  }

  private BridgeConfig parse(Map<String, Object> root) {
    Map<String, BridgeConfig.GameEntry> games = parseGames(toMap(root.get("games")));
    Map<String, Object> help = toMap(root.get("help"));
    List<String> defaultHelpLines = toStringList(help.get("default"));
    if (defaultHelpLines.isEmpty()) {
      defaultHelpLines = FALLBACK_HELP_LINES;
    }
    List<BridgeConfig.PermissionHelpSection> permissionSections =
        parsePermissionSections(toList(help.get("permission_sections")));
    if (permissionSections.isEmpty()) {
      permissionSections = FALLBACK_PERMISSION_SECTIONS;
    } else {
      permissionSections = ensureVotePermissionSections(permissionSections);
    }
    return new BridgeConfig(games, defaultHelpLines, permissionSections);
  }

  private Map<String, BridgeConfig.GameEntry> parseGames(Map<String, Object> rawGames) {
    Map<String, BridgeConfig.GameEntry> games = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : rawGames.entrySet()) {
      String gameId = BridgeConfig.normalize(entry.getKey());
      if (gameId.isEmpty()) {
        continue;
      }

      Map<String, Object> gameMap = toMap(entry.getValue());
      String displayName = toString(gameMap.get("name"), entry.getKey());
      List<String> introLines = toStringList(gameMap.get("intro"));
      List<String> ruleLines = toStringList(gameMap.get("rules"));
      Set<String> aliases = new LinkedHashSet<>();
      aliases.add(gameId);
      for (String alias : toStringList(gameMap.get("aliases"))) {
        String normalizedAlias = BridgeConfig.normalize(alias);
        if (!normalizedAlias.isEmpty()) {
          aliases.add(normalizedAlias);
        }
      }

      games.put(
          gameId, new BridgeConfig.GameEntry(gameId, displayName, introLines, ruleLines, aliases));
    }
    return games;
  }

  private List<BridgeConfig.PermissionHelpSection> parsePermissionSections(
      List<Object> rawSections) {
    List<BridgeConfig.PermissionHelpSection> sections = new ArrayList<>();
    for (Object rawSection : rawSections) {
      Map<String, Object> sectionMap = toMap(rawSection);
      String permission = normalizePermission(toString(sectionMap.get("permission"), ""));
      List<String> lines = toStringList(sectionMap.get("lines"));
      if (!permission.isBlank() && !lines.isEmpty()) {
        sections.add(new BridgeConfig.PermissionHelpSection(permission, lines));
      }
    }
    return sections;
  }

  private List<BridgeConfig.PermissionHelpSection> ensureVotePermissionSections(
      List<BridgeConfig.PermissionHelpSection> configuredSections) {
    List<BridgeConfig.PermissionHelpSection> merged = new ArrayList<>(configuredSections);
    Set<String> existingPermissions = new HashSet<>();
    for (BridgeConfig.PermissionHelpSection section : configuredSections) {
      existingPermissions.add(normalizePermission(section.permission()));
    }

    for (BridgeConfig.PermissionHelpSection fallbackSection : FALLBACK_PERMISSION_SECTIONS) {
      String normalized = normalizePermission(fallbackSection.permission());
      if (!existingPermissions.contains(normalized)) {
        merged.add(fallbackSection);
      }
    }
    return merged;
  }

  private String normalizePermission(String permission) {
    if ("gamevoting.admin".equalsIgnoreCase(permission)) {
      return "gamevoting.vote.admin";
    }
    return permission;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toMap(Object raw) {
    if (!(raw instanceof Map<?, ?> rawMap)) {
      return Map.of();
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      map.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return map;
  }

  private List<Object> toList(Object raw) {
    if (raw instanceof List<?> list) {
      return new ArrayList<>(list);
    }
    return List.of();
  }

  private List<String> toStringList(Object raw) {
    if (raw instanceof List<?> list) {
      List<String> values = new ArrayList<>();
      for (Object item : list) {
        String value = Objects.toString(item, "").trim();
        if (!value.isEmpty()) {
          values.add(value);
        }
      }
      return values;
    }

    if (raw instanceof String str && !str.isBlank()) {
      return List.of(str.trim());
    }
    return List.of();
  }

  private String toString(Object raw, String defaultValue) {
    String value = Objects.toString(raw, "").trim();
    return value.isEmpty() ? defaultValue : value;
  }
}
