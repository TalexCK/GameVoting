package com.talexck.gameVoting.party;

import java.util.UUID;

/**
 * Represents a party invitation with expiry tracking.
 * Invitations automatically expire after 30 seconds.
 */
public class PartyInvite {
    private final UUID partyId;
    private final UUID inviterId;
    private final UUID targetId;
    private final long timestamp;
    private static final long TIMEOUT_MS = 30000;  // 30 seconds

    /**
     * Create a new party invitation.
     *
     * @param partyId The party ID
     * @param inviterId The player who sent the invitation
     * @param targetId The player who received the invitation
     */
    public PartyInvite(UUID partyId, UUID inviterId, UUID targetId) {
        this.partyId = partyId;
        this.inviterId = inviterId;
        this.targetId = targetId;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Check if this invitation has expired.
     *
     * @return true if more than 30 seconds have passed
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > TIMEOUT_MS;
    }

    // === Getters ===

    public UUID getPartyId() {
        return partyId;
    }

    public UUID getInviterId() {
        return inviterId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
