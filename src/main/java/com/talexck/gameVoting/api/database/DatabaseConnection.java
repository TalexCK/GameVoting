package com.talexck.gameVoting.api.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface for database connection providers.
 * Allows abstraction between different database implementations.
 */
public interface DatabaseConnection {

    /**
     * Initialize the database connection pool.
     *
     * @return true if initialization was successful
     */
    boolean initialize();

    /**
     * Get a connection from the pool.
     *
     * @return database connection
     * @throws SQLException if connection cannot be obtained
     */
    Connection getConnection() throws SQLException;

    /**
     * Close the connection pool and release resources.
     */
    void close();

    /**
     * Check if the connection pool is active.
     *
     * @return true if pool is initialized and active
     */
    boolean isActive();
}
