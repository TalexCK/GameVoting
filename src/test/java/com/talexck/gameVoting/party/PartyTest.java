package com.talexck.gameVoting.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartyTest {
  @Test
  void capsPartiesAtSixteenPlayers() {
    Party party = new Party(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    for (int index = 2; index <= 16; index++) {
      party.addMember(new UUID(0L, index));
    }

    assertEquals(16, party.getMaxMembers());
    assertEquals(16, party.getMembers().size());
    assertFalse(party.canInvite());

    party.addMember(new UUID(0L, 17L));
    assertEquals(16, party.getMembers().size());
  }
}
