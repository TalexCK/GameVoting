package com.talexck.gameVoting.commands;

import com.schedulerbridge.common.ServerInstance;
import com.schedulerbridge.common.ServerInstanceState;
import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.ui.VotingUI;
import com.talexck.gameVoting.utils.ColorUtil;
import com.talexck.gameVoting.utils.message.MessageUtil;
import com.talexck.gameVoting.utils.version.ReadyVersionValidator;
import com.talexck.gameVoting.voting.VotingSession;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class VoteCommand implements CommandExecutor, Listener {
  enum JoinGateResult {
    ALLOW,
    NO_GAME,
    WAIT_FOR_TELEPORT
  }

  private final GameVoting plugin;
  private GamesConfigManager gamesManager;
  private static final int DEFAULT_VOTING_DURATION_SECONDS = 60; // 1 minute
  private static final int BRIDGE_READY_CHECK_INTERVAL_TICKS = 20; // 1 second
  private static final String VOTE_LOCK_PERMISSION = "gamevoting.vote.lock";

  private final TeleportTargetTracker teleportTargets = new TeleportTargetTracker();
  // Pre-started game service after voting ends
  private String preStartedServiceName;
  private String preStartedGameId;
  private String pendingTeleportGameId;
  private String pendingTeleportServiceName;
  private BukkitTask readinessTask;
  private String scheduledTeleportServiceName;
  private long teleportGeneration;

  public VoteCommand(GameVoting plugin) {
    this.plugin = plugin;
  }

  static JoinGateResult evaluateJoinGate(
      String requestedGameId, String currentGameService, String pendingTeleportGameId) {
    boolean hasPendingTeleportGame =
        pendingTeleportGameId != null && !pendingTeleportGameId.isBlank();
    if (requestedGameId == null) {
      if (hasPendingTeleportGame) {
        return JoinGateResult.WAIT_FOR_TELEPORT;
      }
      return currentGameService != null && !currentGameService.isBlank()
          ? JoinGateResult.ALLOW
          : JoinGateResult.NO_GAME;
    }

    return JoinGateResult.ALLOW;
  }

  static String selectJoinableService(
      List<String> runningServiceNames, String pendingTeleportServiceName) {
    for (String serviceName : runningServiceNames) {
      if (pendingTeleportServiceName != null
          && pendingTeleportServiceName.equalsIgnoreCase(serviceName)) {
        continue;
      }
      return serviceName;
    }
    return null;
  }

  /**
   * Set the games configuration manager. Called by the plugin during initialization.
   *
   * @param gamesManager The games configuration manager
   */
  public void setGamesManager(GamesConfigManager gamesManager) {
    this.gamesManager = gamesManager;
  }

  /**
   * Execute the vote command.
   *
   * @param sender The command sender
   * @param command The command
   * @param label The command label (alias used)
   * @param args Command arguments
   * @return true if command was handled successfully
   */
  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    // Handle subcommands
    if (args.length > 0) {
      String subCommand = args[0].toLowerCase();

      // Allow console to execute gamestart command
      if ("gamestart".equals(subCommand)) {
        if (sender instanceof Player) {
          return handleGameStart((Player) sender);
        } else {
          // Console sender
          return handleGameStart(null);
        }
      }
    }

    // Check if sender is a player for all other commands
    if (!(sender instanceof Player player)) {
      sender.sendMessage(
          com.talexck
              .gameVoting
              .utils
              .language
              .LanguageManager
              .getInstance()
              .getMessage("command.only_players"));
      return true;
    }

    // Handle subcommands
    if (args.length > 0) {
      String subCommand = args[0].toLowerCase();

      switch (subCommand) {
        case "start":
          return handleStart(player, args);
        case "stop":
          return handleStop(player);
        case "stopgame":
          return handleStopGame(player, args);
        case "gamelist":
          return handleGameList(player);
        case "forcestart":
          return handleForceStart(player, args);
        case "ready":
          return handleReady(player);
        case "holograms":
          return handleHolograms(player, args);
        case "session":
          return handleSession(player, args);
        case "reload":
          return handleReload(player);
        case "join":
          return handleJoin(player, args);
        case "lock":
          return handleLock(player, args);
        case "unlock":
          return handleUnlock(player, args);
        default:
          MessageUtil.sendTranslated(player, "command.usage");
          return true;
      }
    }

    // No arguments - open voting UI
    return handleOpenUI(player);
  }

  /**
   * Handle /vote (open UI).
   *
   * @param player The player
   * @return true
   */
  private boolean handleOpenUI(Player player) {
    VotingSession session = VotingSession.getInstance();

    if (!session.isActive()) {
      MessageUtil.sendTranslated(player, "voting.not_active");
      MessageUtil.sendTranslated(player, "voting.not_active_wait");
      return true;
    }

    // Open the voting UI
    VotingUI ui = new VotingUI(player, gamesManager);
    ui.open(player);

    return true;
  }

  /**
   * Actually start the voting session (called from pre-voting ready phase). This is a public method
   * so it can be called from VoteItemListener.
   *
   * @param durationSeconds Voting duration in seconds
   */
  public void actuallyStartVoting(int durationSeconds) {
    VotingSession session = VotingSession.getInstance();

    clearPreStartedService();
    clearPendingTeleportJoinState();

    // End pre-voting ready phase
    session.endPreVotingReady();

    // Start the voting session with timer and callback
    session.startVoting(
        durationSeconds,
        plugin,
        () -> {
          // This runs when voting ends automatically
          handleVotingEnd();
        });

    // Give vote item to all online players
    for (Player online : Bukkit.getOnlinePlayers()) {
      com.talexck.gameVoting.utils.item.VoteItem.giveVotingItem(online);
    }

    // Broadcast to all players
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("count", String.valueOf(gamesManager.getGameCount()));
    placeholders.put("time", formatDurationMinutes(durationSeconds));

    MessageUtil.broadcastTranslated("general.separator");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("voting.start_header");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("voting.start_instructions_1");
    MessageUtil.broadcastTranslated("voting.start_instructions_2");
    MessageUtil.broadcastTranslated("voting.start_instructions_3", placeholders);
    MessageUtil.broadcastTranslated("voting.start_instructions_4", placeholders);
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("general.separator");

    // Update holograms to show voting active
    updateHologramDisplays();
  }

  /**
   * Handle /vote start [time] (start voting session).
   *
   * @param player The player
   * @param args Command arguments
   * @return true
   */
  private boolean handleStart(Player player, String[] args) {
    VotingSession session = VotingSession.getInstance();
    int requiredPlayers = session.getRequiredPlayers();
    int onlineCount = Bukkit.getOnlinePlayers().size();

    // Enough players in lobby -> no admin permission required.
    if (onlineCount < requiredPlayers && !player.hasPermission("gamevoting.vote.admin")) {
      MessageUtil.sendTranslated(player, "voting.no_permission_start");
      return true;
    }

    // Check if already active or in pre-voting ready phase
    if (session.isActive() || session.isPreVotingReady()) {
      MessageUtil.sendTranslated(player, "voting.already_active");
      return true;
    }

    // Check if there are games to vote for
    if (gamesManager.getGameCount() == 0) {
      MessageUtil.sendTranslated(player, "command.no_games_configured");
      return true;
    }

    // Parse duration
    int durationSeconds = DEFAULT_VOTING_DURATION_SECONDS;
    if (args.length > 1) {
      try {
        durationSeconds = parseDurationSeconds(args[1]);
      } catch (NumberFormatException e) {
        MessageUtil.sendTranslated(player, "command.invalid_duration");
        return true;
      } catch (IllegalArgumentException e) {
        MessageUtil.sendTranslated(player, "command.duration_must_positive");
        return true;
      }
    }

    // Store duration for later use
    final int finalDurationSeconds = durationSeconds;

    clearPreStartedService();
    clearPendingTeleportJoinState();

    // Always start voting directly when /vote start is executed
    session.setVoteStarter(player.getUniqueId());

    // Start the voting session with timer and callback
    session.startVoting(
        finalDurationSeconds,
        plugin,
        () -> {
          // This runs when voting ends automatically
          handleVotingEnd();
        });

    // Give vote item (compass) to all online players
    for (Player online : Bukkit.getOnlinePlayers()) {
      com.talexck.gameVoting.utils.item.VoteItem.giveVotingItem(online);
    }

    // Broadcast to all players
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("count", String.valueOf(gamesManager.getGameCount()));
    placeholders.put("time", formatDurationMinutes(finalDurationSeconds));

    MessageUtil.broadcastTranslated("general.separator");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("voting.start_header");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("voting.start_instructions_1");
    MessageUtil.broadcastTranslated("voting.start_instructions_2");
    MessageUtil.broadcastTranslated("voting.start_instructions_3", placeholders);
    MessageUtil.broadcastTranslated("voting.start_instructions_4", placeholders);
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("general.separator");

    // Update holograms to show voting active
    updateHologramDisplays();

    return true;
  }

  /**
   * Handle /vote lock <playername>.
   *
   * @param player 执行者
   * @param args 命令参数
   * @return true
   */
  private boolean handleLock(Player player, String[] args) {
    if (!player.hasPermission(VOTE_LOCK_PERMISSION)) {
      MessageUtil.sendTranslated(player, "command.no_permission");
      return true;
    }

    if (args.length < 2) {
      MessageUtil.sendTranslated(player, "command.lock_usage");
      return true;
    }

    OfflinePlayer target = resolvePlayerByName(args[1]);
    if (target == null || target.getName() == null || target.getName().isBlank()) {
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("player", args[1]);
      MessageUtil.sendTranslated(player, "command.player_not_found", placeholders);
      return true;
    }

    VotingSession.getInstance().lockPlayerForNextVote(target.getUniqueId());

    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("player", target.getName());
    MessageUtil.sendTranslated(player, "command.vote_lock_added", placeholders);
    return true;
  }

  /**
   * Handle /vote unlock <playername>.
   *
   * @param player 执行者
   * @param args 命令参数
   * @return true
   */
  private boolean handleUnlock(Player player, String[] args) {
    if (!player.hasPermission(VOTE_LOCK_PERMISSION)) {
      MessageUtil.sendTranslated(player, "command.no_permission");
      return true;
    }

    if (args.length < 2) {
      MessageUtil.sendTranslated(player, "command.unlock_usage");
      return true;
    }

    OfflinePlayer target = resolvePlayerByName(args[1]);
    if (target == null || target.getName() == null || target.getName().isBlank()) {
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("player", args[1]);
      MessageUtil.sendTranslated(player, "command.player_not_found", placeholders);
      return true;
    }

    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("player", target.getName());

    if (!VotingSession.getInstance().unlockPlayerForNextVote(target.getUniqueId())) {
      MessageUtil.sendTranslated(player, "command.vote_lock_not_found", placeholders);
      return true;
    }

    MessageUtil.sendTranslated(player, "command.vote_lock_removed", placeholders);
    return true;
  }

  /**
   * Handle /vote stop (stop voting and show results).
   *
   * @param player The player
   * @return true
   */
  private boolean handleStop(Player player) {
    // Check permission
    if (!player.hasPermission("gamevoting.vote.admin")) {
      MessageUtil.sendTranslated(player, "voting.no_permission_stop");
      return true;
    }

    VotingSession session = VotingSession.getInstance();

    // Check if voting is active
    if (!session.isActive()) {
      MessageUtil.sendTranslated(player, "voting.not_active");
      return true;
    }

    // Stop voting manually (won't trigger auto-start)
    Map<String, Integer> results = session.stopVoting();
    session.finishVotingRound(false);
    broadcastResults(results);
    restoreLobbyVoteItems();

    // Update hologram displays to show NOT_VOTING state (popular games)
    updateHologramDisplays();

    return true;
  }

  /**
   * Handle /vote forcestart <game-id> (force start a game without voting).
   *
   * @param player The player
   * @param args Command arguments
   * @return true
   */
  private boolean handleForceStart(Player player, String[] args) {
    // Check permission
    if (!player.hasPermission("gamevoting.vote.admin")) {
      MessageUtil.sendTranslated(player, "command.no_permission");
      return true;
    }

    // Check arguments
    if (args.length < 2) {
      MessageUtil.sendTranslated(player, "command.forcestart_usage");
      return true;
    }

    String gameId = args[1];
    GameConfig game = gamesManager.getGame(gameId);

    if (game == null) {
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("game", gameId);
      MessageUtil.sendTranslated(player, "command.game_not_found", placeholders);
      return true;
    }

    clearPreStartedService();
    clearPendingTeleportJoinState();
    // Snapshot all current lobby players before delayed teleport countdown starts.
    snapshotPlayersToTeleport();

    // Start the game
    startGame(game, player);

    return true;
  }

  /** Handle voting end (called when timer expires or manually stopped). */
  private void handleVotingEnd() {
    VotingSession session = VotingSession.getInstance();

    // Stop voting and get results
    Map<String, Integer> results = session.stopVoting();
    broadcastResults(results);
    session.finishVotingRound(true);

    // Get winner game ID
    int onlineCount = Bukkit.getOnlinePlayers().size();
    String winnerId = findEligibleWinner(results, onlineCount);
    if (winnerId == null) {
      if (results.isEmpty()) {
        MessageUtil.broadcastTranslated("voting.no_votes_cast");
      } else {
        broadcastNoEligibleGames(onlineCount);
      }
      clearPreStartedService();
      clearPendingTeleportJoinState();
      session.clear();
      restoreLobbyVoteItems();
      return;
    }
    session.setLockedWinner(winnerId);

    // Get winner GameConfig
    GameConfig winner = gamesManager.getGame(winnerId);
    if (winner == null) {
      MessageUtil.broadcastTranslated("voting.winner_not_found");
      clearPreStartedService();
      clearPendingTeleportJoinState();
      session.clear();
      restoreLobbyVoteItems();
      return;
    }

    // Start ready phase instead of immediately starting game
    session.startReadyPhase();

    // Entering ready phase should also start winner service.
    preStartWinningService(winner);

    // Update holograms to show vote results
    updateHologramDisplays();

    // 准备阶段默认全员已准备，显示绿色已准备物品
    for (Player online : Bukkit.getOnlinePlayers()) {
      session.markPlayerReady(online.getUniqueId());
      com.talexck.gameVoting.utils.item.VoteItem.updateReadyItem(online, true);
    }

    // Announce ready phase
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("game", ColorUtil.withReset(winner.getName()));

    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("ready.header");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("ready.winner_label", placeholders);
    MessageUtil.broadcastTranslated("ready.instructions_1");
    MessageUtil.broadcastTranslated("ready.instructions_2");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("ready.instructions_3");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("general.separator");

    // 默认全员已准备，直接进入开始倒计时（玩家可右键取消准备来中断）
    if (session.allPlayersReady()) {
      MessageUtil.broadcastTranslated("ready.all_ready_countdown");
      session.startCountdown(
          GameVoting.getInstance(),
          () -> {
            Bukkit.getScheduler()
                .runTask(
                    GameVoting.getInstance(),
                    () -> {
                      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vote gamestart");
                    });
          });
    }
  }

  /**
   * Broadcast voting results.
   *
   * @param results Results map
   */
  private void broadcastResults(Map<String, Integer> results) {
    VotingSession session = VotingSession.getInstance();

    MessageUtil.broadcastTranslated("general.separator");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("voting.end_header");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("voting.results_header");
    MessageUtil.broadcast("");

    if (results.isEmpty()) {
      MessageUtil.broadcastTranslated("voting.no_results");
    } else {
      int position = 1;
      for (Map.Entry<String, Integer> entry : results.entrySet()) {
        GameConfig game = gamesManager.getGame(entry.getKey());
        if (game != null) {
          String medal =
              position == 1
                  ? "&6🥇"
                  : position == 2 ? "&7🥈" : position == 3 ? "&c🥉" : "&e" + position + ".";
          MessageUtil.broadcast(
              medal
                  + " "
                  + ColorUtil.withReset(game.getName())
                  + " &7- &e"
                  + entry.getValue()
                  + " vote(s)");
          position++;
        }
      }
    }

    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("count", String.valueOf(session.getTotalVotes()));

    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("voting.total_votes", placeholders);
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("general.separator");
  }

  /**
   * Start a Scheduler server for the given game and schedule teleport.
   *
   * @param game The game to start
   * @param initiator The player who initiated (null if automatic)
   */
  private void startGame(GameConfig game, Player initiator) {
    launchGameServer(
        game,
        initiator,
        serviceName -> {
          markGamePendingTeleport(game, serviceName);
          Map<String, String> placeholders = new HashMap<>();
          placeholders.put("game", ColorUtil.withReset(game.getName()));
          MessageUtil.broadcast("");
          MessageUtil.broadcastTranslated("game.creating_service", placeholders);
          plugin
              .getLogger()
              .info(
                  "Successfully started scheduler server for "
                      + game.getName()
                      + ": "
                      + serviceName);
          scheduleTeleport(serviceName, game);
        });
  }

  /**
   * Create and start a Scheduler server from game task.
   *
   * @param game Target game
   * @param initiator Command initiator for error feedback (nullable)
   * @return Created service name, or null on failure
   */
  private void launchGameServer(GameConfig game, Player initiator, Consumer<String> onStarted) {
    String serverId = game.getServerId();
    if (serverId == null || serverId.isBlank()) {
      clearPendingTeleportJoinState();
      plugin.getLogger().warning("Game " + game.getId() + " has no scheduler server configured");
      if (initiator != null) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("game", ColorUtil.withReset(game.getName()));
        MessageUtil.sendTranslated(initiator, "game.no_server_id", placeholders);
      }
      return;
    }
    plugin.getLogger().info("Starting scheduler server: " + serverId);
    plugin
        .getServerScheduler()
        .launch(serverId, List.of())
        .whenComplete(
            (instance, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (error != null) {
                            clearPendingTeleportJoinState();
                            plugin
                                .getLogger()
                                .severe(
                                    "Failed to start scheduler server for "
                                        + game.getName()
                                        + ": "
                                        + error.getMessage());
                            if (initiator != null) {
                              MessageUtil.sendTranslated(initiator, "game.service_creation_failed");
                            }
                            return;
                          }
                          plugin
                              .getLogger()
                              .info(
                                  "Scheduler server "
                                      + instance.serverId()
                                      + " entered state "
                                      + instance.state());
                          onStarted.accept(instance.serverId());
                        }));
  }

  /**
   * Pre-start winner service right after voting ends.
   *
   * @param winner Winner game
   */
  private void preStartWinningService(GameConfig winner) {
    clearPreStartedService();
    clearPendingTeleportJoinState();

    launchGameServer(
        winner,
        null,
        serviceName -> {
          preStartedGameId = winner.getId();
          preStartedServiceName = serviceName;
          markGamePendingTeleport(winner, serviceName);
          Map<String, String> placeholders = new HashMap<>();
          placeholders.put("game", ColorUtil.withReset(winner.getName()));
          MessageUtil.broadcast("");
          MessageUtil.broadcastTranslated("game.creating_service", placeholders);
          MessageUtil.broadcast("");
          plugin
              .getLogger()
              .info("Pre-started winner server " + serviceName + " for game " + winner.getId());
        });
  }

  /** Clear cached pre-started service info. */
  private void clearPreStartedService() {
    preStartedGameId = null;
    preStartedServiceName = null;
  }

  private void markGamePendingTeleport(GameConfig game, String serviceName) {
    cancelScheduledTeleportTasks();
    pendingTeleportGameId = game.getId();
    pendingTeleportServiceName = serviceName;
    VotingSession.getInstance().setCurrentGameService(null);
  }

  private void clearPendingTeleportJoinState() {
    cancelScheduledTeleportTasks();
    pendingTeleportGameId = null;
    pendingTeleportServiceName = null;
    teleportTargets.clear();
  }

  private void clearPendingTeleportJoinState(String serviceName) {
    if (serviceName == null || pendingTeleportServiceName == null) {
      return;
    }
    if (pendingTeleportServiceName.equalsIgnoreCase(serviceName)) {
      clearPendingTeleportJoinState();
    }
  }

  private void clearPendingTeleportJoinStateIfStale() {
    if (pendingTeleportServiceName == null || pendingTeleportServiceName.isBlank()) {
      return;
    }
    String serviceName = pendingTeleportServiceName;
    long generation = teleportGeneration;
    plugin
        .getServerScheduler()
        .find(serviceName)
        .thenAccept(
            instance ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (isPendingTeleport(generation, serviceName)
                              && (instance.isEmpty() || !instance.get().active())) {
                            clearPendingTeleportJoinState(serviceName);
                          }
                        }))
        .exceptionally(
            error -> {
              plugin
                  .getLogger()
                  .warning(
                      "Failed to validate pending scheduler server "
                          + serviceName
                          + ": "
                          + error.getMessage());
              return null;
            });
  }

  private long beginScheduledTeleport(String serviceName) {
    cancelTeleportTaskHandles();
    scheduledTeleportServiceName = serviceName;
    return teleportGeneration;
  }

  private void cancelScheduledTeleportTasks() {
    teleportGeneration++;
    cancelTeleportTaskHandles();
    scheduledTeleportServiceName = null;
  }

  private void cancelTeleportTaskHandles() {
    if (readinessTask != null) {
      readinessTask.cancel();
      readinessTask = null;
    }
  }

  private boolean isPendingTeleport(long generation, String serviceName) {
    return teleportGeneration == generation
        && pendingTeleportServiceName != null
        && pendingTeleportServiceName.equalsIgnoreCase(serviceName);
  }

  private boolean isCurrentTeleport(long generation, String serviceName) {
    return isPendingTeleport(generation, serviceName)
        && scheduledTeleportServiceName != null
        && scheduledTeleportServiceName.equalsIgnoreCase(serviceName);
  }

  private void sendJoinWaitForTeleport(Player player, String gameName) {
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put(
        "game", ColorUtil.withReset(gameName == null || gameName.isBlank() ? "当前游戏" : gameName));
    MessageUtil.sendTranslated(player, "join.wait_for_teleport", placeholders);
  }

  private String getPendingTeleportGameName() {
    if (pendingTeleportGameId == null || pendingTeleportGameId.isBlank()) {
      return null;
    }
    GameConfig pendingGame = gamesManager.getGame(pendingTeleportGameId);
    return pendingGame == null ? pendingTeleportGameId : pendingGame.getName();
  }

  private void scheduleTeleport(String serviceName, GameConfig game) {
    long generation = beginScheduledTeleport(serviceName);

    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("game", ColorUtil.withReset(game.getName()));

    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("game.teleporting_when_ready", placeholders);
    waitForServiceReadyAndTeleport(
        serviceName, game, BRIDGE_READY_CHECK_INTERVAL_TICKS, generation);
    MessageUtil.broadcast("");
  }

  private void waitForServiceReadyAndTeleport(
      String serviceName, GameConfig game, int checkIntervalTicks, long generation) {
    AtomicBoolean checking = new AtomicBoolean();

    readinessTask =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> {
                  if (!isCurrentTeleport(generation, serviceName)) {
                    return;
                  }
                  if (!checking.compareAndSet(false, true)) {
                    return;
                  }
                  plugin
                      .getServerScheduler()
                      .find(serviceName)
                      .whenComplete(
                          (instance, error) ->
                              Bukkit.getScheduler()
                                  .runTask(
                                      plugin,
                                      () -> {
                                        checking.set(false);
                                        if (!isCurrentTeleport(generation, serviceName)) {
                                          return;
                                        }
                                        if (error != null) {
                                          plugin
                                              .getLogger()
                                              .warning(
                                                  "Failed while polling scheduler readiness for "
                                                      + serviceName
                                                      + ": "
                                                      + error.getMessage());
                                          return;
                                        }
                                        if (instance.isEmpty() || !instance.get().active()) {
                                          clearPendingTeleportJoinState(serviceName);
                                          Map<String, String> placeholders = new HashMap<>();
                                          placeholders.put(
                                              "game", ColorUtil.withReset(game.getName()));
                                          placeholders.put("service", serviceName);
                                          MessageUtil.broadcastTranslated(
                                              "teleport.service_missing_abort", placeholders);
                                          return;
                                        }
                                        if (instance.get().state() != ServerInstanceState.READY) {
                                          return;
                                        }
                                        if (readinessTask != null) {
                                          readinessTask.cancel();
                                          readinessTask = null;
                                        }
                                        plugin
                                            .getLogger()
                                            .info(
                                                "Server "
                                                    + serviceName
                                                    + " is ready, queueing player transfers"
                                                    + " immediately");
                                        Map<String, String> placeholders = new HashMap<>();
                                        placeholders.put(
                                            "game", ColorUtil.withReset(game.getName()));
                                        String message =
                                            com.talexck
                                                .gameVoting
                                                .utils
                                                .language
                                                .LanguageManager
                                                .getInstance()
                                                .getMessage(
                                                    "teleport.teleporting_now", placeholders);
                                        for (Player player : Bukkit.getOnlinePlayers()) {
                                          com.talexck.gameVoting.utils.display.ActionBarUtil
                                              .sendActionBar(player, message);
                                        }
                                        teleportPlayersToService(serviceName, game, generation);
                                      }));
                },
                0L,
                checkIntervalTicks);
  }

  /**
   * Teleport all online players to the specified service using Scheduler Bridge API. Executes "send
   * <player> <server>" command on the proxy service.
   *
   * @param serviceName The name of the service
   * @param game The game configuration
   */
  private void teleportPlayersToService(String serviceName, GameConfig game, long generation) {
    if (!isCurrentTeleport(generation, serviceName)) {
      return;
    }
    List<UUID> targets = teleportTargets.drainOnline(this::isOnlineLobbyPlayer);
    if (targets.isEmpty()) {
      plugin
          .getLogger()
          .warning(
              "Teleport snapshot is empty, skipping teleport to avoid sending unready players.");
      MessageUtil.broadcastTranslated("teleport.no_targets");
      clearPendingTeleportJoinState(serviceName);
      return;
    }
    plugin
        .getServerScheduler()
        .queueTransfers(serviceName, targets)
        .whenComplete(
            (ignored, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!isCurrentTeleport(generation, serviceName)) {
                            return;
                          }
                          if (error != null) {
                            plugin
                                .getLogger()
                                .severe(
                                    "Failed to queue player transfers to "
                                        + serviceName
                                        + ": "
                                        + error.getMessage());
                            MessageUtil.broadcastTranslated("teleport.commands_failed");
                            clearPendingTeleportJoinState(serviceName);
                            return;
                          }
                          plugin
                              .getLogger()
                              .info(
                                  "Queued "
                                      + targets.size()
                                      + " player transfers to "
                                      + serviceName);
                          clearPendingTeleportJoinState(serviceName);
                          VotingSession.getInstance().setCurrentGameService(serviceName);
                        }));

    // Schedule hologram update to show historical wins after a short delay
    // This allows players time to be teleported before hologram changes
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              updateHologramDisplays();
            },
            20L); // 1 second delay
  }

  /**
   * Capture current online players as teleport targets. This represents lobby members at game start
   * time.
   */
  private void snapshotPlayersToTeleport() {
    teleportTargets.capture(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList());
    plugin.getLogger().info("Captured " + teleportTargets.size() + " lobby players for teleport.");
  }

  /**
   * Capture a subset of online players as teleport targets.
   *
   * @param targetPlayers Eligible player UUID set
   */
  private void snapshotPlayersToTeleport(Set<UUID> targetPlayers) {
    teleportTargets.capture(
        Bukkit.getOnlinePlayers().stream()
            .map(Player::getUniqueId)
            .filter(targetPlayers::contains)
            .toList());
    plugin.getLogger().info("Captured " + teleportTargets.size() + " ready players for teleport.");
  }

  /** Handle /vote ready - Mark player as ready. */
  private boolean handleReady(Player player) {
    VotingSession session = VotingSession.getInstance();

    if (!session.isReadyPhase()) {
      MessageUtil.sendTranslated(player, "ready.not_active");
      return true;
    }

    if (session.isPlayerReady(player.getUniqueId())) {
      MessageUtil.sendTranslated(player, "ready.already_ready");
      return true;
    }

    ReadyVersionValidator.ValidationResult versionResult =
        ReadyVersionValidator.validate(player, gamesManager, session);
    if (!versionResult.allowed()) {
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("expected", versionResult.expectedVersion());
      placeholders.put(
          "current",
          versionResult.playerVersion() == null ? "Unknown" : versionResult.playerVersion());
      if (versionResult.detectionFailed()) {
        MessageUtil.sendTranslated(player, "ready.version_not_detected", placeholders);
      } else {
        MessageUtil.sendTranslated(player, "ready.version_mismatch", placeholders);
      }
      return true;
    }

    session.markPlayerReady(player.getUniqueId());
    com.talexck.gameVoting.utils.item.VoteItem.updateReadyItem(player, true);

    int readyCount = session.getReadyCount();
    int totalPlayers = Bukkit.getOnlinePlayers().size();

    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("player", player.getName());
    placeholders.put("count", String.valueOf(readyCount));
    placeholders.put("total", String.valueOf(totalPlayers));

    MessageUtil.sendTranslated(player, "ready.marked_ready");
    MessageUtil.broadcastTranslated("ready.player_ready", placeholders);

    // Check if all players are ready
    if (session.allPlayersReady()) {
      MessageUtil.broadcastTranslated("ready.all_ready");
      return handleGameStart(player);
    }

    return true;
  }

  /**
   * Handle /vote gamestart - Force start the game (only for vote starter). Can also be called by
   * console (when player is null).
   */
  private boolean handleGameStart(Player player) {
    VotingSession session = VotingSession.getInstance();

    if (!session.isReadyPhase()) {
      if (player != null) {
        MessageUtil.sendTranslated(player, "ready.not_active");
      }
      return true;
    }

    // Check if player is the vote starter (for manual force start)
    // Allow console to force start (player == null)
    if (player != null && !session.canForceStart(player.getUniqueId())) {
      MessageUtil.sendTranslated(player, "game.only_starter_can_force");
      return true;
    }

    // Stop countdown if running (manual force start)
    if (session.isCountdownActive()) {
      session.stopCountdown();
    }

    // Actually start the game
    return executeGameStart(player);
  }

  /**
   * Execute the actual game start logic. Can be called by force start or countdown completion.
   *
   * @param initiator The player who initiated (null if countdown)
   * @return true if command handled
   */
  public boolean executeGameStart(Player initiator) {
    VotingSession session = VotingSession.getInstance();

    // Get the winning game ID
    String winnerId = session.getWinner();
    if (winnerId == null) {
      if (initiator != null) {
        MessageUtil.sendTranslated(initiator, "game.no_winner");
      }
      return true;
    }

    // Get GameConfig from ID
    GameConfig winner = gamesManager.getGame(winnerId);
    if (winner == null) {
      if (initiator != null) {
        MessageUtil.sendTranslated(initiator, "game.config_not_found");
      }
      return true;
    }

    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("game", ColorUtil.withReset(winner.getName()));
    if (initiator != null) {
      placeholders.put("player", initiator.getName());
    }

    MessageUtil.broadcastTranslated("general.separator");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("game.starting");
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("game.game_label", placeholders);
    if (initiator != null) {
      MessageUtil.broadcastTranslated("game.started_by_player", placeholders);
    } else {
      MessageUtil.broadcastTranslated("game.started_by_ready");
    }
    MessageUtil.broadcast("");
    MessageUtil.broadcastTranslated("general.separator");

    // Remove vote items from all players
    for (Player online : Bukkit.getOnlinePlayers()) {
      com.talexck.gameVoting.utils.item.VoteItem.removeVoteItem(online);
    }

    // Save vote results to database before clearing session
    saveVoteResultToDatabase(session, winner);

    // Force start by vote starter should only teleport players who clicked ready.
    if (initiator != null
        && session.canForceStart(initiator.getUniqueId())
        && !session.allPlayersReady()) {
      snapshotPlayersToTeleport(session.getReadyPlayers());
      Map<String, String> placeholdersReadyOnly = new HashMap<>();
      placeholdersReadyOnly.put("count", String.valueOf(teleportTargets.size()));
      placeholdersReadyOnly.put("total", String.valueOf(Bukkit.getOnlinePlayers().size()));
      MessageUtil.broadcastTranslated("ready.force_start_ready_only", placeholdersReadyOnly);
    } else {
      // All-ready/auto-start path keeps existing behavior: teleport all lobby players.
      snapshotPlayersToTeleport();
    }

    // Start the game (reuse pre-started service when available)
    if (preStartedServiceName != null && winner.getId().equalsIgnoreCase(preStartedGameId)) {
      scheduleTeleport(preStartedServiceName, winner);
      plugin
          .getLogger()
          .info(
              "Using pre-started service for winner "
                  + winner.getId()
                  + ": "
                  + preStartedServiceName);
      clearPreStartedService();
    } else {
      startGame(winner, initiator);
      clearPreStartedService();
    }

    // Clear session
    session.clear();

    return true;
  }

  /** Handle /vote holograms subcommands. */
  private boolean handleHolograms(Player player, String[] args) {
    // Check permission
    if (!player.hasPermission("gamevoting.vote.admin")) {
      MessageUtil.sendTranslated(player, "command.no_permission");
      return true;
    }

    if (args.length < 2) {
      MessageUtil.sendTranslated(player, "command.holograms_usage");
      return true;
    }

    String subCmd = args[1].toLowerCase();

    switch (subCmd) {
      case "create":
        return handleHologramCreate(player);
      case "remove":
        return handleHologramRemove(player, args);
      case "list":
        return handleHologramList(player);
      default:
        MessageUtil.sendTranslated(player, "command.holograms_usage");
        return true;
    }
  }

  /** Handle /vote holograms create. */
  private boolean handleHologramCreate(Player player) {
    var hologramConfig = plugin.getHologramConfigManager();
    var location = new com.talexck.gameVoting.utils.hologram.HologramLocation(player.getLocation());

    int id = hologramConfig.addLocation(location);

    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("id", String.valueOf(id));
    MessageUtil.sendTranslated(player, "hologram.created", placeholders);

    // Update hologram display
    updateHologramDisplays();

    return true;
  }

  /** Handle /vote holograms remove <id>. */
  private boolean handleHologramRemove(Player player, String[] args) {
    if (args.length < 3) {
      MessageUtil.sendMessage(player, "&cUsage: /vote holograms remove <id>");
      return true;
    }

    var hologramConfig = plugin.getHologramConfigManager();

    try {
      int id = Integer.parseInt(args[2]);

      if (id < 0 || id >= hologramConfig.getLocationCount()) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("max", String.valueOf(hologramConfig.getLocationCount() - 1));
        MessageUtil.sendTranslated(player, "hologram.invalid_id", placeholders);
        return true;
      }

      hologramConfig.removeLocation(id);

      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("id", String.valueOf(id));
      MessageUtil.sendTranslated(player, "hologram.removed", placeholders);

      // Update hologram displays
      updateHologramDisplays();

    } catch (NumberFormatException e) {
      MessageUtil.sendMessage(player, "&cInvalid hologram ID!");
    }

    return true;
  }

  /** Handle /vote holograms list. */
  private boolean handleHologramList(Player player) {
    var hologramConfig = plugin.getHologramConfigManager();
    var locations = hologramConfig.getAllLocations();

    if (locations.isEmpty()) {
      MessageUtil.sendTranslated(player, "hologram.no_holograms");
      return true;
    }

    MessageUtil.sendTranslated(player, "hologram.list_header");

    for (int i = 0; i < locations.size(); i++) {
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("id", String.valueOf(i));
      placeholders.put("location", locations.get(i).serialize());
      MessageUtil.sendTranslated(player, "hologram.list_entry", placeholders);
    }

    return true;
  }

  /** Handle /vote session subcommands. */
  private boolean handleSession(Player player, String[] args) {
    if (args.length < 2) {
      MessageUtil.sendTranslated(player, "command.session_usage");
      return true;
    }

    String subCmd = args[1].toLowerCase();

    switch (subCmd) {
      case "list":
        return handleSessionList(player, args);
      case "stop":
        return handleSessionStop(player);
      default:
        MessageUtil.sendTranslated(player, "command.session_usage");
        return true;
    }
  }

  /** Handle /vote session list [page]. */
  private boolean handleSessionList(Player player, String[] args) {
    var dbManager = com.talexck.gameVoting.utils.database.DatabaseManager.getInstance();

    if (dbManager == null || !dbManager.hasVoteHistoryRepository()) {
      MessageUtil.sendMessage(player, "&cDatabase is not enabled! Cannot view session history.");
      return true;
    }

    var repository = dbManager.getVoteHistoryRepository();

    // Parse page number
    int page = 0;
    if (args.length > 2) {
      try {
        page = Integer.parseInt(args[2]) - 1; // Convert to 0-based
        if (page < 0) {
          MessageUtil.sendTranslated(player, "session.invalid_page");
          return true;
        }
      } catch (NumberFormatException e) {
        MessageUtil.sendTranslated(player, "session.invalid_page");
        return true;
      }
    }

    int pageSize = 10;
    var history = repository.getSessionHistory(page, pageSize);
    int totalSessions = repository.getTotalSessions();
    int totalPages = (int) Math.ceil((double) totalSessions / pageSize);

    if (history.isEmpty()) {
      MessageUtil.sendTranslated(player, "session.no_history");
      return true;
    }

    // Display header
    Map<String, String> headerPlaceholders = new HashMap<>();
    headerPlaceholders.put("page", String.valueOf(page + 1));
    headerPlaceholders.put("total", String.valueOf(totalPages));
    MessageUtil.sendTranslated(player, "session.list_header", headerPlaceholders);

    // Display entries
    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    for (var record : history) {
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("time", dateFormat.format(java.util.Date.from(record.getTimestamp())));
      placeholders.put("game", ColorUtil.withReset(record.getWinningGameName()));
      placeholders.put("votes", String.valueOf(record.getTotalVotes()));
      MessageUtil.sendTranslated(player, "session.list_entry", placeholders);
    }

    return true;
  }

  /** Handle /vote session stop. */
  private boolean handleSessionStop(Player player) {
    // Check permission
    if (!player.hasPermission("gamevoting.vote.admin")) {
      MessageUtil.sendTranslated(player, "command.no_permission");
      return true;
    }

    VotingSession session = VotingSession.getInstance();

    // Check if session is active (voting or ready phase)
    if (!session.isActive()
        && !session.isReadyPhase()
        && !session.isPreVotingReady()
        && pendingTeleportServiceName == null) {
      MessageUtil.sendTranslated(player, "command.no_active_session");
      return true;
    }

    // Clear session completely (this also stops countdown and cancels tasks)
    session.clear();
    clearPreStartedService();
    clearPendingTeleportJoinState();

    // Clear BossBar display for all players (do this AFTER clearing session to ensure tasks are
    // stopped)
    com.talexck.gameVoting.utils.display.BossBarManager bossBarManager =
        com.talexck.gameVoting.utils.display.BossBarManager.getInstance();
    for (Player online : Bukkit.getOnlinePlayers()) {
      bossBarManager.removeBar(online);
    }

    // Give appropriate items based on player count
    restoreLobbyVoteItems();

    // Update holograms to NOT_VOTING state
    updateHologramDisplays();

    MessageUtil.broadcastTranslated("command.session_stopped");

    return true;
  }

  /** Handle /vote reload - Reload plugin configuration. */
  private boolean handleReload(Player player) {
    // Check permission
    if (!player.hasPermission("gamevoting.vote.admin")) {
      MessageUtil.sendTranslated(player, "command.reload_no_permission");
      return true;
    }

    MessageUtil.sendTranslated(player, "command.reload_start");

    try {
      clearPreStartedService();
      clearPendingTeleportJoinState();

      // Reload main config
      plugin.reloadConfig();

      CompletableFuture<Integer> gameReload =
          gamesManager == null ? CompletableFuture.completedFuture(0) : gamesManager.reload();

      // Reload hologram configuration
      var hologramConfig = plugin.getHologramConfigManager();
      if (hologramConfig != null) {
        hologramConfig.reload();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("count", String.valueOf(hologramConfig.getLocationCount()));
        MessageUtil.sendTranslated(player, "command.reload_holograms", placeholders);
      }

      // Reload language files
      var languageManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
      if (languageManager != null) {
        languageManager.reload();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("lang", languageManager.getCurrentLanguage());
        MessageUtil.sendTranslated(player, "command.reload_language", placeholders);
      }

      gameReload.whenComplete(
          (count, error) ->
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        if (error != null) {
                          MessageUtil.sendTranslated(player, "command.reload_failed");
                          plugin
                              .getLogger()
                              .severe("Error reloading game catalog: " + error.getMessage());
                          return;
                        }
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("count", String.valueOf(count));
                        MessageUtil.sendTranslated(player, "command.reload_games", placeholders);
                        updateHologramDisplays();
                        MessageUtil.sendTranslated(player, "command.reload_success");
                        plugin.getLogger().info("Plugin reloaded by " + player.getName());
                      }));

    } catch (Exception e) {
      MessageUtil.sendTranslated(player, "command.reload_failed");
      plugin.getLogger().severe("Error reloading plugin: " + e.getMessage());
      e.printStackTrace();
    }

    return true;
  }

  /** Handle /vote join [game] - Join current running game or a specific game. */
  private boolean handleJoin(Player player, String[] args) {
    VotingSession session = VotingSession.getInstance();
    clearPendingTeleportJoinStateIfStale();
    String serviceName;
    GameConfig targetGame;

    if (args.length > 1) {
      String gameId = args[1];
      targetGame = gamesManager.getGame(gameId);
      if (targetGame == null) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("game", gameId);
        MessageUtil.sendTranslated(player, "command.game_not_found", placeholders);
        return true;
      }

      String serverId = targetGame.getServerId();
      if (serverId == null || serverId.isBlank()) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("game", ColorUtil.withReset(targetGame.getName()));
        MessageUtil.sendTranslated(player, "join.game_unavailable", placeholders);
        return true;
      }
      if (pendingTeleportGameId != null
          && pendingTeleportGameId.equalsIgnoreCase(targetGame.getId())) {
        sendJoinWaitForTeleport(player, targetGame.getName());
        return true;
      }
      serviceName = serverId;
    } else {
      JoinGateResult joinGate =
          evaluateJoinGate(null, session.getCurrentGameService(), pendingTeleportGameId);
      if (joinGate == JoinGateResult.WAIT_FOR_TELEPORT) {
        sendJoinWaitForTeleport(player, getPendingTeleportGameName());
        return true;
      }
      if (joinGate == JoinGateResult.NO_GAME) {
        MessageUtil.sendTranslated(player, "join.no_game");
        return true;
      }
      serviceName = session.getCurrentGameService();
      targetGame = findGameByServerId(serviceName);
      if (targetGame == null) {
        MessageUtil.sendTranslated(player, "join.game_unavailable");
        return true;
      }
    }

    queuePlayerTransfer(player, serviceName, targetGame);
    return true;
  }

  private void queuePlayerTransfer(Player player, String serverId, GameConfig game) {
    ReadyVersionValidator.ValidationResult versionResult =
        ReadyVersionValidator.validate(player, game);
    if (!versionResult.allowed()) {
      sendVersionValidationFailure(player, versionResult);
      return;
    }

    plugin
        .getServerScheduler()
        .find(serverId)
        .whenComplete(
            (instance, findError) -> {
              if (findError != null
                  || instance.isEmpty()
                  || instance.get().state() != ServerInstanceState.READY) {
                Bukkit.getScheduler()
                    .runTask(
                        plugin, () -> MessageUtil.sendTranslated(player, "join.game_unavailable"));
                return;
              }
              plugin
                  .getServerScheduler()
                  .queueTransfers(serverId, List.of(player.getUniqueId()))
                  .whenComplete(
                      (ignored, transferError) ->
                          Bukkit.getScheduler()
                              .runTask(
                                  plugin,
                                  () -> {
                                    if (transferError != null) {
                                      MessageUtil.sendTranslated(player, "join.failed");
                                      plugin
                                          .getLogger()
                                          .severe(
                                              "Failed to queue transfer for "
                                                  + player.getName()
                                                  + ": "
                                                  + transferError.getMessage());
                                      return;
                                    }
                                    MessageUtil.sendTranslated(player, "join.teleporting");
                                    plugin
                                        .getLogger()
                                        .info(
                                            "Queued transfer for "
                                                + player.getName()
                                                + " to "
                                                + serverId);
                                  }));
            });
  }

  private void sendVersionValidationFailure(
      Player player, ReadyVersionValidator.ValidationResult versionResult) {
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("expected", versionResult.expectedVersion());
    placeholders.put(
        "current",
        versionResult.playerVersion() == null ? "Unknown" : versionResult.playerVersion());
    if (versionResult.detectionFailed()) {
      MessageUtil.sendTranslated(player, "ready.version_not_detected", placeholders);
    } else {
      MessageUtil.sendTranslated(player, "ready.version_mismatch", placeholders);
    }
  }

  /** Handle /vote gamelist - List online minigame services grouped by game ID. */
  private boolean handleGameList(Player player) {
    if (!player.hasPermission("gamevoting.vote.admin")) {
      MessageUtil.sendTranslated(player, "command.no_permission");
      return true;
    }
    plugin
        .getServerScheduler()
        .list()
        .whenComplete(
            (instances, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (error != null) {
                            MessageUtil.sendTranslated(player, "command.gamelist_failed");
                            plugin
                                .getLogger()
                                .severe("Failed to fetch scheduler servers: " + error.getMessage());
                            return;
                          }
                          List<GameOnlineServices> onlineGames = getOnlineGameServices(instances);
                          if (onlineGames.isEmpty()) {
                            MessageUtil.sendTranslated(player, "command.gamelist_empty");
                            return;
                          }
                          MessageUtil.sendTranslated(player, "command.gamelist_header");
                          for (GameOnlineServices group : onlineGames) {
                            Map<String, String> placeholders = new HashMap<>();
                            placeholders.put("id", group.game().getId());
                            placeholders.put("name", ColorUtil.withReset(group.game().getName()));
                            placeholders.put("count", String.valueOf(group.services().size()));
                            placeholders.put(
                                "services",
                                group.services().stream()
                                    .map(GameServiceIds::display)
                                    .collect(Collectors.joining(", ")));
                            MessageUtil.sendTranslated(
                                player, "command.gamelist_entry", placeholders);
                          }
                        }));
    return true;
  }

  /** Handle /vote stopgame <service-id> - Stop one online minigame service. */
  private boolean handleStopGame(Player player, String[] args) {
    if (!player.hasPermission("gamevoting.vote.admin")) {
      MessageUtil.sendTranslated(player, "command.no_permission");
      return true;
    }

    if (args.length < 2) {
      MessageUtil.sendTranslated(player, "command.stopgame_usage");
      return true;
    }

    String targetServiceId = args[1];
    plugin
        .getServerScheduler()
        .list()
        .whenComplete(
            (instances, listError) -> {
              if (listError != null) {
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          Map<String, String> placeholders = new HashMap<>();
                          placeholders.put("game", targetServiceId);
                          placeholders.put("success", "0");
                          placeholders.put("failed", "1");
                          MessageUtil.sendTranslated(
                              player, "command.stopgame_failed", placeholders);
                        });
                plugin
                    .getLogger()
                    .severe("Failed to fetch scheduler servers: " + listError.getMessage());
                return;
              }
              var targetInstance =
                  GameServiceIds.resolve(instances, targetServiceId)
                      .filter(instance -> findGameByServerId(instance.serverId()) != null);
              if (targetInstance.isEmpty()) {
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          Map<String, String> placeholders = new HashMap<>();
                          placeholders.put("game", targetServiceId);
                          MessageUtil.sendTranslated(
                              player, "command.stopgame_not_found", placeholders);
                        });
                return;
              }
              GameConfig target = findGameByServerId(targetInstance.get().serverId());
              plugin
                  .getServerScheduler()
                  .stop(targetInstance.get().serverId())
                  .whenComplete(
                      (instance, stopError) ->
                          Bukkit.getScheduler()
                              .runTask(
                                  plugin,
                                  () -> {
                                    Map<String, String> placeholders = new HashMap<>();
                                    placeholders.put("game", targetServiceId);
                                    placeholders.put("success", stopError == null ? "1" : "0");
                                    placeholders.put("failed", stopError == null ? "0" : "1");
                                    if (stopError != null) {
                                      MessageUtil.sendTranslated(
                                          player, "command.stopgame_failed", placeholders);
                                      plugin
                                          .getLogger()
                                          .severe(
                                              "Failed to stop scheduler server "
                                                  + targetInstance.get().serverId()
                                                  + ": "
                                                  + stopError.getMessage());
                                      return;
                                    }
                                    MessageUtil.sendTranslated(
                                        player, "command.stopgame_success", placeholders);
                                    if (pendingTeleportGameId != null
                                        && pendingTeleportGameId.equalsIgnoreCase(target.getId())) {
                                      clearPendingTeleportJoinState();
                                    }
                                    if (preStartedGameId != null
                                        && preStartedGameId.equalsIgnoreCase(target.getId())) {
                                      clearPreStartedService();
                                    }
                                  }));
            });
    return true;
  }

  private GameConfig findGameByServerId(String serverId) {
    return gamesManager.getGames().stream()
        .filter(game -> game.getServerId() != null)
        .filter(game -> game.getServerId().equalsIgnoreCase(serverId))
        .findFirst()
        .orElse(null);
  }

  private boolean isOnlineLobbyPlayer(UUID playerId) {
    Player player = Bukkit.getPlayer(playerId);
    return player != null && player.isOnline();
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    teleportTargets.remove(event.getPlayer().getUniqueId());
  }

  public void shutdown() {
    clearPreStartedService();
    clearPendingTeleportJoinState();
  }

  private List<GameOnlineServices> getOnlineGameServices(List<ServerInstance> instances) {
    List<GameOnlineServices> result = new ArrayList<>();
    for (GameConfig game : gamesManager.getGames()) {
      String serverId = game.getServerId();
      if (serverId == null || serverId.isBlank()) {
        continue;
      }
      List<ServerInstance> services =
          instances.stream()
              .filter(instance -> instance.serverId().equalsIgnoreCase(serverId))
              .filter(this::isServiceOnline)
              .sorted(Comparator.comparing(ServerInstance::serverId, String.CASE_INSENSITIVE_ORDER))
              .toList();
      if (!services.isEmpty()) {
        result.add(new GameOnlineServices(game, services));
      }
    }

    return result;
  }

  private boolean isServiceOnline(ServerInstance service) {
    return service.state() == ServerInstanceState.READY;
  }

  /**
   * Parse /vote start duration argument. Supports minute values like: 1, 0.5, 0.1min.
   *
   * @param rawDuration Raw argument
   * @return Duration in seconds
   */
  private int parseDurationSeconds(String rawDuration) {
    if (rawDuration == null || rawDuration.isBlank()) {
      return DEFAULT_VOTING_DURATION_SECONDS;
    }

    String normalized = rawDuration.trim().toLowerCase(Locale.ROOT);
    if (normalized.endsWith("min")) {
      normalized = normalized.substring(0, normalized.length() - 3).trim();
    }

    double minutes = Double.parseDouble(normalized);
    if (!Double.isFinite(minutes) || minutes <= 0) {
      throw new IllegalArgumentException("Duration must be positive");
    }

    int seconds = (int) Math.round(minutes * 60.0D);
    if (seconds <= 0) {
      throw new IllegalArgumentException("Duration too small");
    }

    return seconds;
  }

  private String findEligibleWinner(Map<String, Integer> results, int playerCount) {
    return selectRandomWinner(
        results,
        gameId -> gamesManager.isGameAvailable(gameId, playerCount),
        ThreadLocalRandom.current());
  }

  static String selectRandomWinner(
      Map<String, Integer> results, Predicate<String> isAvailable, Random random) {
    int highestEligibleVotes = Integer.MIN_VALUE;
    List<String> candidates = new ArrayList<>();

    for (Map.Entry<String, Integer> entry : results.entrySet()) {
      if (!isAvailable.test(entry.getKey())) {
        continue;
      }

      int votes = entry.getValue();
      if (votes > highestEligibleVotes) {
        highestEligibleVotes = votes;
        candidates.clear();
        candidates.add(entry.getKey());
      } else if (votes == highestEligibleVotes) {
        candidates.add(entry.getKey());
      }
    }

    if (candidates.isEmpty()) {
      return null;
    }

    return candidates.get(random.nextInt(candidates.size()));
  }

  private void broadcastNoEligibleGames(int onlineCount) {
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("current", String.valueOf(onlineCount));
    MessageUtil.broadcastTranslated("voting.no_eligible_games", placeholders);
  }

  private void restoreLobbyVoteItems() {
    int onlineCount = Bukkit.getOnlinePlayers().size();
    int requiredPlayers = VotingSession.getInstance().getRequiredPlayers();

    for (Player online : Bukkit.getOnlinePlayers()) {
      com.talexck.gameVoting.utils.item.VoteItem.removeVoteItem(online);

      if (onlineCount >= requiredPlayers) {
        com.talexck.gameVoting.utils.item.VoteItem.giveStartVotingItem(online);
      } else {
        com.talexck.gameVoting.utils.item.VoteItem.giveInsufficientPlayersItem(online);
      }
    }
  }

  /**
   * Format seconds to minute text for language placeholders.
   *
   * @param durationSeconds Duration in seconds
   * @return Minute text with up to 2 decimals
   */
  private String formatDurationMinutes(int durationSeconds) {
    DecimalFormat format = new DecimalFormat("0.##");
    return format.format(durationSeconds / 60.0D);
  }

  /**
   * Save vote result to database after game starts.
   *
   * @param session The voting session
   * @param winner The winning game config
   */
  private void saveVoteResultToDatabase(VotingSession session, GameConfig winner) {
    var dbManager = com.talexck.gameVoting.utils.database.DatabaseManager.getInstance();

    if (dbManager == null || !dbManager.hasVoteHistoryRepository()) {
      plugin.getLogger().warning("Database not available - vote result not saved");
      return;
    }

    try {
      var repository = dbManager.getVoteHistoryRepository();

      // Build vote history record
      var voteHistory =
          new com.talexck.gameVoting.voting.VoteHistory.Builder()
              .sessionId(UUID.randomUUID())
              .timestamp(java.time.Instant.now())
              .winningGameId(winner.getId())
              .winningGameName(winner.getName())
              .totalVotes(session.getTotalVoteCount())
              .playerCount(Bukkit.getOnlinePlayers().size())
              .voteDetails(new HashMap<>(session.getVoteCounts()))
              .build();

      // Save to database asynchronously
      Bukkit.getScheduler()
          .runTaskAsynchronously(
              plugin,
              () -> {
                boolean success = repository.saveSession(voteHistory);
                if (success) {
                  plugin
                      .getLogger()
                      .info(
                          "Saved vote result to database: "
                              + winner.getName()
                              + " won with "
                              + session.getTotalVoteCount()
                              + " votes");
                } else {
                  plugin.getLogger().warning("Failed to save vote result to database");
                }
              });
    } catch (Exception e) {
      plugin.getLogger().severe("Exception saving vote result to database: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /** Update all hologram displays based on current voting state. */
  private void updateHologramDisplays() {
    var hologramConfig = plugin.getHologramConfigManager();
    var displayManager = plugin.getHologramDisplayManager();
    var locations = hologramConfig.getAllLocations();

    if (locations.isEmpty()) {
      return;
    }

    VotingSession session = VotingSession.getInstance();
    var state =
        com.talexck.gameVoting.utils.hologram.HologramDisplayManager.DisplayState.NOT_VOTING;

    if (session.isPreVotingReady()) {
      state =
          com.talexck
              .gameVoting
              .utils
              .hologram
              .HologramDisplayManager
              .DisplayState
              .PRE_VOTING_READY;
    } else if (session.isActive()) {
      state =
          com.talexck.gameVoting.utils.hologram.HologramDisplayManager.DisplayState.VOTING_ACTIVE;
    } else if (session.isReadyPhase()) {
      state = com.talexck.gameVoting.utils.hologram.HologramDisplayManager.DisplayState.VOTE_ENDED;
    }

    displayManager.updateAllHolograms(state, locations);
  }

  private OfflinePlayer resolvePlayerByName(String playerName) {
    Player online = Bukkit.getPlayerExact(playerName);
    if (online != null) {
      return online;
    }

    for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
      String name = offlinePlayer.getName();
      if (name != null && name.equalsIgnoreCase(playerName)) {
        return offlinePlayer;
      }
    }
    return null;
  }

  private record GameOnlineServices(GameConfig game, List<ServerInstance> services) {}
}
