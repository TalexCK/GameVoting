package com.talexck.gameVoting.utils.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.talexck.gameVoting.api.database.DatabaseConnection;
import com.talexck.gameVoting.api.database.VoteHistoryRepository;
import com.talexck.gameVoting.voting.VoteHistory;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * PostgreSQL implementation of VoteHistoryRepository.
 */
public class PostgresVoteHistoryRepository implements VoteHistoryRepository {

    private final DatabaseConnection connection;
    private final Logger logger;
    private final Gson gson;

    public PostgresVoteHistoryRepository(DatabaseConnection connection, Logger logger) {
        this.connection = connection;
        this.logger = logger;
        this.gson = new Gson();
    }

    @Override
    public boolean initialize() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS vote_history (
                session_id UUID PRIMARY KEY,
                timestamp TIMESTAMP NOT NULL,
                winning_game_id VARCHAR(255) NOT NULL,
                winning_game_name VARCHAR(255) NOT NULL,
                total_votes INT NOT NULL,
                player_count INT NOT NULL,
                vote_details JSONB NOT NULL
            );
            
            CREATE INDEX IF NOT EXISTS idx_vote_history_timestamp ON vote_history(timestamp DESC);
            CREATE INDEX IF NOT EXISTS idx_vote_history_winning_game ON vote_history(winning_game_id);
            """;

        try (Connection conn = connection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            logger.info("Vote history table initialized successfully");
            return true;
        } catch (SQLException e) {
            logger.severe("Failed to initialize vote history table: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean saveSession(VoteHistory history) {
        String sql = """
            INSERT INTO vote_history (session_id, timestamp, winning_game_id, winning_game_name, 
                                     total_votes, player_count, vote_details)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
            """;

        try (Connection conn = connection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, history.getSessionId());
            stmt.setTimestamp(2, Timestamp.from(history.getTimestamp()));
            stmt.setString(3, history.getWinningGameId());
            stmt.setString(4, history.getWinningGameName());
            stmt.setInt(5, history.getTotalVotes());
            stmt.setInt(6, history.getPlayerCount());
            stmt.setString(7, gson.toJson(history.getVoteDetails()));

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe("Failed to save vote history: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<VoteHistory> getSessionHistory(int page, int pageSize) {
        String sql = """
            SELECT session_id, timestamp, winning_game_id, winning_game_name, 
                   total_votes, player_count, vote_details
            FROM vote_history
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
            """;

        List<VoteHistory> results = new ArrayList<>();

        try (Connection conn = connection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, pageSize);
            stmt.setInt(2, page * pageSize);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToHistory(rs));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to retrieve session history: " + e.getMessage());
        }

        return results;
    }

    @Override
    public VoteHistory getSession(UUID sessionId) {
        String sql = """
            SELECT session_id, timestamp, winning_game_id, winning_game_name, 
                   total_votes, player_count, vote_details
            FROM vote_history
            WHERE session_id = ?
            """;

        try (Connection conn = connection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, sessionId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToHistory(rs);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to retrieve session: " + e.getMessage());
        }

        return null;
    }

    @Override
    public Map<String, Integer> getTopWinningGames(int limit) {
        String sql = """
            SELECT winning_game_id, COUNT(*) as win_count
            FROM vote_history
            GROUP BY winning_game_id
            ORDER BY win_count DESC
            LIMIT ?
            """;

        Map<String, Integer> results = new LinkedHashMap<>();

        try (Connection conn = connection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.put(rs.getString("winning_game_id"), rs.getInt("win_count"));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to retrieve top winning games: " + e.getMessage());
        }

        return results;
    }

    @Override
    public int getTotalSessions() {
        String sql = "SELECT COUNT(*) as total FROM vote_history";

        try (Connection conn = connection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            logger.severe("Failed to count sessions: " + e.getMessage());
        }

        return 0;
    }

    private VoteHistory mapResultSetToHistory(ResultSet rs) throws SQLException {
        UUID sessionId = (UUID) rs.getObject("session_id");
        Instant timestamp = rs.getTimestamp("timestamp").toInstant();
        String winningGameId = rs.getString("winning_game_id");
        String winningGameName = rs.getString("winning_game_name");
        int totalVotes = rs.getInt("total_votes");
        int playerCount = rs.getInt("player_count");
        
        String voteDetailsJson = rs.getString("vote_details");
        Map<String, Integer> voteDetails = gson.fromJson(voteDetailsJson, 
            new TypeToken<Map<String, Integer>>(){}.getType());

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
