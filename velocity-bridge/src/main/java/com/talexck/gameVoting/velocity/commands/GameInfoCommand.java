package com.talexck.gameVoting.velocity.commands;

import com.talexck.gameVoting.velocity.config.BridgeConfig;
import com.talexck.gameVoting.velocity.utils.LegacyColorUtil;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class GameInfoCommand implements SimpleCommand {

  private final Supplier<BridgeConfig> configSupplier;

  public GameInfoCommand(Supplier<BridgeConfig> configSupplier) {
    this.configSupplier = configSupplier;
  }

  @Override
  public void execute(Invocation invocation) {
    BridgeConfig config = configSupplier.get();
    String[] args = invocation.arguments();
    if (args.length < 1 || args[0].isBlank()) {
      invocation.source().sendMessage(LegacyColorUtil.colorize("&c用法: &e/game <game>"));
      sendGameList(invocation, config);
      return;
    }

    String input = args[0];
    config
        .findGame(input)
        .ifPresentOrElse(
            game -> {
              invocation
                  .source()
                  .sendMessage(
                      LegacyColorUtil.colorize("&6=== &r" + game.name() + " &r&6==="));
              if (!game.introLines().isEmpty()) {
                invocation.source().sendMessage(LegacyColorUtil.colorize("&e介绍:"));
                for (String line : game.introLines()) {
                  invocation
                      .source()
                      .sendMessage(LegacyColorUtil.colorize("&7- " + line + "&r"));
                }
              }
              if (!game.ruleLines().isEmpty()) {
                invocation.source().sendMessage(LegacyColorUtil.colorize("&e规则与信息:"));
                for (String line : game.ruleLines()) {
                  invocation
                      .source()
                      .sendMessage(LegacyColorUtil.colorize("&7- " + line + "&r"));
                }
              }
              if (game.introLines().isEmpty() && game.ruleLines().isEmpty()) {
                invocation.source().sendMessage(LegacyColorUtil.colorize("&c该游戏暂未配置介绍与规则。"));
              }
            },
            () -> {
              invocation.source().sendMessage(LegacyColorUtil.colorize("&c未找到游戏: &e" + input));
              sendGameList(invocation, config);
            });
  }

  @Override
  public List<String> suggest(Invocation invocation) {
    String[] args = invocation.arguments();
    if (args.length > 1) {
      return List.of();
    }

    String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
    return configSupplier.get().collectGameSuggestions().stream()
        .filter(key -> key.startsWith(prefix))
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.toList());
  }

  private void sendGameList(Invocation invocation, BridgeConfig config) {
    String games =
        config.getGames().stream()
            .map(BridgeConfig.GameEntry::id)
            .collect(Collectors.joining(", "));
    if (games.isEmpty()) {
      invocation.source().sendMessage(LegacyColorUtil.colorize("&c当前未配置任何游戏。"));
      return;
    }
    invocation.source().sendMessage(LegacyColorUtil.colorize("&a可用游戏: &e" + games));
  }
}
