package com.talexck.gameVoting.utils.database;

import com.talexck.gameVoting.api.database.DatabaseConnection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * PostgreSQL implementation of DatabaseConnection using HikariCP connection pooling.
 */
public class PostgresConnection implements DatabaseConnection {

    private HikariDataSource dataSource;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final Logger logger;

    public PostgresConnection(String host, int port, String database,
                             String username, String password, Logger logger) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.logger = logger;
    }

    @Override
    public boolean initialize() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, database));
            config.setUsername(username);
            config.setPassword(password);

            // Connection pool settings
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            // Connection test query
            config.setConnectionTestQuery("SELECT 1");

            dataSource = new HikariDataSource(config);

            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                logger.info("PostgreSQL connection established successfully");
                return true;
            }
        } catch (SQLException e) {
            logger.severe("Failed to initialize PostgreSQL connection: " + e.getMessage());
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
            return false;
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Connection pool is not initialized");
        }
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("PostgreSQL connection pool closed");
        }
    }

    @Override
    public boolean isActive() {
        return dataSource != null && !dataSource.isClosed();
    }
}
