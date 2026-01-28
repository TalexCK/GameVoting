package com.talexck.gameVoting.voting;

/**
 * Represents the result of a vote operation.
 */
public enum VoteResult {
    /**
     * Vote was successfully added.
     */
    ADDED,

    /**
     * Vote was successfully removed.
     */
    REMOVED,

    /**
     * Player has reached the maximum vote limit (3 votes).
     */
    LIMIT_REACHED,

    /**
     * Voting session is not currently active.
     */
    SESSION_INACTIVE
}
