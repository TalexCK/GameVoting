package com.talexck.gameVoting.utils.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.talexck.gameVoting.api.database.VoteHistoryRepository;
import com.talexck.gameVoting.voting.VoteHistory;
import org.bson.Document;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * MongoDB implementation of VoteHistoryRepository.
 */
public class MongoDBVoteHistoryRepository implements VoteHistoryRepository {

    private final MongoDatabase database;
    private final Logger logger;
    private MongoCollection<Document> collection;

    private static final String COLLECTION_NAME = "vote_history";

    public MongoDBVoteHistoryRepository(MongoDatabase database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    @Override
    public boolean initialize() {
        try {
            // Create collection if it doesn't exist
            if (!collectionExists()) {
                database.createCollection(COLLECTION_NAME);
            }

            collection = database.getCollection(COLLECTION_NAME);

            // Create indexes
            collection.createIndex(Indexes.descending("timestamp"));
            collection.createIndex(Indexes.ascending("winning_game_id"));

            logger.info("Vote history collection initialized successfully");
            return true;
        } catch (Exception e) {
            logger.severe("Failed to initialize vote history collection: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean saveSession(VoteHistory history) {
        try {
            Document doc = new Document()
                .append("_id", history.getSessionId().toString())
                .append("session_id", history.getSessionId().toString())
                .append("timestamp", Date.from(history.getTimestamp()))
                .append("winning_game_id", history.getWinningGameId())
                .append("winning_game_name", history.getWinningGameName())
                .append("total_votes", history.getTotalVotes())
                .append("player_count", history.getPlayerCount())
                .append("vote_details", new Document(history.getVoteDetails()));

            collection.insertOne(doc);
            return true;
        } catch (Exception e) {
            logger.severe("Failed to save vote history: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<VoteHistory> getSessionHistory(int page, int pageSize) {
        List<VoteHistory> results = new ArrayList<>();

        try {
            collection.find()
                .sort(Sorts.descending("timestamp"))
                .skip(page * pageSize)
                .limit(pageSize)
                .forEach(doc -> results.add(mapDocumentToHistory(doc)));
        } catch (Exception e) {
            logger.severe("Failed to retrieve session history: " + e.getMessage());
        }

        return results;
    }

    @Override
    public VoteHistory getSession(UUID sessionId) {
        try {
            Document doc = collection.find(new Document("session_id", sessionId.toString()))
                .first();
            
            if (doc != null) {
                return mapDocumentToHistory(doc);
            }
        } catch (Exception e) {
            logger.severe("Failed to retrieve session: " + e.getMessage());
        }

        return null;
    }

    @Override
    public Map<String, Integer> getTopWinningGames(int limit) {
        Map<String, Integer> results = new LinkedHashMap<>();

        try {
            // Aggregation pipeline to count wins by game_id
            List<Document> pipeline = Arrays.asList(
                new Document("$group", new Document("_id", "$winning_game_id")
                    .append("win_count", new Document("$sum", 1))),
                new Document("$sort", new Document("win_count", -1)),
                new Document("$limit", limit)
            );

            collection.aggregate(pipeline).forEach(doc -> {
                String gameId = doc.getString("_id");
                Integer winCount = doc.getInteger("win_count");
                results.put(gameId, winCount);
            });
        } catch (Exception e) {
            logger.severe("Failed to retrieve top winning games: " + e.getMessage());
        }

        return results;
    }

    @Override
    public int getTotalSessions() {
        try {
            return (int) collection.countDocuments();
        } catch (Exception e) {
            logger.severe("Failed to count sessions: " + e.getMessage());
            return 0;
        }
    }

    private boolean collectionExists() {
        for (String name : database.listCollectionNames()) {
            if (name.equals(COLLECTION_NAME)) {
                return true;
            }
        }
        return false;
    }

    private VoteHistory mapDocumentToHistory(Document doc) {
        UUID sessionId = UUID.fromString(doc.getString("session_id"));
        Instant timestamp = doc.getDate("timestamp").toInstant();
        String winningGameId = doc.getString("winning_game_id");
        String winningGameName = doc.getString("winning_game_name");
        int totalVotes = doc.getInteger("total_votes");
        int playerCount = doc.getInteger("player_count");
        
        Document voteDetailsDoc = doc.get("vote_details", Document.class);
        Map<String, Integer> voteDetails = new HashMap<>();
        voteDetailsDoc.forEach((key, value) -> voteDetails.put(key, (Integer) value));

        return new VoteHistory.Builder()
            .sessionId(sessionId)
            .timestamp(timestamp)
            .winningGameId(winningGameId)
            .winningGameName(winningGameName)
            .totalVotes(totalVotes)
            .playerCount(playerCount)
            .voteDetails(voteDetails)
            .build();
    }
}
