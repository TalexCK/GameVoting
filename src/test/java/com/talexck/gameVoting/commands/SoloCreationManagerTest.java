package com.talexck.gameVoting.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SoloCreationManagerTest {
  private static final UUID OWNER =
      UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID TARGET =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID OTHER =
      UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Test
  void createsSinglePlayerRosterUntilInviteIsAccepted() {
    SoloCreationManager manager = new SoloCreationManager();
    manager.begin(OWNER, "puzzle");
    SoloCreationManager.Invite invite = manager.invite(OWNER, "puzzle", TARGET, 100L, 1_000L);

    assertEquals(List.of(OWNER), manager.roster(OWNER, "puzzle"));
    assertEquals(TARGET, manager.draft(OWNER, "puzzle").orElseThrow().pendingPlayer());

    SoloCreationManager.Resolution accepted = manager.accept(invite.token(), TARGET, 200L);

    assertEquals(SoloCreationManager.ResolutionStatus.ACCEPTED, accepted.status());
    assertEquals(List.of(OWNER, TARGET), manager.roster(OWNER, "puzzle"));
    assertEquals(TARGET, manager.draft(OWNER, "puzzle").orElseThrow().acceptedPlayer());
  }

  @Test
  void bindsInvitationToItsTargetAndKeepsItAvailableAfterWrongPlayerAttempt() {
    SoloCreationManager manager = new SoloCreationManager();
    manager.begin(OWNER, "puzzle");
    SoloCreationManager.Invite invite = manager.invite(OWNER, "puzzle", TARGET, 100L, 1_000L);

    assertEquals(
        SoloCreationManager.ResolutionStatus.INVALID_TARGET,
        manager.accept(invite.token(), OTHER, 200L).status());
    assertEquals(
        SoloCreationManager.ResolutionStatus.ACCEPTED,
        manager.accept(invite.token(), TARGET, 300L).status());
  }

  @Test
  void expiresInvitationAndRestoresSinglePlayerCreation() {
    SoloCreationManager manager = new SoloCreationManager();
    manager.begin(OWNER, "puzzle");
    SoloCreationManager.Invite invite = manager.invite(OWNER, "puzzle", TARGET, 100L, 1_000L);

    assertTrue(manager.expire(invite.token(), 1_100L).isPresent());
    assertFalse(manager.expire(invite.token(), 1_200L).isPresent());
    assertEquals(List.of(OWNER), manager.roster(OWNER, "puzzle"));
    assertEquals(null, manager.draft(OWNER, "puzzle").orElseThrow().pendingPlayer());
  }

  @Test
  void switchingGamesInvalidatesThePreviousInvitation() {
    SoloCreationManager manager = new SoloCreationManager();
    manager.begin(OWNER, "first");
    SoloCreationManager.Invite invite = manager.invite(OWNER, "first", TARGET, 100L, 1_000L);

    manager.begin(OWNER, "second");

    assertTrue(manager.draft(OWNER, "first").isEmpty());
    assertEquals(
        SoloCreationManager.ResolutionStatus.NOT_FOUND,
        manager.accept(invite.token(), TARGET, 200L).status());
    assertEquals(List.of(OWNER), manager.roster(OWNER, "second"));
  }

  @Test
  void targetLeavingCancelsOnlyThePendingInvitation() {
    SoloCreationManager manager = new SoloCreationManager();
    manager.begin(OWNER, "puzzle");
    SoloCreationManager.Invite invite = manager.invite(OWNER, "puzzle", TARGET, 100L, 1_000L);

    assertEquals(1, manager.clearTarget(TARGET).size());
    assertEquals(List.of(OWNER), manager.roster(OWNER, "puzzle"));
    assertEquals(
        SoloCreationManager.ResolutionStatus.NOT_FOUND,
        manager.accept(invite.token(), TARGET, 200L).status());
  }

  @Test
  void acceptedPlayerIsReservedUntilRemoved() {
    SoloCreationManager manager = new SoloCreationManager();
    manager.begin(OWNER, "puzzle");
    SoloCreationManager.Invite invite = manager.invite(OWNER, "puzzle", TARGET, 100L, 1_000L);
    manager.accept(invite.token(), TARGET, 200L);

    assertTrue(manager.isReserved("PUZZLE", TARGET));
    assertThrows(IllegalStateException.class, () -> manager.begin(TARGET, "puzzle"));

    manager.clearSelection(OWNER, "puzzle");
    assertFalse(manager.isReserved("puzzle", TARGET));
    assertEquals(TARGET, manager.begin(TARGET, "puzzle").owner());
  }

  @Test
  void targetCanAcceptOnlyOnePendingCreationForTheSameGame() {
    SoloCreationManager manager = new SoloCreationManager();
    manager.begin(OWNER, "puzzle");
    manager.begin(OTHER, "puzzle");
    SoloCreationManager.Invite first = manager.invite(OWNER, "puzzle", TARGET, 100L, 1_000L);
    SoloCreationManager.Invite second = manager.invite(OTHER, "puzzle", TARGET, 100L, 1_000L);

    assertEquals(
        SoloCreationManager.ResolutionStatus.PENDING,
        manager.inspect(first.token(), TARGET, 200L).status());
    assertEquals(
        SoloCreationManager.ResolutionStatus.ACCEPTED,
        manager.accept(first.token(), TARGET, 200L).status());
    assertEquals(
        SoloCreationManager.ResolutionStatus.ALREADY_RESERVED,
        manager.accept(second.token(), TARGET, 200L).status());
  }

  @Test
  void rejectsSelfInvitationAndMissingDraft() {
    SoloCreationManager manager = new SoloCreationManager();

    assertThrows(
        IllegalStateException.class,
        () -> manager.invite(OWNER, "puzzle", TARGET, 100L, 1_000L));
    manager.begin(OWNER, "puzzle");
    assertThrows(
        IllegalArgumentException.class,
        () -> manager.invite(OWNER, "puzzle", OWNER, 100L, 1_000L));
  }
}
