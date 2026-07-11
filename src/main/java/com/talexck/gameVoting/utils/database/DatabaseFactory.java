package com.talexck.gameVoting.utils.database;

import com.talexck.gameVoting.api.database.DatabaseConnection;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** Factory for creating PostgreSQL connections. */
public class DatabaseFactory {

  /** Database type enumeration. */
  public enum DatabaseType {
    POSTGRESQL,
    NONE
  }

  /**
   * Create a PostgreSQL database connection.
   *
   * @param config Configuration section containing database settings
   * @param logger Logger instance
   * @return DatabaseConnection instance, or null if creation failed
   */
  public static DatabaseConnection createConnection(ConfigurationSection config, Logger logger) {
    String host = config.getString("host", "localhost");
    int port = config.getInt("port", 5432);
    String database = config.getString("database", "gamevoting");
    String username = config.getString("username", "minigames");
    String password = config.getString("password", "");
    return new PostgresConnection(host, port, database, username, password, logger);
  }

  /**
   * Parse database type from string.
   *
   * @param typeStr Type string
   * @return DatabaseType enum value
   */
  public static DatabaseType parseType(String typeStr) {
    if (typeStr == null || typeStr.isEmpty()) {
      return DatabaseType.NONE;
    }

    switch (typeStr.toLowerCase()) {
      case "postgresql":
      case "postgres":
        return DatabaseType.POSTGRESQL;
      default:
        return DatabaseType.NONE;
    }
  }
}
