package com.talexck.gameVoting.utils.database;

import com.talexck.gameVoting.api.database.DatabaseConnection;
import com.talexck.gameVoting.api.database.NoSQLConnection;
import com.talexck.gameVoting.api.database.VoteHistoryRepository;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Manager for database connections.
 * Handles initialization and lifecycle of database connections based on configuration.
 */
public class DatabaseManager {
    private static DatabaseManager instance;

    private DatabaseConnection sqlConnection;
    private NoSQLConnection noSqlConnection;
    private VoteHistoryRepository voteHistoryRepository;
    private DatabaseFactory.DatabaseType databaseType;
    private final Logger logger;

    private DatabaseManager(Plugin plugin) {
        this.logger = plugin.getLogger();
    }

    /**
     * Initialize the database manager with plugin configuration.
     *
     * @param plugin Plugin instance
     * @return DatabaseManager instance
     */
    public static DatabaseManager initialize(Plugin plugin) {
        if (instance == null) {
            instance = new DatabaseManager(plugin);
            instance.loadFromConfig(plugin);
        }
        return instance;
    }

    /**
     * Get the DatabaseManager instance.
     *
     * @return DatabaseManager instance, or null if not initialized
     */
    public static DatabaseManager getInstance() {
        return instance;
    }

    /**
     * Load database configuration and initialize connection.
     *
     * @param plugin Plugin instance
     */
    private void loadFromConfig(Plugin plugin) {
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database");
        if (dbConfig == null) {
            logger.warning("Database configuration section not found in config.yml");
            databaseType = DatabaseFactory.DatabaseType.NONE;
            return;
        }

        boolean enabled = dbConfig.getBoolean("enabled", false);
        if (!enabled) {
            logger.info("Database is disabled in configuration");
            databaseType = DatabaseFactory.DatabaseType.NONE;
            return;
        }

        String typeStr = dbConfig.getString("type", "postgresql");
        databaseType = DatabaseFactory.parseType(typeStr);

        if (databaseType == DatabaseFactory.DatabaseType.NONE) {
            logger.warning("Invalid or unsupported database type: " + typeStr);
            return;
        }

        // Initialize appropriate connection type
        if (DatabaseFactory.isSQLDatabase(databaseType)) {
            sqlConnection = DatabaseFactory.createSQLConnection(databaseType, dbConfig, logger);
            if (sqlConnection != null && sqlConnection.initialize()) {
                logger.info("SQL database (" + databaseType + ") initialized successfully");
                
                // Initialize VoteHistoryRepository for SQL databases
                initializeVoteHistoryRepository();
            } else {
                logger.severe("Failed to initialize SQL database connection");
                sqlConnection = null;
            }
        } else if (DatabaseFactory.isNoSQLDatabase(databaseType)) {
            noSqlConnection = DatabaseFactory.createMongoDBConnection(dbConfig, logger);
            if (noSqlConnection != null && noSqlConnection.initialize()) {
                logger.info("MongoDB database initialized successfully");
                
                // Initialize VoteHistoryRepository for MongoDB
                initializeVoteHistoryRepository();
            } else {
                logger.severe("Failed to initialize MongoDB connection");
                noSqlConnection = null;
            }
        }
    }

    /**
     * Initialize the VoteHistoryRepository based on database type.
     */
    private void initializeVoteHistoryRepository() {
        switch (databaseType) {
            case POSTGRESQL:
                voteHistoryRepository = new PostgresVoteHistoryRepository(sqlConnection, logger);
                break;
            case MYSQL:
                voteHistoryRepository = new MySQLVoteHistoryRepository(sqlConnection, logger);
                break;
            case MONGODB:
                voteHistoryRepository = new MongoDBVoteHistoryRepository(noSqlConnection.getClient(), logger);
                break;
            default:
                logger.warning("No VoteHistoryRepository implementation for database type: " + databaseType);
                return;
        }

        if (voteHistoryRepository != null && voteHistoryRepository.initialize()) {
            logger.info("VoteHistoryRepository initialized successfully");
        } else {
            logger.severe("Failed to initialize VoteHistoryRepository");
            voteHistoryRepository = null;
        }
    }

    /**
     * Get the SQL database connection.
     *
     * @return DatabaseConnection instance, or null if not initialized
     */
    public DatabaseConnection getSQLConnection() {
        return sqlConnection;
    }

    /**
     * Get the NoSQL database connection.
     *
     * @return NoSQLConnection instance, or null if not initialized
     */
    public NoSQLConnection getNoSQLConnection() {
        return noSqlConnection;
    }

    /**
     * Get the current database type.
     *
     * @return DatabaseType enum value
     */
    public DatabaseFactory.DatabaseType getDatabaseType() {
        return databaseType;
    }

    /**
     * Check if database is enabled and active.
     *
     * @return true if database connection is active
     */
    public boolean isActive() {
        if (sqlConnection != null) {
            return sqlConnection.isActive();
        }
        if (noSqlConnection != null) {
            return noSqlConnection.isActive();
        }
        return false;
    }

    /**
     * Check if SQL database is available.
     *
     * @return true if SQL connection is active
     */
    public boolean hasSQLConnection() {
        return sqlConnection != null && sqlConnection.isActive();
    }

    /**
     * Check if NoSQL database is available.
     *
     * @return true if NoSQL connection is active
     */
    public boolean hasNoSQLConnection() {
        return noSqlConnection != null && noSqlConnection.isActive();
    }

    /**
     * Get the VoteHistoryRepository instance.
     *
     * @return VoteHistoryRepository instance, or null if not initialized
     */
    public VoteHistoryRepository getVoteHistoryRepository() {
        return voteHistoryRepository;
    }

    /**
     * Check if VoteHistoryRepository is available.
     *
     * @return true if repository is initialized
     */
    public boolean hasVoteHistoryRepository() {
        return voteHistoryRepository != null;
    }

    /**
     * Shutdown and close all database connections.
     */
    public void shutdown() {
        if (sqlConnection != null) {
            sqlConnection.close();
            sqlConnection = null;
        }
        if (noSqlConnection != null) {
            noSqlConnection.close();
            noSqlConnection = null;
        }
        logger.info("Database connections closed");
    }
}
