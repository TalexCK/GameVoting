package com.talexck.gameVoting.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schedulerbridge.common.ServerScheduler;
import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.utils.message.MessageUtil;
import com.talexck.gameVoting.utils.version.ReadyVersionValidator;
import com.talexck.gameVoting.voting.VotingSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class VoteCommandTest {

  @Test
  @DisplayName("Should randomly choose among tied eligible games")
  void testFindEligibleWinnerChoosesFromTopTie() {
    Map<String, Integer> results = new LinkedHashMap<>();
    results.put("game1", 5);
    results.put("game2", 5);
    results.put("game3", 4);

    String winner =
        VoteCommand.selectRandomWinner(
            results, Set.of("game1", "game2", "game3")::contains, new FixedRandom(1));

    assertEquals("game2", winner);
  }

  @Test
  @DisplayName("Should ignore unavailable games before random tie-break")
  void testFindEligibleWinnerSkipsUnavailableGames() {
    Map<String, Integer> results = new LinkedHashMap<>();
    results.put("game1", 5);
    results.put("game2", 4);
    results.put("game3", 4);

    String winner =
        VoteCommand.selectRandomWinner(
            results, Set.of("game2", "game3")::contains, new FixedRandom(1));

    assertEquals("game3", winner);
  }

  @Test
  @DisplayName("Should still choose the highest score when all results are negative")
  void testFindEligibleWinnerWithAllNegativeScores() {
    Map<String, Integer> results = new LinkedHashMap<>();
    results.put("game1", -3);
    results.put("game2", -1);
    results.put("game3", -2);

    String winner =
        VoteCommand.selectRandomWinner(
            results, Set.of("game1", "game2", "game3")::contains, new FixedRandom(0));

    assertEquals("game2", winner);
  }

  @Test
  @DisplayName("Should skip pending teleport service when another instance is joinable")
  void testSelectJoinableServiceSkipsPendingTeleportService() {
    String serviceName =
        VoteCommand.selectJoinableService(java.util.List.of("SkyWars-2", "SkyWars-3"), "SkyWars-2");

    assertEquals("SkyWars-3", serviceName);
  }

  @Test
  @DisplayName("Should block joining when only pending teleport service exists")
  void testSelectJoinableServiceReturnsNullWhenOnlyPendingTeleportServiceExists() {
    String serviceName =
        VoteCommand.selectJoinableService(java.util.List.of("SkyWars-2"), "SkyWars-2");

    assertNull(serviceName);
  }

  @Test
  @DisplayName("Should block join without game while unified teleport is pending")
  void testEvaluateJoinGateBlocksCurrentJoinWhilePendingTeleport() {
    VoteCommand.JoinGateResult result = VoteCommand.evaluateJoinGate(null, "SkyWars-1", "skywars");

    assertEquals(VoteCommand.JoinGateResult.WAIT_FOR_TELEPORT, result);
  }

  @Test
  @DisplayName("Should allow joining unrelated game while another game waits for teleport")
  void testEvaluateJoinGateAllowsOtherGames() {
    VoteCommand.JoinGateResult result = VoteCommand.evaluateJoinGate("bedwars", null, "skywars");

    assertEquals(VoteCommand.JoinGateResult.ALLOW, result);
  }

  @Test
  @DisplayName("Should block joining BedWars below the configured client version range")
  void shouldBlockJoiningBedWarsWithWrongClientVersion() {
    JoinTestContext context = createJoinTestContext();
    ReadyVersionValidator.ValidationResult result =
        ReadyVersionValidator.validate(context.game(), "1.20.6");

    try (MockedStatic<ReadyVersionValidator> validator = mockStatic(ReadyVersionValidator.class);
        MockedStatic<MessageUtil> messages = mockStatic(MessageUtil.class)) {
      validator
          .when(() -> ReadyVersionValidator.validate(context.player(), context.game()))
          .thenReturn(result);

      context
          .command()
          .onCommand(
              context.player(), context.bukkitCommand(), "vote", new String[] {"join", "bedwar"});

      verify(context.scheduler(), never()).find("bedwar");
    }
  }

  @Test
  @DisplayName("Should continue joining BedWars within the configured client version range")
  void shouldContinueJoiningBedWarsWithRequiredClientVersion() {
    JoinTestContext context = createJoinTestContext();
    ReadyVersionValidator.ValidationResult result =
        ReadyVersionValidator.validate(context.game(), "26.2");
    when(context.scheduler().find("bedwar")).thenReturn(new CompletableFuture<>());

    try (MockedStatic<ReadyVersionValidator> validator = mockStatic(ReadyVersionValidator.class)) {
      validator
          .when(() -> ReadyVersionValidator.validate(context.player(), context.game()))
          .thenReturn(result);

      context
          .command()
          .onCommand(
              context.player(), context.bukkitCommand(), "vote", new String[] {"join", "bedwar"});

      verify(context.scheduler()).find("bedwar");
    }
  }

  @Test
  @DisplayName("Should block joining the current game below its configured version range")
  void shouldBlockJoiningCurrentGameWithWrongClientVersion() {
    JoinTestContext context = createJoinTestContext();
    ReadyVersionValidator.ValidationResult result =
        ReadyVersionValidator.validate(context.game(), "1.20.6");
    VotingSession.getInstance().setCurrentGameService("bedwar");

    try (MockedStatic<ReadyVersionValidator> validator = mockStatic(ReadyVersionValidator.class);
        MockedStatic<MessageUtil> messages = mockStatic(MessageUtil.class)) {
      validator
          .when(() -> ReadyVersionValidator.validate(context.player(), context.game()))
          .thenReturn(result);

      context
          .command()
          .onCommand(context.player(), context.bukkitCommand(), "vote", new String[] {"join"});

      verify(context.scheduler(), never()).find("bedwar");
    } finally {
      VotingSession.getInstance().setCurrentGameService(null);
    }
  }

  @Test
  @DisplayName("Should continue joining the current game within its configured version range")
  void shouldContinueJoiningCurrentGameWithinClientVersionRange() {
    JoinTestContext context = createJoinTestContext();
    ReadyVersionValidator.ValidationResult result =
        ReadyVersionValidator.validate(context.game(), "26.2");
    when(context.scheduler().find("bedwar")).thenReturn(new CompletableFuture<>());
    VotingSession.getInstance().setCurrentGameService("bedwar");

    try (MockedStatic<ReadyVersionValidator> validator = mockStatic(ReadyVersionValidator.class)) {
      validator
          .when(() -> ReadyVersionValidator.validate(context.player(), context.game()))
          .thenReturn(result);

      context
          .command()
          .onCommand(context.player(), context.bukkitCommand(), "vote", new String[] {"join"});

      verify(context.scheduler()).find("bedwar");
    } finally {
      VotingSession.getInstance().setCurrentGameService(null);
    }
  }

  private JoinTestContext createJoinTestContext() {
    GameVoting plugin = mock(GameVoting.class);
    GamesConfigManager gamesManager = mock(GamesConfigManager.class);
    ServerScheduler scheduler = mock(ServerScheduler.class);
    Player player = mock(Player.class);
    Command bukkitCommand = mock(Command.class);
    GameConfig game =
        new GameConfig(
            "bedwar",
            "BedWars",
            List.of(),
            Material.RED_BED,
            0,
            "bedwar",
            null,
            "1.21.11",
            "26.2",
            2,
            8);
    VoteCommand command = new VoteCommand(plugin);
    command.setGamesManager(gamesManager);

    when(plugin.getServerScheduler()).thenReturn(scheduler);
    when(gamesManager.getGame("bedwar")).thenReturn(game);
    when(gamesManager.getGames()).thenReturn(List.of(game));
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());

    return new JoinTestContext(command, bukkitCommand, player, scheduler, game);
  }

  private static final class FixedRandom extends Random {
    private final int fixedIndex;

    private FixedRandom(int fixedIndex) {
      this.fixedIndex = fixedIndex;
    }

    @Override
    public int nextInt(int bound) {
      if (fixedIndex >= bound) {
        throw new AssertionError("固定随机值超出候选范围");
      }
      return fixedIndex;
    }
  }

  private record JoinTestContext(
      VoteCommand command,
      Command bukkitCommand,
      Player player,
      ServerScheduler scheduler,
      GameConfig game) {}
}
