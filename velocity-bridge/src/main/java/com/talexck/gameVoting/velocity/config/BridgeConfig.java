package com.talexck.gameVoting.velocity.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BridgeConfig {

  private final Map<String, GameEntry> gamesByKey;
  private final List<String> defaultHelpLines;
  private final List<PermissionHelpSection> permissionHelpSections;

  public BridgeConfig(
      Map<String, GameEntry> gamesByKey,
      List<String> defaultHelpLines,
      List<PermissionHelpSection> permissionHelpSections) {
    this.gamesByKey = Collections.unmodifiableMap(new LinkedHashMap<>(gamesByKey));
    this.defaultHelpLines = List.copyOf(defaultHelpLines);
    this.permissionHelpSections = List.copyOf(permissionHelpSections);
  }

  public static BridgeConfig empty() {
    return new BridgeConfig(Map.of(), List.of(), List.of());
  }

  public Collection<GameEntry> getGames() {
    return gamesByKey.values();
  }

  public List<String> getDefaultHelpLines() {
    return defaultHelpLines;
  }

  public List<PermissionHelpSection> getPermissionHelpSections() {
    return permissionHelpSections;
  }

  public Optional<GameEntry> findGame(String input) {
    String normalized = normalize(input);
    if (normalized.isEmpty()) {
      return Optional.empty();
    }

    GameEntry direct = gamesByKey.get(normalized);
    if (direct != null) {
      return Optional.of(direct);
    }

    return gamesByKey.values().stream()
        .filter(game -> game.aliases().contains(normalized))
        .findFirst();
  }

  public Set<String> collectGameSuggestions() {
    Set<String> suggestions = new LinkedHashSet<>();
    for (GameEntry game : gamesByKey.values()) {
      suggestions.add(game.id());
      suggestions.addAll(game.aliases());
    }
    return suggestions;
  }

  public static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  public record GameEntry(
      String id,
      String name,
      List<String> introLines,
      List<String> ruleLines,
      Set<String> aliases) {
    public GameEntry {
      introLines = List.copyOf(introLines);
      ruleLines = List.copyOf(ruleLines);
      aliases = Set.copyOf(aliases);
    }
  }

  public record PermissionHelpSection(String permission, List<String> lines) {
    public PermissionHelpSection {
      lines = List.copyOf(lines);
    }
  }
}
