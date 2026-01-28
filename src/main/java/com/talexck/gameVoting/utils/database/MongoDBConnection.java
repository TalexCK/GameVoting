package com.talexck.gameVoting.utils.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.talexck.gameVoting.api.database.NoSQLConnection;
import org.bson.Document;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * MongoDB implementation of NoSQLConnection.
 * Uses MongoDB Java Driver for connection management.
 */
public class MongoDBConnection implements NoSQLConnection {

    private MongoClient mongoClient;
    private MongoDatabase database;
    private final String host;
    private final int port;
    private final String databaseName;
    private final String username;
    private final String password;
    private final Logger logger;

    public MongoDBConnection(String host, int port, String databaseName,
                            String username, String password, Logger logger) {
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
        this.logger = logger;
    }

    @Override
    public boolean initialize() {
        try {
            // Build connection string
            String connectionString;
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                connectionString = String.format("mongodb://%s:%s@%s:%d/%s?authSource=admin",
                    username, password, host, port, databaseName);
            } else {
                connectionString = String.format("mongodb://%s:%d/%s", host, port, databaseName);
            }

            // Configure MongoDB client settings
            MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .applyToConnectionPoolSettings(builder -> builder
                    .maxSize(10)
                    .minSize(2)
                    .maxWaitTime(30, TimeUnit.SECONDS)
                    .maxConnectionIdleTime(10, TimeUnit.MINUTES)
                    .maxConnectionLifeTime(30, TimeUnit.MINUTES))
                .applyToSocketSettings(builder -> builder
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS))
                .build();

            mongoClient = MongoClients.create(settings);
            database = mongoClient.getDatabase(databaseName);

            // Test connection with ping
            database.runCommand(new Document("ping", 1));

            logger.info("MongoDB connection established successfully");
            return true;
        } catch (MongoException e) {
            logger.severe("Failed to initialize MongoDB connection: " + e.getMessage());
            if (mongoClient != null) {
                mongoClient.close();
                mongoClient = null;
                database = null;
            }
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getClient() {
        if (database == null) {
            throw new IllegalStateException("MongoDB connection is not initialized");
        }
        return (T) database;
    }

    /**
     * Get the MongoDatabase instance.
     *
     * @return MongoDatabase instance
     */
    public MongoDatabase getDatabase() {
        if (database == null) {
            throw new IllegalStateException("MongoDB connection is not initialized");
        }
        return database;
    }

    @Override
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("MongoDB connection closed");
            mongoClient = null;
            database = null;
        }
    }

    @Override
    public boolean isActive() {
        return mongoClient != null && database != null;
    }
}
