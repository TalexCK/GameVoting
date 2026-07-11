package com.talexck.gameVoting.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schedulerbridge.common.ServerInstance;
import com.schedulerbridge.common.ServerInstanceState;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameServiceIdsTest {
  @Test
  void formatsSchedulerServerAsNumberedService() {
    ServerInstance instance = instance("Backstabbed", ServerInstanceState.READY);

    assertEquals("Backstabbed-1", GameServiceIds.display(instance));
  }

  @Test
  void resolvesOnlyControllableServices() {
    ServerInstance ready = instance("Backstabbed", ServerInstanceState.READY);
    ServerInstance exited = instance("skywars", ServerInstanceState.EXITED);

    assertEquals(
        ready,
        GameServiceIds.resolve(List.of(ready, exited), "backstabbed-1").orElseThrow());
    assertTrue(GameServiceIds.resolve(List.of(ready, exited), "skywars-1").isEmpty());
  }

  @Test
  void startingAndReadyServicesAreControllable() {
    assertTrue(GameServiceIds.isControllable(instance("one", ServerInstanceState.STARTING)));
    assertTrue(GameServiceIds.isControllable(instance("two", ServerInstanceState.READY)));
    assertFalse(GameServiceIds.isControllable(instance("three", ServerInstanceState.STOPPING)));
    assertFalse(GameServiceIds.isControllable(instance("four", ServerInstanceState.FAILED)));
  }

  private static ServerInstance instance(String serverId, ServerInstanceState state) {
    return new ServerInstance(serverId, "instance", state, 1L, 50001);
  }
}
