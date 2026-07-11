package com.talexck.gameVoting.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeleportTargetTrackerTest {

  @Test
  @DisplayName("Should replace targets when a new game start captures players")
  void shouldReplaceTargetsForEachGameStart() {
    TeleportTargetTracker tracker = new TeleportTargetTracker();
    UUID previousPlayer = UUID.randomUUID();
    UUID currentPlayer = UUID.randomUUID();

    tracker.capture(List.of(previousPlayer));
    tracker.capture(List.of(currentPlayer));

    assertEquals(List.of(currentPlayer), tracker.drainOnline(playerId -> true));
  }

  @Test
  @DisplayName("Should discard disconnected players before transfer is queued")
  void shouldDiscardDisconnectedPlayersBeforeQueueingTransfer() {
    TeleportTargetTracker tracker = new TeleportTargetTracker();
    UUID disconnectedPlayer = UUID.randomUUID();
    UUID onlinePlayer = UUID.randomUUID();

    tracker.capture(List.of(disconnectedPlayer, onlinePlayer));
    tracker.remove(disconnectedPlayer);

    assertEquals(List.of(onlinePlayer), tracker.drainOnline(onlinePlayer::equals));
  }

  @Test
  @DisplayName("Should consume an offline target so reconnect cannot reuse it")
  void shouldConsumeOfflineTargetBeforeReconnect() {
    TeleportTargetTracker tracker = new TeleportTargetTracker();
    UUID playerId = UUID.randomUUID();

    tracker.capture(List.of(playerId));

    assertTrue(tracker.drainOnline(ignored -> false).isEmpty());
    assertTrue(tracker.drainOnline(ignored -> true).isEmpty());
  }
}
