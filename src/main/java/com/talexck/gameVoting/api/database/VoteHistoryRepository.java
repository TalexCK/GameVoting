package com.talexck.gameVoting.api.database;

import com.talexck.gameVoting.voting.VoteHistory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repository interface for vote history database operations.
 * Implementations exist for PostgreSQL, MySQL, and MongoDB.
 */
public interface VoteHistoryRepository {

    /**
     * Save a voting session to the database.
     *
     * @param history The vote history record to save
     * @return true if saved successfully
     */
    boolean saveSession(VoteHistory history);

    /**
     * Get paginated voting session history.
     *
     * @param page Page number (0-indexed)
     * @param pageSize Number of records per page
     * @return List of vote history records for the page
     */
    List<VoteHistory> getSessionHistory(int page, int pageSize);

    /**
     * Get a specific session by ID.
     *
     * @param sessionId The session UUID
     * @return VoteHistory or null if not found
     */
    VoteHistory getSession(UUID sessionId);

    /**
     * Get top N games by historical win count.
     *
     * @param limit Number of top games to return
     * @return Map of game_id -> win_count sorted by win count descending
     */
    Map<String, Integer> getTopWinningGames(int limit);

    /**
     * Get total number of voting sessions.
     *
     * @return Total session count
     */
    int getTotalSessions();

    /**
     * Initialize database tables/collections for vote history.
     * Should be called on plugin startup.
     *
     * @return true if initialization successful
     */
    boolean initialize();
}
