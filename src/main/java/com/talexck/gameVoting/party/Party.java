package com.talexck.gameVoting.party;

import java.util.*;

/**
 * Represents a party with members, a leader, and voting state.
 * Parties allow players to group together and participate in games collectively.
 */
public class Party {
    private final UUID partyId;
    private UUID leaderId;
    private final Set<UUID> members;
    private final Set<UUID> pendingInvites;
    private final long createdAt;
    private int maxMembers;

    // Party voting state (for future implementation)
    private boolean votingActive;
    private Map<UUID, Set<String>> partyVotes;
    private Map<String, Integer> voteCounts;

    public Party(UUID leaderId) {
        this.partyId = UUID.randomUUID();
        this.leaderId = leaderId;
        this.members = new HashSet<>();
        this.pendingInvites = new HashSet<>();
        this.createdAt = System.currentTimeMillis();
        this.maxMembers = 8;
        this.votingActive = false;
        this.partyVotes = new HashMap<>();
        this.voteCounts = new HashMap<>();

        // Leader is first member
        members.add(leaderId);
    }

    // === Core Methods ===

    /**
     * Check if a player is the party leader.
     *
     * @param playerId The player's UUID
     * @return true if the player is the leader
     */
    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    /**
     * Check if a player is a member of this party.
     *
     * @param playerId The player's UUID
     * @return true if the player is a member
     */
    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    /**
     * Add a member to the party.
     *
     * @param playerId The player's UUID
     */
    public void addMember(UUID playerId) {
        members.add(playerId);
    }

    /**
     * Remove a member from the party.
     *
     * @param playerId The player's UUID
     */
    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    /**
     * Transfer leadership to another member.
     *
     * @param newLeaderId The new leader's UUID
     */
    public void transferLeadership(UUID newLeaderId) {
        if (members.contains(newLeaderId)) {
            this.leaderId = newLeaderId;
        }
    }

    /**
     * Check if the party can accept more members.
     *
     * @return true if the party has space
     */
    public boolean canInvite() {
        return members.size() < maxMembers;
    }

    /**
     * Add a pending invitation.
     *
     * @param playerId The invited player's UUID
     */
    public void addInvite(UUID playerId) {
        pendingInvites.add(playerId);
    }

    /**
     * Remove a pending invitation.
     *
     * @param playerId The invited player's UUID
     */
    public void removeInvite(UUID playerId) {
        pendingInvites.remove(playerId);
    }

    /**
     * Check if a player has a pending invitation.
     *
     * @param playerId The player's UUID
     * @return true if the player has an invite
     */
    public boolean hasInvite(UUID playerId) {
        return pendingInvites.contains(playerId);
    }

    // === Party Voting Methods (for future implementation) ===

    /**
     * Start party voting session.
     */
    public void startPartyVoting() {
        votingActive = true;
        partyVotes.clear();
        voteCounts.clear();
    }

    /**
     * Stop party voting session.
     */
    public void stopPartyVoting() {
        votingActive = false;
    }

    /**
     * Record a vote for a game.
     *
     * @param playerId The voter's UUID
     * @param gameId The game ID being voted for
     */
    public void voteForGame(UUID playerId, String gameId) {
        if (!votingActive || !isMember(playerId)) return;

        Set<String> votes = partyVotes.computeIfAbsent(playerId, k -> new HashSet<>());
        votes.add(gameId);
        voteCounts.put(gameId, voteCounts.getOrDefault(gameId, 0) + 1);
    }

    /**
     * Get the winning game from party voting.
     *
     * @return The game ID with most votes, or null if no votes
     */
    public String getWinningGame() {
        return voteCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // === Getters ===

    public UUID getPartyId() {
        return partyId;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public Set<UUID> getMembers() {
        return new HashSet<>(members);
    }

    public Set<UUID> getPendingInvites() {
        return new HashSet<>(pendingInvites);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public boolean isVotingActive() {
        return votingActive;
    }
}
