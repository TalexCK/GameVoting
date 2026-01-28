package com.talexck.gameVoting.party;

import java.util.*;

/**
 * Singleton manager for all party operations.
 * Handles party creation, dissolution, invitations, and member management.
 */
public class PartyManager {
    private static PartyManager instance;

    private final Map<UUID, UUID> playerToParty;      // Player UUID -> Party ID
    private final Map<UUID, Party> parties;           // Party ID -> Party
    private final Map<UUID, PartyInvite> pendingInvites;  // Player UUID -> Invite

    private static final int INVITE_TIMEOUT_SECONDS = 30;
    private static final int MAX_PARTY_SIZE = 8;

    private PartyManager() {
        this.playerToParty = new HashMap<>();
        this.parties = new HashMap<>();
        this.pendingInvites = new HashMap<>();
    }

    /**
     * Get the singleton instance of PartyManager.
     *
     * @return The PartyManager instance
     */
    public static PartyManager getInstance() {
        if (instance == null) {
            instance = new PartyManager();
        }
        return instance;
    }

    // === Party Creation & Dissolution ===

    /**
     * Create a new party with the specified leader.
     *
     * @param leaderId The leader's UUID
     * @return The created party, or null if player is already in a party
     */
    public Party createParty(UUID leaderId) {
        if (hasParty(leaderId)) {
            return null;  // Already in a party
        }
        Party party = new Party(leaderId);
        parties.put(party.getPartyId(), party);
        playerToParty.put(leaderId, party.getPartyId());
        return party;
    }

    /**
     * Disband a party and remove all members.
     *
     * @param partyId The party ID to disband
     */
    public void disbandParty(UUID partyId) {
        Party party = parties.get(partyId);
        if (party == null) return;

        // Remove all members from lookup
        for (UUID member : party.getMembers()) {
            playerToParty.remove(member);
        }
        parties.remove(partyId);
    }

    // === Invite System ===

    /**
     * Send a party invitation to a target player.
     *
     * @param inviterId The player sending the invite
     * @param targetId The player receiving the invite
     * @return true if invitation was sent successfully
     */
    public boolean sendInvite(UUID inviterId, UUID targetId) {
        Party party = getPartyByPlayer(inviterId);
        if (party == null) return false;
        if (!party.isLeader(inviterId)) return false;
        if (hasParty(targetId)) return false;  // Target already in party
        if (!party.canInvite()) return false;  // Party full

        PartyInvite invite = new PartyInvite(party.getPartyId(), inviterId, targetId);
        pendingInvites.put(targetId, invite);
        party.addInvite(targetId);

        return true;
    }

    /**
     * Accept a pending party invitation.
     *
     * @param playerId The player accepting the invite
     * @return true if successfully joined the party
     */
    public boolean acceptInvite(UUID playerId) {
        PartyInvite invite = pendingInvites.get(playerId);
        if (invite == null || invite.isExpired()) {
            pendingInvites.remove(playerId);
            return false;
        }

        Party party = parties.get(invite.getPartyId());
        if (party == null) return false;

        // Add to party
        party.addMember(playerId);
        party.removeInvite(playerId);
        playerToParty.put(playerId, party.getPartyId());
        pendingInvites.remove(playerId);

        return true;
    }

    /**
     * Decline a pending party invitation.
     *
     * @param playerId The player declining the invite
     */
    public void declineInvite(UUID playerId) {
        PartyInvite invite = pendingInvites.remove(playerId);
        if (invite != null) {
            Party party = parties.get(invite.getPartyId());
            if (party != null) {
                party.removeInvite(playerId);
            }
        }
    }

    // === Member Management ===

    /**
     * Remove a player from their party.
     * If the leader leaves, leadership is transferred to another member.
     * If the party becomes empty, it is disbanded.
     *
     * @param playerId The player leaving
     */
    public void leaveParty(UUID playerId) {
        Party party = getPartyByPlayer(playerId);
        if (party == null) return;

        party.removeMember(playerId);
        playerToParty.remove(playerId);

        // Handle party dissolution or leadership transfer
        if (party.getMembers().isEmpty()) {
            disbandParty(party.getPartyId());
        } else if (party.isLeader(playerId)) {
            // Transfer to random member
            UUID newLeader = party.getMembers().iterator().next();
            party.transferLeadership(newLeader);
        }
    }

    /**
     * Transfer party leadership to another member.
     *
     * @param currentLeader The current leader's UUID
     * @param newLeader The new leader's UUID
     * @return true if leadership was transferred successfully
     */
    public boolean transferLeadership(UUID currentLeader, UUID newLeader) {
        Party party = getPartyByPlayer(currentLeader);
        if (party == null) return false;
        if (!party.isLeader(currentLeader)) return false;
        if (!party.isMember(newLeader)) return false;

        party.transferLeadership(newLeader);
        return true;
    }

    // === Queries ===

    /**
     * Get the party that a player belongs to.
     *
     * @param playerId The player's UUID
     * @return The party, or null if not in a party
     */
    public Party getPartyByPlayer(UUID playerId) {
        UUID partyId = playerToParty.get(playerId);
        return partyId == null ? null : parties.get(partyId);
    }

    /**
     * Check if a player is in a party.
     *
     * @param playerId The player's UUID
     * @return true if the player is in a party
     */
    public boolean hasParty(UUID playerId) {
        return playerToParty.containsKey(playerId);
    }

    /**
     * Get a player's pending invitation.
     *
     * @param playerId The player's UUID
     * @return The pending invite, or null if none exists
     */
    public PartyInvite getPendingInvite(UUID playerId) {
        return pendingInvites.get(playerId);
    }

    // === Cleanup ===

    /**
     * Handle a player quitting the server.
     * Removes them from their party and cleans up invitations.
     *
     * @param playerId The player's UUID
     */
    public void handlePlayerQuit(UUID playerId) {
        leaveParty(playerId);
        pendingInvites.remove(playerId);
    }
}
