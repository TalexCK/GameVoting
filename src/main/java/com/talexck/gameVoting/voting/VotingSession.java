package com.talexck.gameVoting.voting;

import com.talexck.gameVoting.config.GameConfig;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages the current voting session state. Singleton pattern to ensure only one voting session
 * exists. Supports multi-voting (1-3 votes per player) with countdown display.
 */
public class VotingSession {
  private static VotingSession instance;
  private static final int MAX_VOTES = 3; // Maximum 3 votes per player
  private static final int MAX_NEGATIVE_VOTES_PER_GAME = 1;
  private static final int VOTE_LOCK_MIN_PLAYERS = 4;

  private boolean active;
  private final Map<UUID, Set<String>> playerVotes; // UUID -> 正向票游戏集合
  private final Map<UUID, Map<String, Integer>> playerNegativeVotes; // UUID -> 游戏ID -> 否定票数
  private final Map<String, Integer> positiveVoteCounts; // game ID -> 正向票数
  private final Map<String, Integer> negativeVoteCounts; // game ID -> 否定票数
  private BukkitTask timerTask;
  private BukkitTask countdownTask;
  private Runnable onEndCallback;
  private long startTime;
  private int durationSeconds;

  // Ready system state
  private boolean readyPhase; // True when voting ended, waiting for players to ready up
  private final Set<UUID> readyPlayers; // Players who are ready
  private UUID voteStarter; // Player who started the vote (can force start)

  // Countdown state (10 second countdown when all players ready)
  private BukkitTask countdownToStartTask; // Task for 10-second countdown
  private int countdownSeconds; // Current countdown time
  private boolean countdownActive; // Is countdown currently running

  // Current game service (for /vote join)
  private String currentGameService; // Scheduler server name of running game

  // Pre-voting ready phase (before voting starts)
  private boolean preVotingReady; // True when waiting for players to trigger voting
  private final Set<UUID> preVotingReadyPlayers; // Players who marked ready to start voting
  private int requiredPlayers = 2; // Minimum players required to start voting
  private int pendingVotingDurationSeconds = 60; // Duration to use when voting actually starts
  private String lockedWinnerId; // Final winner fixed after voting ends
  private final Set<UUID> pendingVoteLockedPlayers; // 需要在下一次有效投票中生效的玩家
  private final Set<UUID> activeVoteLockedPlayers; // 当前轮次实际被禁投的玩家
  private boolean currentRoundAppliesVoteLock;

  private VotingSession() {
    this.active = false;
    this.playerVotes = new HashMap<>();
    this.playerNegativeVotes = new HashMap<>();
    this.positiveVoteCounts = new HashMap<>();
    this.negativeVoteCounts = new HashMap<>();
    this.timerTask = null;
    this.countdownTask = null;
    this.onEndCallback = null;
    this.startTime = 0;
    this.durationSeconds = 0;
    this.readyPhase = false;
    this.readyPlayers = new HashSet<>();
    this.voteStarter = null;
    this.countdownToStartTask = null;
    this.countdownSeconds = 10;
    this.countdownActive = false;
    this.currentGameService = null;
    this.preVotingReady = false;
    this.preVotingReadyPlayers = new HashSet<>();
    this.lockedWinnerId = null;
    this.pendingVoteLockedPlayers = new HashSet<>();
    this.activeVoteLockedPlayers = new HashSet<>();
    this.currentRoundAppliesVoteLock = false;
  }

  /**
   * Get the singleton instance of the voting session.
   *
   * @return The voting session instance
   */
  public static VotingSession getInstance() {
    if (instance == null) {
      instance = new VotingSession();
    }
    return instance;
  }

