package com.talexck.gameVoting.velocity.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SchedulerGameCatalogClient {

  private static final String URL_ENV = "SCHEDULER_BRIDGE_URL";
  private static final String TOKEN_ENV = "SCHEDULER_BRIDGE_TOKEN";
  private static final String GAMES_PATH = "/bridge/v1/games";

  private final String baseUrl;
  private final String token;

  private SchedulerGameCatalogClient(String baseUrl, String token) {
    this.baseUrl = stripTrailingSlash(baseUrl);
    this.token = token;
  }

  public static Optional<SchedulerGameCatalogClient> fromEnvironment() {
    return create(System.getenv(URL_ENV), System.getenv(TOKEN_ENV));
  }

  static Optional<SchedulerGameCatalogClient> create(String baseUrl, String token) {
    if (baseUrl == null || baseUrl.isBlank() || token == null || token.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(new SchedulerGameCatalogClient(baseUrl.trim(), token.trim()));
  }

  public List<BridgeConfig.GameEntry> load() throws IOException {
    HttpURLConnection connection;
    try {
      connection =
          (HttpURLConnection) URI.create(baseUrl + GAMES_PATH).toURL().openConnection();
    } catch (IllegalArgumentException error) {
      throw new IOException("Scheduler bridge URL is invalid", error);
    }
    connection.setRequestMethod("GET");
    connection.setConnectTimeout(3000);
    connection.setReadTimeout(5000);
    connection.setRequestProperty("Authorization", "Bearer " + token);
    try {
      int status = connection.getResponseCode();
      if (status < 200 || status >= 300) {
        throw new IOException("Scheduler game catalog returned HTTP " + status);
      }
      List<BridgeConfig.GameEntry> games = new ArrayList<>();
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (!line.isBlank()) {
            games.add(parseGameRecord(line));
          }
        }
      }
      return List.copyOf(games);
    } finally {
      connection.disconnect();
    }
  }

  static BridgeConfig.GameEntry parseGameRecord(String record) throws IOException {
    String[] fields = record.split("\\t", -1);
    if (fields.length != 12 && fields.length != 17) {
      throw new IOException("Scheduler returned an invalid game record");
    }
    try {
      Integer.parseInt(fields[0]);
      String id = BridgeConfig.normalize(decode(fields[1]));
      String serverId = BridgeConfig.normalize(decode(fields[2]));
      String name = decode(fields[3]);
      if (id.isEmpty() || name.isBlank()) {
        throw new IllegalArgumentException("Game ID and name must not be empty");
      }

      List<String> intro = new ArrayList<>();
      String description = decode(fields[4]);
      if (!description.isEmpty()) {
        for (String line : description.split("\\u001f", -1)) {
          if (!line.isBlank()) {
            intro.add(line.trim());
          }
        }
      }

      String exactVersion = decode(fields[7]);
      String minVersion = decode(fields[8]);
      String maxVersion = decode(fields[9]);
      int minPlayers = Integer.parseInt(fields[10]);
      int maxPlayers = Integer.parseInt(fields[11]);
      if (minPlayers < 1 || maxPlayers < minPlayers) {
        throw new IllegalArgumentException("Game player limits are invalid");
      }

      List<String> details = new ArrayList<>();
      String versionText = versionText(exactVersion, minVersion, maxVersion);
      if (!versionText.isEmpty()) {
        details.add("&f支持版本：&e" + versionText);
      }
      details.add("&f人数：&e" + rangeText(minPlayers, maxPlayers));
      details.add("&f类型：&e" + gameType(fields));

      Set<String> aliases = new LinkedHashSet<>();
      aliases.add(id);
      if (!serverId.isEmpty()) {
        aliases.add(serverId);
      }
      return new BridgeConfig.GameEntry(id, name, intro, details, aliases);
    } catch (IllegalArgumentException error) {
      throw new IOException("Scheduler returned an invalid game record", error);
    }
  }

  private static String versionText(String exact, String minimum, String maximum) {
    if (!exact.isBlank()) {
      return exact;
    }
    if (minimum.isBlank()) {
      return maximum;
    }
    if (maximum.isBlank() || minimum.equalsIgnoreCase(maximum)) {
      return minimum;
    }
    return minimum + " &7- &e" + maximum;
  }

  private static String rangeText(int minimum, int maximum) {
    if (minimum == maximum) {
      return Integer.toString(minimum);
    }
    return minimum + " &7- &e" + maximum;
  }

  private static String gameType(String[] fields) {
    if (fields.length == 12 || !parseBoolean(fields[12])) {
      return "投票游戏";
    }
    String soloMode = decode(fields[13]);
    if (soloMode.equalsIgnoreCase("player_world")) {
      return "独立存档 Solo";
    }
    return "共享 Solo";
  }

  private static boolean parseBoolean(String value) {
    if (value.equalsIgnoreCase("true")) {
      return true;
    }
    if (value.equalsIgnoreCase("false")) {
      return false;
    }
    throw new IllegalArgumentException("Game boolean field is invalid");
  }

  private static String decode(String value) {
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private static String stripTrailingSlash(String value) {
    String result = value;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
