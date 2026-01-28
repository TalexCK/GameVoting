package com.talexck.gameVoting.api.database;

/**
 * Interface for NoSQL database connection providers (e.g., MongoDB).
 * Separate from DatabaseConnection as NoSQL databases don't use JDBC.
 */
public interface NoSQLConnection {

    /**
     * Initialize the database connection.
     *
     * @return true if initialization was successful
     */
    boolean initialize();

    /**
     * Get the database client object.
     * For MongoDB, this returns a MongoDatabase.
     *
     * @param <T> The database client type
     * @return database client instance
     */
    <T> T getClient();

    /**
     * Close the connection and release resources.
     */
    void close();

    /**
     * Check if the connection is active.
     *
     * @return true if connection is initialized and active
     */
    boolean isActive();
}
