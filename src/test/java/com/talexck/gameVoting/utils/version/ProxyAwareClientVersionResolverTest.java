package com.talexck.gameVoting.utils.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talexck.gameVoting.proxy.ProxyVersionBridge;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ProxyAwareClientVersionResolverTest {
  @Test
  void waitsForProxyRefreshInsteadOfUsingBackendProtocol() {
    UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    Player player = mock(Player.class);
    ProxyVersionBridge bridge = mock(ProxyVersionBridge.class);
    AtomicBoolean backendCalled = new AtomicBoolean();
    when(player.getUniqueId()).thenReturn(playerId);
    when(bridge.getCachedVersion(playerId)).thenReturn(Optional.empty());

    String resolved =
        ProxyAwareClientVersionResolver.resolve(
            player,
            bridge,
            ignored -> {
              backendCalled.set(true);
              return "1.21/1.21.1";
            });

    assertNull(resolved);
    assertFalse(backendCalled.get());
    verify(bridge).requestPlayerVersion(player, playerId);
  }

  @Test
  void usesCachedProxyProtocolWithoutBackendDetection() {
    UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    Player player = mock(Player.class);
    ProxyVersionBridge bridge = mock(ProxyVersionBridge.class);
    when(player.getUniqueId()).thenReturn(playerId);
    when(bridge.getCachedVersion(playerId)).thenReturn(Optional.of("26.2"));

    String resolved =
        ProxyAwareClientVersionResolver.resolve(
            player, bridge, ignored -> "1.21/1.21.1");

    assertEquals("26.2", resolved);
  }
}