  /**
   * Start a new voting session with a timer and countdown display. Clears all previous votes.
   *
   * @param durationSeconds Duration in seconds
   * @param plugin Plugin instance for scheduling
   * @param callback Callback to execute when voting ends
   */
  public void startVoting(int durationSeconds, org.bukkit.plugin.Plugin plugin, Runnable callback) {
    active = true;
    readyPhase = false;
    readyPlayers.clear();
    playerVotes.clear();
    playerNegativeVotes.clear();
    positiveVoteCounts.clear();
    negativeVoteCounts.clear();
    lockedWinnerId = null;
    activateVoteLocksForCurrentRound();
    this.onEndCallback = callback;
    this.startTime = System.currentTimeMillis();
    this.durationSeconds = durationSeconds;

    // Cancel existing timers if any
    if (timerTask != null) {
      timerTask.cancel();
    }
    if (countdownTask != null) {
      countdownTask.cancel();
    }

    // Schedule automatic end
    long ticks = durationSeconds * 20L; // Convert seconds to ticks
    timerTask =
        Bukkit.getScheduler()
            .runTaskLater(
                plugin,
                () -> {
                  if (active) {
                    if (onEndCallback != null) {
                      onEndCallback.run();
                    }
                  }
                },
                ticks);

    // Start countdown display task (runs every second)
    VotingCountdownTask countdownDisplay = new VotingCountdownTask(this, durationSeconds);
    countdownTask = countdownDisplay.runTaskTimer(plugin, 0L, 20L);
  }

  /** Start a new voting session without a timer (legacy method). Clears all previous votes. */
  public void startVoting() {
    active = true;
    readyPhase = false;
    readyPlayers.clear();
    playerVotes.clear();
    playerNegativeVotes.clear();
    positiveVoteCounts.clear();
    negativeVoteCounts.clear();
    lockedWinnerId = null;
    activateVoteLocksForCurrentRound();
    this.startTime = System.currentTimeMillis();
    this.durationSeconds = 0;
  }

