package com.talexck.gameVoting.utils.database;

import com.talexck.gameVoting.api.database.DatabaseConnection;
import com.talexck.gameVoting.api.database.VoteHistoryRepository;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public class DatabaseManager {
  private static DatabaseManager instance;

  private DatabaseConnection connection;
  private VoteHistoryRepository voteHistoryRepository;
  private final Logger logger;

  private DatabaseManager(Plugin plugin) {
    this.logger = plugin.getLogger();
  }

  public static DatabaseManager initialize(Plugin plugin) {
    if (instance == null) {
      instance = new DatabaseManager(plugin);
      instance.loadFromConfig(plugin);
    }
    return instance;
  }

  public static DatabaseManager getInstance() {
    return instance;
  }

  private void loadFromConfig(Plugin plugin) {
    ConfigurationSection config = plugin.getConfig().getConfigurationSection("database");
    if (config == null) {
      logger.severe("Database configuration section not found in config.yml");
      return;
    }
    if (!config.getBoolean("enabled", true)) {
      logger.info("Database is disabled in configuration");
      return;
    }
    connection = DatabaseFactory.createConnection(config, logger);
    if (!connection.initialize()) {
      logger.severe("Failed to initialize PostgreSQL connection");
      connection = null;
      return;
    }
    voteHistoryRepository = new PostgresVoteHistoryRepository(connection, logger);
    if (!voteHistoryRepository.initialize()) {
      logger.severe("Failed to initialize VoteHistoryRepository");
      voteHistoryRepository = null;
    }
  }

  public DatabaseConnection getSQLConnection() {
    return connection;
  }

  public boolean isActive() {
    return connection != null && connection.isActive();
  }

  public boolean hasSQLConnection() {
    return isActive();
  }

  public VoteHistoryRepository getVoteHistoryRepository() {
    return voteHistoryRepository;
  }

  public boolean hasVoteHistoryRepository() {
    return voteHistoryRepository != null;
  }

  public void shutdown() {
    if (connection != null) {
      connection.close();
      connection = null;
    }
    logger.info("Database connections closed");
  }
}
