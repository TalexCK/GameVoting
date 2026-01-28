package com.talexck.gameVoting.utils.database;

import com.talexck.gameVoting.api.database.DatabaseConnection;
import com.talexck.gameVoting.api.database.NoSQLConnection;
import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

/**
 * Factory for creating database connections based on configuration.
 * Supports PostgreSQL, MySQL, and MongoDB.
 */
public class DatabaseFactory {

    /**
     * Database type enumeration.
     */
    public enum DatabaseType {
        POSTGRESQL,
        MYSQL,
        MONGODB,
        NONE
    }

    /**
     * Create a SQL database connection (PostgreSQL or MySQL).
     *
     * @param type Database type
     * @param config Configuration section containing database settings
     * @param logger Logger instance
     * @return DatabaseConnection instance, or null if creation failed
     */
    public static DatabaseConnection createSQLConnection(DatabaseType type, ConfigurationSection config, Logger logger) {
        if (type == DatabaseType.NONE || type == DatabaseType.MONGODB) {
            return null;
        }

        String host = config.getString("host", "localhost");
        int port = config.getInt("port", getDefaultPort(type));
        String database = config.getString("database", "gamevoting");
        String username = config.getString("username", "root");
        String password = config.getString("password", "");

        switch (type) {
            case POSTGRESQL:
                return new PostgresConnection(host, port, database, username, password, logger);
            case MYSQL:
                return new MySQLConnection(host, port, database, username, password, logger);
            default:
                return null;
        }
    }

    /**
     * Create a NoSQL database connection (MongoDB).
     *
     * @param config Configuration section containing database settings
     * @param logger Logger instance
     * @return NoSQLConnection instance, or null if creation failed
     */
    public static NoSQLConnection createMongoDBConnection(ConfigurationSection config, Logger logger) {
        String host = config.getString("host", "localhost");
        int port = config.getInt("port", 27017);
        String database = config.getString("database", "gamevoting");
        String username = config.getString("username", "");
        String password = config.getString("password", "");

        return new MongoDBConnection(host, port, database, username, password, logger);
    }

    /**
     * Parse database type from string.
     *
     * @param typeStr Type string (e.g., "postgresql", "mysql", "mongodb")
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
            case "mysql":
                return DatabaseType.MYSQL;
            case "mongodb":
            case "mongo":
                return DatabaseType.MONGODB;
            default:
                return DatabaseType.NONE;
        }
    }

    /**
     * Get default port for database type.
     *
     * @param type Database type
     * @return Default port number
     */
    private static int getDefaultPort(DatabaseType type) {
        switch (type) {
            case POSTGRESQL:
                return 5432;
            case MYSQL:
                return 3306;
            case MONGODB:
                return 27017;
            default:
                return 0;
        }
    }

    /**
     * Check if database type is SQL-based.
     *
     * @param type Database type
     * @return true if SQL database
     */
    public static boolean isSQLDatabase(DatabaseType type) {
        return type == DatabaseType.POSTGRESQL || type == DatabaseType.MYSQL;
    }

    /**
     * Check if database type is NoSQL-based.
     *
     * @param type Database type
     * @return true if NoSQL database
     */
    public static boolean isNoSQLDatabase(DatabaseType type) {
        return type == DatabaseType.MONGODB;
    }
}
