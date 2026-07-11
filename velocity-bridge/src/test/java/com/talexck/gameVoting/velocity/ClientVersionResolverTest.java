package com.talexck.gameVoting.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientVersionResolverTest {
  private static final UUID PLAYER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void prefersViaOriginalProtocolAndVersionName() {
    String resolved =
        ClientVersionResolver.resolve(
                PLAYER_ID,
                767,
                ignored -> new ClientVersionResolver.ViaProtocol(776, "26.2"))
            .orElseThrow();

    assertEquals("26.2", resolved);
  }

  @Test
  void fallsBackToVelocityWhenViaHasNoPlayerProtocol() {
    String resolved =
        ClientVersionResolver.resolve(PLAYER_ID, 767, ignored -> null).orElseThrow();

    assertEquals("1.21/1.21.1", resolved);
  }
}
