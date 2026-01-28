package com.talexck.gameVoting.voting;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Model class representing a historical voting session.
 * Used for database storage and retrieval of voting history.
 */
public class VoteHistory {
    private final UUID sessionId;
    private final Instant timestamp;
    private final String winningGameId;
    private final String winningGameName;
    private final int totalVotes;
    private final int playerCount;
    private final Map<String, Integer> voteDetails;  // game_id -> vote_count

    public VoteHistory(UUID sessionId, Instant timestamp, String winningGameId, 
                      String winningGameName, int totalVotes, int playerCount, 
                      Map<String, Integer> voteDetails) {
        this.sessionId = sessionId;
        this.timestamp = timestamp;
        this.winningGameId = winningGameId;
        this.winningGameName = winningGameName;
        this.totalVotes = totalVotes;
        this.playerCount = playerCount;
        this.voteDetails = voteDetails;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getWinningGameId() {
        return winningGameId;
    }

    public String getWinningGameName() {
        return winningGameName;
    }

    public int getTotalVotes() {
        return totalVotes;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public Map<String, Integer> getVoteDetails() {
        return voteDetails;
    }

    /**
     * Builder pattern for creating VoteHistory instances.
     */
    public static class Builder {
        private UUID sessionId;
        private Instant timestamp;
        private String winningGameId;
        private String winningGameName;
        private int totalVotes;
        private int playerCount;
        private Map<String, Integer> voteDetails;

        public Builder sessionId(UUID sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder winningGameId(String winningGameId) {
            this.winningGameId = winningGameId;
            return this;
        }

        public Builder winningGameName(String winningGameName) {
            this.winningGameName = winningGameName;
            return this;
        }

        public Builder totalVotes(int totalVotes) {
            this.totalVotes = totalVotes;
            return this;
        }

        public Builder playerCount(int playerCount) {
            this.playerCount = playerCount;
            return this;
        }

        public Builder voteDetails(Map<String, Integer> voteDetails) {
            this.voteDetails = voteDetails;
            return this;
        }

        public VoteHistory build() {
            return new VoteHistory(sessionId, timestamp, winningGameId, 
                                 winningGameName, totalVotes, playerCount, voteDetails);
        }
    }
}