  /**
   * Stop the current voting session.
   *
   * @return Map of game IDs to vote counts, sorted by count (descending)
   */
  public Map<String, Integer> stopVoting() {
    active = false;

    // Cancel timers if running
    if (timerTask != null) {
      timerTask.cancel();
      timerTask = null;
    }
    if (countdownTask != null) {
      countdownTask.cancel();
      countdownTask = null;
    }

    // Clean up boss bars
    com.talexck.gameVoting.utils.display.BossBarManager manager =
        com.talexck.gameVoting.utils.display.BossBarManager.getInstance();
    for (Player player : Bukkit.getOnlinePlayers()) {
      manager.removeBar(player);
    }

    // Create sorted map of results
    Map<String, Integer> results = new LinkedHashMap<>();
    getVoteCounts().entrySet().stream()
        .sorted(
            Map.Entry.<String, Integer>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
        .forEachOrdered(e -> results.put(e.getKey(), e.getValue()));

    return results;
  }

  /**
   * Get remaining time in seconds.
   *
   * @return Remaining seconds, or 0 if not active or no timer
   */
  public int getRemainingSeconds() {
    if (!active || durationSeconds == 0) {
      return 0;
    }
    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
    long total = durationSeconds;
    return (int) Math.max(0, total - elapsed);
  }

  /**
   * Get the winning game ID (most votes).
   *
   * @return The game ID with most votes, or null if no votes
   */
  public String getWinner() {
    if (lockedWinnerId != null) {
      return lockedWinnerId;
    }
    return getVoteCounts().entrySet().stream()
        .max(
            Map.Entry.<String, Integer>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
        .map(Map.Entry::getKey)
        .orElse(null);
  }

  /**
   * Lock the winner after voting ends so ready phase logic stays stable.
   *
   * @param gameId final winner id, null to clear
   */
  public void setLockedWinner(String gameId) {
    this.lockedWinnerId = gameId;
  }

  /**
   * Record or remove a vote for a game (toggle behavior). Players can vote for up to 3 games.
   *
   * @param player The player voting
   * @param game The game being voted for
   * @return The result of the vote operation
   */
  public VoteResult vote(Player player, GameConfig game) {
    if (!active) {
      return VoteResult.SESSION_INACTIVE;
    }
    if (isVoteLocked(player)) {
      return VoteResult.PLAYER_LOCKED;
    }

    UUID playerId = player.getUniqueId();
    String gameId = game.getId();

    Set<String> votes = playerVotes.computeIfAbsent(playerId, k -> new HashSet<>());

    if (votes.contains(gameId)) {
      votes.remove(gameId);
      cleanupPositiveVotes(playerId, votes);
      decrementGameCount(positiveVoteCounts, gameId);
      return VoteResult.REMOVED;
    }

    if (!canVote(player)) {
      return VoteResult.LIMIT_REACHED;
    }

    votes.add(gameId);
    incrementGameCount(positiveVoteCounts, gameId);
    return VoteResult.ADDED;
  }

  /**
   * 新增一张否定票。
   *
   * @param player 投票玩家
   * @param game 目标游戏
   * @return 操作结果
   */
  public VoteResult addNegativeVote(Player player, GameConfig game) {
    if (!active) {
      return VoteResult.SESSION_INACTIVE;
    }
    if (isVoteLocked(player)) {
      return VoteResult.PLAYER_LOCKED;
    }

    UUID playerId = player.getUniqueId();
    String gameId = game.getId();
    Map<String, Integer> negativeVotes =
        playerNegativeVotes.computeIfAbsent(playerId, k -> new HashMap<>());
    if (negativeVotes.getOrDefault(gameId, 0) >= MAX_NEGATIVE_VOTES_PER_GAME) {
      return VoteResult.NEGATIVE_VOTE_LIMIT_REACHED;
    }
    if (!canVote(player)) {
      return VoteResult.LIMIT_REACHED;
    }

    negativeVotes.merge(gameId, 1, Integer::sum);
    incrementGameCount(negativeVoteCounts, gameId);
    return VoteResult.ADDED;
  }

  /**
   * 移除一张否定票。
   *
   * @param player 投票玩家
   * @param game 目标游戏
   * @return 操作结果
   */
  public VoteResult removeNegativeVote(Player player, GameConfig game) {
    if (!active) {
      return VoteResult.SESSION_INACTIVE;
    }
    if (isVoteLocked(player)) {
      return VoteResult.PLAYER_LOCKED;
    }

    UUID playerId = player.getUniqueId();
    String gameId = game.getId();
    Map<String, Integer> negativeVotes = playerNegativeVotes.get(playerId);
    if (negativeVotes == null) {
      return VoteResult.NOTHING_TO_REMOVE;
    }

    int currentCount = negativeVotes.getOrDefault(gameId, 0);
    if (currentCount <= 0) {
      return VoteResult.NOTHING_TO_REMOVE;
    }

    if (currentCount == 1) {
      negativeVotes.remove(gameId);
    } else {
      negativeVotes.put(gameId, currentCount - 1);
    }
    cleanupNegativeVotes(playerId, negativeVotes);
    decrementGameCount(negativeVoteCounts, gameId);
    return VoteResult.REMOVED;
  }

  /**
   * Check if a player has voted for a specific game.
   *
   * @param player The player to check
   * @param gameId The game ID
   * @return true if the player has voted for this game
   */
  public boolean hasVotedFor(Player player, String gameId) {
    Set<String> votes = playerVotes.get(player.getUniqueId());
    return votes != null && votes.contains(gameId);
  }

  /**
   * 获取玩家对某个游戏投出的否定票数量。
   *
   * @param player 玩家
   * @param gameId 游戏ID
   * @return 否定票数量
   */
  public int getNegativeVoteCount(Player player, String gameId) {
    Map<String, Integer> negativeVotes = playerNegativeVotes.get(player.getUniqueId());
    return negativeVotes == null ? 0 : negativeVotes.getOrDefault(gameId, 0);
  }

  /**
   * 获取玩家已投出的正向票数量。
   *
   * @param player 玩家
   * @return 正向票数量
   */
  public int getPlayerPositiveVoteCount(Player player) {
    Set<String> votes = playerVotes.get(player.getUniqueId());
    return votes == null ? 0 : votes.size();
  }

  /**
   * 获取玩家已投出的否定票总数。
   *
   * @param player 玩家
   * @return 否定票总数
   */
  public int getPlayerNegativeVoteCount(Player player) {
    Map<String, Integer> negativeVotes = playerNegativeVotes.get(player.getUniqueId());
    return negativeVotes == null
        ? 0
        : negativeVotes.values().stream().mapToInt(Integer::intValue).sum();
  }

  /**
   * Get the number of votes a player has cast.
   *
   * @param player The player to check
   * @return The number of votes (0-3)
   */
  public int getPlayerVoteCount(Player player) {
    return getPlayerPositiveVoteCount(player) + getPlayerNegativeVoteCount(player);
  }

  /**
   * Get all game IDs that a player voted for.
   *
   * @param player The player to check
   * @return Set of game IDs, empty if no votes
   */
  public Set<String> getPlayerVotes(Player player) {
    Set<String> votes = playerVotes.get(player.getUniqueId());
    return votes == null ? new HashSet<>() : new HashSet<>(votes);
  }

  /**
   * Check if a player can vote (under vote limit).
   *
   * @param player The player to check
   * @return true if the player can cast more votes
   */
  public boolean canVote(Player player) {
    return getPlayerVoteCount(player) < MAX_VOTES;
  }

  /**
   * Check if a player has voted.
   *
   * @param player The player to check
   * @return true if the player has voted
   */
  public boolean hasVoted(Player player) {
    return getPlayerVoteCount(player) > 0;
  }

  /**
   * 当前轮次该玩家是否被禁止投票。
   *
   * @param player 玩家
   * @return 是否禁投
   */
  public boolean isVoteLocked(Player player) {
    return active
        && currentRoundAppliesVoteLock
        && activeVoteLockedPlayers.contains(player.getUniqueId());
  }

  /**
   * 将玩家加入下一次有效投票的禁投列表。 如果当前轮次允许生效，则立即阻止其后续投票操作。
   *
   * @param playerId 玩家 UUID
   */
  public void lockPlayerForNextVote(UUID playerId) {
    pendingVoteLockedPlayers.add(playerId);
    if (active && currentRoundAppliesVoteLock) {
      activeVoteLockedPlayers.add(playerId);
    }
  }

  /**
   * 取消玩家待生效或本轮生效中的禁投状态。
   *
   * @param playerId 玩家 UUID
   * @return 是否实际移除了状态
   */
  public boolean unlockPlayerForNextVote(UUID playerId) {
    boolean removedPending = pendingVoteLockedPlayers.remove(playerId);
    boolean removedActive = activeVoteLockedPlayers.remove(playerId);
    return removedPending || removedActive;
  }

  /**
   * 玩家是否仍带有待生效的禁投状态。
   *
   * @param playerId 玩家 UUID
   * @return 是否存在待生效禁投
   */
  public boolean hasPendingVoteLock(UUID playerId) {
    return pendingVoteLockedPlayers.contains(playerId);
  }

  /**
   * 在一轮投票结束后结算禁投状态。
   *
   * @param consumeLockedPlayers true 表示本轮正常出结果，需要消耗本轮生效的禁投
   */
  public void finishVotingRound(boolean consumeLockedPlayers) {
    if (consumeLockedPlayers && currentRoundAppliesVoteLock) {
      pendingVoteLockedPlayers.removeAll(activeVoteLockedPlayers);
    }
    activeVoteLockedPlayers.clear();
    currentRoundAppliesVoteLock = false;
  }

  /**
   * Get the game ID that a player voted for (legacy single-vote method). Returns the first vote if
   * player has multiple votes.
   *
   * @param player The player to check
   * @return The game ID, or null if the player hasn't voted
   * @deprecated Use getPlayerVotes() for multi-vote support
   */
  @Deprecated
  public String getPlayerVote(Player player) {
    Set<String> votes = playerVotes.get(player.getUniqueId());
    if (votes == null || votes.isEmpty()) {
      return null;
    }
    return votes.iterator().next();
  }

  /**
   * Get the current vote count for a specific game.
   *
   * @param gameId The game ID
   * @return The vote count
   */
  public int getVoteCount(String gameId) {
    return positiveVoteCounts.getOrDefault(gameId, 0) - negativeVoteCounts.getOrDefault(gameId, 0);
  }

  /**
   * Get all current vote counts.
   *
   * @return Map of game IDs to vote counts
   */
  public Map<String, Integer> getVoteCounts() {
    Set<String> gameIds = new HashSet<>(positiveVoteCounts.keySet());
    gameIds.addAll(negativeVoteCounts.keySet());

    Map<String, Integer> results = new HashMap<>();
    for (String gameId : gameIds) {
      results.put(gameId, getVoteCount(gameId));
    }
    return results;
  }

  /**
   * Check if voting is currently active.
   *
   * @return true if voting is active
   */
  public boolean isActive() {
    return active;
  }

  /**
   * Get the total number of unique voters.
   *
   * @return The total number of players who voted
   */
  public int getTotalVotes() {
    Set<UUID> voters = new HashSet<>(playerVotes.keySet());
    voters.addAll(playerNegativeVotes.keySet());
    return voters.size();
  }

  /**
   * Get the sum of all votes cast (can be more than total voters with multi-voting).
   *
   * @return The sum of all votes
   */
  public int getTotalVoteCount() {
    return positiveVoteCounts.values().stream().mapToInt(Integer::intValue).sum()
        + negativeVoteCounts.values().stream().mapToInt(Integer::intValue).sum();
  }

  /** 清空当前轮次会话数据，但保留待生效的禁投状态。 */
  public void clear() {
    active = false;
    playerVotes.clear();
    playerNegativeVotes.clear();
    positiveVoteCounts.clear();
    negativeVoteCounts.clear();
    lockedWinnerId = null;
    readyPhase = false;
    readyPlayers.clear();
    voteStarter = null;
    if (timerTask != null) {
      timerTask.cancel();
      timerTask = null;
    }
    if (countdownTask != null) {
      countdownTask.cancel();
      countdownTask = null;
    }
    // Stop countdown task
    stopCountdown();
    currentGameService = null;
    preVotingReady = false;
    preVotingReadyPlayers.clear();
    activeVoteLockedPlayers.clear();
    currentRoundAppliesVoteLock = false;
  }

  /** 完整清空会话与待生效禁投，供测试或全量重置使用。 */
  public void clearAll() {
    clear();
    pendingVoteLockedPlayers.clear();
  }

  private void incrementGameCount(Map<String, Integer> target, String gameId) {
    target.merge(gameId, 1, Integer::sum);
  }

  private void decrementGameCount(Map<String, Integer> target, String gameId) {
    int updatedCount = target.getOrDefault(gameId, 0) - 1;
    if (updatedCount <= 0) {
      target.remove(gameId);
      return;
    }
    target.put(gameId, updatedCount);
  }

  private void cleanupPositiveVotes(UUID playerId, Set<String> votes) {
    if (votes.isEmpty()) {
      playerVotes.remove(playerId);
    }
  }

  private void cleanupNegativeVotes(UUID playerId, Map<String, Integer> negativeVotes) {
    if (negativeVotes.isEmpty()) {
      playerNegativeVotes.remove(playerId);
    }
  }

  // === Ready System Methods ===

  /** Start the ready phase after voting ends. */
  public void startReadyPhase() {
    this.readyPhase = true;
    this.readyPlayers.clear();
  }

  /**
   * Check if currently in ready phase.
   *
   * @return true if in ready phase
   */
  public boolean isReadyPhase() {
    return readyPhase;
  }

  /**
   * Mark a player as ready.
   *
   * @param playerId Player UUID
   * @return true if player was marked ready (false if already ready)
   */
  public boolean markPlayerReady(UUID playerId) {
    if (!readyPhase) {
      return false;
    }
    return readyPlayers.add(playerId);
  }

  /**
   * Check if a player is ready.
   *
   * @param playerId Player UUID
   * @return true if player is ready
   */
  public boolean isPlayerReady(UUID playerId) {
    return readyPlayers.contains(playerId);
  }

  /**
   * Get the number of ready players.
   *
   * @return Number of ready players
   */
  public int getReadyCount() {
    return readyPlayers.size();
  }

  /**
   * Get a copy of all players who marked ready.
   *
   * @return Ready player UUID set
   */
  public Set<UUID> getReadyPlayers() {
    return new HashSet<>(readyPlayers);
  }

  /**
   * Check if all online players are ready.
   *
   * @return true if all players are ready
   */
  public boolean allPlayersReady() {
    if (!readyPhase) {
      return false;
    }
    int onlineCount = Bukkit.getOnlinePlayers().size();
    return onlineCount > 0 && readyPlayers.size() >= onlineCount;
  }

  /**
   * Unmark a player as ready (toggle ready status).
   *
   * @param playerId Player UUID
   * @return true if player was unmarked (false if wasn't ready)
   */
  public boolean unmarkPlayerReady(UUID playerId) {
    if (!readyPhase) {
      return false;
    }
    return readyPlayers.remove(playerId);
  }

  /**
   * Check if the countdown is currently active.
   *
   * @return true if countdown is running
   */
  public boolean isCountdownActive() {
    return countdownActive;
  }

  /**
   * Get the current countdown seconds remaining.
   *
   * @return Seconds remaining (0-10)
   */
  public int getCountdownSeconds() {
    return countdownSeconds;
  }

  /**
   * Start 10-second countdown with ActionBar display. Called when all players are ready.
   *
   * @param plugin Plugin instance for scheduling
   * @param onComplete Callback to execute when countdown finishes
   */
  public void startCountdown(org.bukkit.plugin.Plugin plugin, Runnable onComplete) {
    // Cancel existing countdown if any
    stopCountdown();

    countdownSeconds = 10;
    countdownActive = true;

    // Create countdown task (runs every second)
    countdownToStartTask =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> {
                  if (countdownSeconds <= 0) {
                    // Countdown finished
                    stopCountdown();
                    countdownActive = false;
                    if (onComplete != null) {
                      onComplete.run();
                    }
                    return;
                  }

                  // Display action bar to all online players
                  var langManager =
                      com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
                  java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                  placeholders.put("seconds", String.valueOf(countdownSeconds));
                  for (Player player : Bukkit.getOnlinePlayers()) {
                    com.talexck.gameVoting.utils.display.ActionBarUtil.sendActionBar(
                        player, langManager.getMessage("game.countdown_actionbar", placeholders));
                  }

                  countdownSeconds--;
                },
                0L,
                20L); // Run immediately, then every 20 ticks (1 second)
  }

  /** Stop and reset the countdown. Called when a player unreadies during countdown. */
  public void stopCountdown() {
    if (countdownToStartTask != null) {
      countdownToStartTask.cancel();
      countdownToStartTask = null;
    }
    countdownActive = false;
    countdownSeconds = 10;

    // Clear action bars for all players
    for (Player player : Bukkit.getOnlinePlayers()) {
      com.talexck.gameVoting.utils.display.ActionBarUtil.clearActionBar(player);
    }
  }

  /**
   * Set the player who started the vote (can force start).
   *
   * @param playerId Player UUID
   */
  public void setVoteStarter(UUID playerId) {
    this.voteStarter = playerId;
  }

  /**
   * Get the player who started the vote.
   *
   * @return Player UUID, or null if none
   */
  public UUID getVoteStarter() {
    return voteStarter;
  }

  /**
   * Check if a player can force start the game.
   *
   * @param playerId Player UUID
   * @return true if player is the vote starter
   */
  public boolean canForceStart(UUID playerId) {
    return voteStarter != null && voteStarter.equals(playerId);
  }

  /**
   * Set the current game service name (for /vote join).
   *
   * @param serviceName Scheduler server name
   */
  public void setCurrentGameService(String serviceName) {
    this.currentGameService = serviceName;
  }

  /**
   * Get the current game service name.
   *
   * @return Scheduler server name, or null if no game running
   */
  public String getCurrentGameService() {
    return currentGameService;
  }

  /**
   * Check if there is a game currently running.
   *
   * @return true if a game service is active
   */
  public boolean hasCurrentGame() {
    return currentGameService != null && !currentGameService.isEmpty();
  }

  // === Pre-Voting Ready Phase Methods ===

  /** Start the pre-voting ready phase (waiting for players to trigger voting). */
  public void startPreVotingReady() {
    this.preVotingReady = true;
    this.preVotingReadyPlayers.clear();
  }

  /**
   * Check if currently in pre-voting ready phase.
   *
   * @return true if in pre-voting ready phase
   */
  public boolean isPreVotingReady() {
    return preVotingReady;
  }

  /**
   * Mark a player as ready to start voting.
   *
   * @param playerId Player UUID
   * @return true if player was marked ready (false if already ready)
   */
  public boolean markPreVotingReady(UUID playerId) {
    if (!preVotingReady) {
      return false;
    }
    return preVotingReadyPlayers.add(playerId);
  }

  /**
   * Check if a player is ready to start voting.
   *
   * @param playerId Player UUID
   * @return true if player is ready
   */
  public boolean isPreVotingPlayerReady(UUID playerId) {
    return preVotingReadyPlayers.contains(playerId);
  }

  /**
   * Get the number of players ready to start voting.
   *
   * @return Number of ready players
   */
  public int getPreVotingReadyCount() {
    return preVotingReadyPlayers.size();
  }

  /**
   * Check if all online players are ready to start voting.
   *
   * @return true if all players are ready
   */
  public boolean allPlayersReadyToVote() {
    if (!preVotingReady) {
      return false;
    }
    int onlineCount = Bukkit.getOnlinePlayers().size();
    return onlineCount >= requiredPlayers && preVotingReadyPlayers.size() >= onlineCount;
  }

  /**
   * Unmark a player as ready to start voting.
   *
   * @param playerId Player UUID
   * @return true if player was unmarked (false if wasn't ready)
   */
  public boolean unmarkPreVotingReady(UUID playerId) {
    if (!preVotingReady) {
      return false;
    }
    return preVotingReadyPlayers.remove(playerId);
  }

  /** End the pre-voting ready phase. */
  public void endPreVotingReady() {
    this.preVotingReady = false;
    this.preVotingReadyPlayers.clear();
  }

  /**
   * Get the required number of players to start voting.
   *
   * @return Required player count
   */
  public int getRequiredPlayers() {
    return requiredPlayers;
  }

  /**
   * Set the required number of players to start voting.
   *
   * @param count Required player count
   */
  public void setRequiredPlayers(int count) {
    this.requiredPlayers = count;
  }

  /**
   * Get the pending voting duration.
   *
   * @return Pending voting duration in seconds
   */
  public int getPendingVotingDuration() {
    return pendingVotingDurationSeconds;
  }

  /**
   * Set the pending voting duration.
   *
   * @param durationSeconds Voting duration in seconds
   */
  public void setPendingVotingDuration(int durationSeconds) {
    this.pendingVotingDurationSeconds = durationSeconds;
  }

  private void activateVoteLocksForCurrentRound() {
    activeVoteLockedPlayers.clear();
    currentRoundAppliesVoteLock = Bukkit.getOnlinePlayers().size() >= VOTE_LOCK_MIN_PLAYERS;
    if (!currentRoundAppliesVoteLock) {
      return;
    }

    for (Player online : Bukkit.getOnlinePlayers()) {
      UUID playerId = online.getUniqueId();
      if (pendingVoteLockedPlayers.contains(playerId)) {
        activeVoteLockedPlayers.add(playerId);
      }
    }
  }
}
