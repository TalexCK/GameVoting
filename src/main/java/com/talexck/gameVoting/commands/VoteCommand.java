package com.talexck.gameVoting.commands;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.api.cloudnet.CloudNetAPI;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.ui.VotingUI;
import com.talexck.gameVoting.utils.message.MessageUtil;
import com.talexck.gameVoting.utils.version.ReadyVersionValidator;
import com.talexck.gameVoting.voting.VotingSession;
import eu.cloudnetservice.modules.bridge.BridgeServiceHelper;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import eu.cloudnetservice.driver.service.ServiceCreateResult;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class VoteCommand implements CommandExecutor {
    private final GameVoting plugin;
    private GamesConfigManager gamesManager;
    private static final int DEFAULT_VOTING_DURATION_SECONDS = 60; // 1 minute
    private static final int BRIDGE_READY_CHECK_INTERVAL_TICKS = 20; // 1 second
    private static final int TELEPORT_DELAY_AFTER_READY_SECONDS = 60;
    
    // Store a snapshot of lobby players for delayed teleport
    private Set<UUID> playersToTeleport = new HashSet<>();
    // Pre-started game service after voting ends
    private String preStartedServiceName;
    private String preStartedGameId;

    public VoteCommand(GameVoting plugin) {
        this.plugin = plugin;
    }

    /**
     * Set the games configuration manager.
     * Called by the plugin during initialization.
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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
            sender.sendMessage(com.talexck.gameVoting.utils.language.LanguageManager.getInstance().getMessage("command.only_players"));
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
     * Actually start the voting session (called from pre-voting ready phase).
     * This is a public method so it can be called from VoteItemListener.
     *
     * @param durationSeconds Voting duration in seconds
     */
    public void actuallyStartVoting(int durationSeconds) {
        VotingSession session = VotingSession.getInstance();
        
        // End pre-voting ready phase
        session.endPreVotingReady();
        
        // Start the voting session with timer and callback
        session.startVoting(durationSeconds, plugin, () -> {
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
        
        // Always start voting directly when /vote start is executed
        session.setVoteStarter(player.getUniqueId());
        
        // Start the voting session with timer and callback
        session.startVoting(finalDurationSeconds, plugin, () -> {
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

        // Snapshot all current lobby players before delayed teleport countdown starts.
        snapshotPlayersToTeleport();
        clearPreStartedService();

        // Start the game
        startGame(game, player);

        return true;
    }

    /**
     * Handle voting end (called when timer expires or manually stopped).
     */
    private void handleVotingEnd() {
        VotingSession session = VotingSession.getInstance();

        // Stop voting and get results
        Map<String, Integer> results = session.stopVoting();
        broadcastResults(results);

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
        placeholders.put("game", winner.getName());
        
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
            session.startCountdown(GameVoting.getInstance(), () -> {
                Bukkit.getScheduler().runTask(GameVoting.getInstance(), () -> {
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
                    String medal = position == 1 ? "&6🥇" : position == 2 ? "&7🥈" : position == 3 ? "&c🥉" : "&e" + position + ".";
                    MessageUtil.broadcast(medal + " " + game.getName() + " &7- &e" + entry.getValue() + " vote(s)");
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
     * Start a CloudNet service for the given game and schedule teleport.
     *
     * @param game The game to start
     * @param initiator The player who initiated (null if automatic)
     */
    private void startGame(GameConfig game, Player initiator) {
        String serviceName = createAndStartService(game, initiator);
        if (serviceName == null) {
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("game", game.getName());
        MessageUtil.broadcast("");
        MessageUtil.broadcastTranslated("game.creating_service", placeholders);

        plugin.getLogger().info("Successfully started CloudNet service for " + game.getName() + ": " + serviceName);

        // Wait for service readiness or fallback delay (config-driven).
        scheduleTeleport(serviceName, game);
    }

    /**
     * Create and start a CloudNet service from game task.
     *
     * @param game Target game
     * @param initiator Command initiator for error feedback (nullable)
     * @return Created service name, or null on failure
     */
    private String createAndStartService(GameConfig game, Player initiator) {
        String taskName = game.getCloudnetTask();

        if (taskName == null || taskName.isEmpty()) {
            plugin.getLogger().warning("Game " + game.getId() + " has no CloudNet task configured!");
            if (initiator != null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("game", game.getName());
                MessageUtil.sendTranslated(initiator, "game.no_cloudnet_task", placeholders);
            }
            return null;
        }

        try {
            CloudNetAPI api = CloudNetAPI.getInstance();
            
            plugin.getLogger().info("Creating CloudNet service from task: " + taskName);
            
            // Create service
            ServiceCreateResult result = api.createService(taskName);
            
            if (result == null || result.serviceInfo() == null) {
                throw new RuntimeException("Service creation returned null result");
            }
            
            var serviceInfo = result.serviceInfo();
            String serviceName = serviceInfo.name();
            plugin.getLogger().info("Created service: " + serviceName + " (State: " + serviceInfo.lifeCycle() + ")");
            
            // Ensure service is started
            if (serviceInfo.lifeCycle().name().equals("PREPARED") || serviceInfo.lifeCycle().name().equals("STOPPED")) {
                plugin.getLogger().info("Starting service: " + serviceName);
                api.startService(serviceInfo.serviceId().uniqueId());
            }
            return serviceName;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start CloudNet service for " + game.getName() + ": " + e.getMessage());
            e.printStackTrace();
            if (initiator != null) {
                MessageUtil.sendTranslated(initiator, "game.service_creation_failed");
            }
            return null;
        }
    }

    /**
     * Pre-start winner service right after voting ends.
     *
     * @param winner Winner game
     */
    private void preStartWinningService(GameConfig winner) {
        clearPreStartedService();

        String serviceName = createAndStartService(winner, null);
        if (serviceName == null) {
            return;
        }

        preStartedGameId = winner.getId();
        preStartedServiceName = serviceName;
        VotingSession.getInstance().setCurrentGameService(serviceName);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("game", winner.getName());
        MessageUtil.broadcast("");
        MessageUtil.broadcastTranslated("game.creating_service", placeholders);
        MessageUtil.broadcast("");

        plugin.getLogger().info("Pre-started winner service " + serviceName + " for game " + winner.getId());
    }

    /**
     * Clear cached pre-started service info.
     */
    private void clearPreStartedService() {
        preStartedGameId = null;
        preStartedServiceName = null;
    }
    
    /**
     * Schedule teleport by bridge-ready mode (default) or fixed delay fallback.
     *
     * @param serviceName Service name
     * @param game Game config
     */
    private void scheduleTeleport(String serviceName, GameConfig game) {
        boolean waitBridgeReady = game.isWaitForBridgeReady();
        int expectedStartupSeconds = game.getExpectedStartupSeconds();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("game", game.getName());

        MessageUtil.broadcast("");
        if (waitBridgeReady) {
            placeholders.put("time", String.valueOf(TELEPORT_DELAY_AFTER_READY_SECONDS));
            MessageUtil.broadcastTranslated("game.teleporting_when_ready", placeholders);
            waitForServiceReadyAndTeleport(serviceName, game, BRIDGE_READY_CHECK_INTERVAL_TICKS, TELEPORT_DELAY_AFTER_READY_SECONDS);
        } else {
            placeholders.put("time", String.valueOf(expectedStartupSeconds));
            MessageUtil.broadcastTranslated("game.teleporting_in", placeholders);
            startTeleportCountdown(serviceName, game, expectedStartupSeconds);
        }
        MessageUtil.broadcast("");
    }

    /**
     * Poll service state via CloudNet Bridge until fully online, then teleport.
     *
     * @param serviceName Service name
     * @param game Game config
     * @param checkIntervalTicks Poll interval in ticks
     * @param delayAfterReadySeconds Delay before teleport once service is ready
     */
    private void waitForServiceReadyAndTeleport(String serviceName, GameConfig game, int checkIntervalTicks, int delayAfterReadySeconds) {
        final int[] taskIdHolder = new int[1];

        taskIdHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                var serviceOpt = CloudNetAPI.getInstance().getServiceByName(serviceName);
                if (serviceOpt.isEmpty()) {
                    Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                    plugin.getLogger().warning("Cancelled readiness wait for service " + serviceName
                        + " because it is missing (likely stopped manually).");
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("game", game.getName());
                    placeholders.put("service", serviceName);
                    MessageUtil.broadcastTranslated("teleport.service_missing_abort", placeholders);
                    return;
                }

                ServiceInfoSnapshot snapshot = serviceOpt.get();
                if (!isServiceReadyForTeleport(snapshot)) {
                    return;
                }

                Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                plugin.getLogger().info("Service " + serviceName + " is ready, teleporting in "
                    + delayAfterReadySeconds + " seconds (no countdown).");
                scheduleDelayedTeleportWithoutCountdown(serviceName, game, delayAfterReadySeconds);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed while polling service readiness for " + serviceName + ": " + ex.getMessage());
            }
        }, 0L, checkIntervalTicks).getTaskId();
    }

    /**
     * Schedule teleport after a fixed delay without countdown broadcast.
     *
     * @param serviceName Service name
     * @param game Game config
     * @param delaySeconds Delay seconds
     */
    private void scheduleDelayedTeleportWithoutCountdown(String serviceName, GameConfig game, int delaySeconds) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("game", game.getName());
            String message = com.talexck.gameVoting.utils.language.LanguageManager.getInstance()
                .getMessage("teleport.teleporting_now", placeholders);
            for (Player player : Bukkit.getOnlinePlayers()) {
                com.talexck.gameVoting.utils.display.ActionBarUtil.sendActionBar(player, message);
            }

            teleportPlayersToService(serviceName, game);
        }, delaySeconds * 20L);
    }

    /**
     * Start a countdown before teleporting players.
     * Shows countdown in ActionBar for all players.
     *
     * @param serviceName The name of the service
     * @param game The game configuration
     * @param seconds Total countdown seconds
     */
    private void startTeleportCountdown(String serviceName, GameConfig game, int seconds) {
        final int[] remaining = {seconds};
        final int[] taskIdHolder = new int[1];
        
        // Schedule repeating task for countdown
        taskIdHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                int timeLeft = remaining[0];
                
                // Countdown finished
                if (timeLeft <= 0) {
                    // Cancel this task
                    Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                    
                    // Show final message
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("game", game.getName());
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        String message = com.talexck.gameVoting.utils.language.LanguageManager.getInstance()
                            .getMessage("teleport.teleporting_now", placeholders);
                        com.talexck.gameVoting.utils.display.ActionBarUtil.sendActionBar(player, message);
                    }
                    
                    // Teleport players after a brief delay
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        teleportPlayersToService(serviceName, game);
                    }, 10L);
                    return;
                }
                
                // Show countdown in ActionBar for all players
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("game", game.getName());
                placeholders.put("time", String.valueOf(timeLeft));
                
                String messageKey;
                if (timeLeft > 10) {
                    messageKey = "teleport.countdown_yellow";
                } else if (timeLeft > 5) {
                    messageKey = "teleport.countdown_gold";
                } else {
                    messageKey = "teleport.countdown_red";
                }
                
                String message = com.talexck.gameVoting.utils.language.LanguageManager.getInstance()
                    .getMessage(messageKey, placeholders);
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    com.talexck.gameVoting.utils.display.ActionBarUtil.sendActionBar(player, message);
                }
                
                remaining[0]--;
            }
        }, 0L, 20L).getTaskId(); // Run every second
    }
    
    /**
     * Teleport all online players to the specified service using CloudNet Bridge API.
     * Executes "send <player> <server>" command on the proxy service.
     * 
     * @param serviceName The name of the service
     * @param game The game configuration
     */
    private void teleportPlayersToService(String serviceName, GameConfig game) {
        // Get proxy service name from config
        String proxyService = plugin.getConfig().getString("proxy-service-name", "Proxy-1");
        
        CloudNetAPI api = CloudNetAPI.getInstance();
        int successCount = 0;
        int failCount = 0;

        if (playersToTeleport.isEmpty()) {
            plugin.getLogger().warning("Teleport snapshot is empty, skipping teleport to avoid sending unready players.");
            MessageUtil.broadcastTranslated("teleport.no_targets");
            return;
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Only teleport players captured in the pre-start lobby snapshot.
            if (!playersToTeleport.contains(player.getUniqueId())) {
                plugin.getLogger().info("Skipping teleport for " + player.getName() + " - not in lobby snapshot");
                continue;
            }
            
            try {
                // Execute "send <player> <server>" command on proxy service
                String command = "send " + player.getName() + " " + serviceName;
                api.executeServiceCommand(proxyService, command);
                
                successCount++;
                plugin.getLogger().info("Sent teleport command for " + player.getName() + " to " + serviceName + " via proxy " + proxyService);
                
            } catch (Exception e) {
                failCount++;
                plugin.getLogger().severe("Exception sending teleport command for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        plugin.getLogger().info("Teleport commands sent: " + successCount + " succeeded, " + failCount + " failed");
        
        if (failCount > 0) {
            MessageUtil.broadcastTranslated("teleport.commands_failed");
        }
        
        // Store service name in voting session for /vote join
        VotingSession.getInstance().setCurrentGameService(serviceName);
        
        // Schedule hologram update to show historical wins after a short delay
        // This allows players time to be teleported before hologram changes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateHologramDisplays();
        }, 20L); // 1 second delay
    }

    /**
     * Capture current online players as teleport targets.
     * This represents lobby members at game start time.
     */
    private void snapshotPlayersToTeleport() {
        playersToTeleport.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            playersToTeleport.add(online.getUniqueId());
        }
        plugin.getLogger().info("Captured " + playersToTeleport.size() + " lobby players for teleport.");
    }

    /**
     * Capture a subset of online players as teleport targets.
     *
     * @param targetPlayers Eligible player UUID set
     */
    private void snapshotPlayersToTeleport(Set<UUID> targetPlayers) {
        playersToTeleport.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (targetPlayers.contains(online.getUniqueId())) {
                playersToTeleport.add(online.getUniqueId());
            }
        }
        plugin.getLogger().info("Captured " + playersToTeleport.size() + " ready players for teleport.");
    }

    /**
     * Handle /vote ready - Mark player as ready.
     */
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
            placeholders.put("current", versionResult.playerVersion() == null ? "Unknown" : versionResult.playerVersion());
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
     * Handle /vote gamestart - Force start the game (only for vote starter).
     * Can also be called by console (when player is null).
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
     * Execute the actual game start logic.
     * Can be called by force start or countdown completion.
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
        placeholders.put("game", winner.getName());
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
        if (initiator != null && session.canForceStart(initiator.getUniqueId()) && !session.allPlayersReady()) {
            snapshotPlayersToTeleport(session.getReadyPlayers());
            Map<String, String> placeholdersReadyOnly = new HashMap<>();
            placeholdersReadyOnly.put("count", String.valueOf(playersToTeleport.size()));
            placeholdersReadyOnly.put("total", String.valueOf(Bukkit.getOnlinePlayers().size()));
            MessageUtil.broadcastTranslated("ready.force_start_ready_only", placeholdersReadyOnly);
        } else {
            // All-ready/auto-start path keeps existing behavior: teleport all lobby players.
            snapshotPlayersToTeleport();
        }

        // Start the game (reuse pre-started service when available)
        if (preStartedServiceName != null && winner.getId().equalsIgnoreCase(preStartedGameId)) {
            scheduleTeleport(preStartedServiceName, winner);
            plugin.getLogger().info("Using pre-started service for winner " + winner.getId() + ": " + preStartedServiceName);
            clearPreStartedService();
        } else {
            startGame(winner, initiator);
            clearPreStartedService();
        }

        // Clear session
        session.clear();

        return true;
    }

    /**
     * Handle /vote holograms subcommands.
     */
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

    /**
     * Handle /vote holograms create.
     */
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

    /**
     * Handle /vote holograms remove <id>.
     */
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

    /**
     * Handle /vote holograms list.
     */
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

    /**
     * Handle /vote session subcommands.
     */
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

    /**
     * Handle /vote session list [page].
     */
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
            placeholders.put("game", record.getWinningGameName());
            placeholders.put("votes", String.valueOf(record.getTotalVotes()));
            MessageUtil.sendTranslated(player, "session.list_entry", placeholders);
        }
        
        return true;
    }

    /**
     * Handle /vote session stop.
     */
    private boolean handleSessionStop(Player player) {
        // Check permission
        if (!player.hasPermission("gamevoting.vote.admin")) {
            MessageUtil.sendTranslated(player, "command.no_permission");
            return true;
        }

        VotingSession session = VotingSession.getInstance();

        // Check if session is active (voting or ready phase)
        if (!session.isActive() && !session.isReadyPhase() && !session.isPreVotingReady()) {
            MessageUtil.sendTranslated(player, "command.no_active_session");
            return true;
        }

        // Clear session completely (this also stops countdown and cancels tasks)
        session.clear();
        clearPreStartedService();

        // Clear BossBar display for all players (do this AFTER clearing session to ensure tasks are stopped)
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

    /**
     * Handle /vote reload - Reload plugin configuration.
     */
    private boolean handleReload(Player player) {
        // Check permission
        if (!player.hasPermission("gamevoting.vote.admin")) {
            MessageUtil.sendTranslated(player, "command.reload_no_permission");
            return true;
        }

        MessageUtil.sendTranslated(player, "command.reload_start");

        try {
            // Reload main config
            plugin.reloadConfig();
            
            // Reload games configuration
            if (gamesManager != null) {
                gamesManager.reload();
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("count", String.valueOf(gamesManager.getGameCount()));
                MessageUtil.sendTranslated(player, "command.reload_games", placeholders);
            }
            
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
            
            // Update holograms to reflect any changes
            updateHologramDisplays();
            
            MessageUtil.sendTranslated(player, "command.reload_success");
            plugin.getLogger().info("Plugin reloaded by " + player.getName());
            
        } catch (Exception e) {
            MessageUtil.sendTranslated(player, "command.reload_failed");
            plugin.getLogger().severe("Error reloading plugin: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
    
    /**
     * Handle /vote join [game] - Join current running game or a specific game.
     */
    private boolean handleJoin(Player player, String[] args) {
        VotingSession session = VotingSession.getInstance();
        String serviceName;

        if (args.length > 1) {
            String gameId = args[1];
            GameConfig targetGame = gamesManager.getGame(gameId);
            if (targetGame == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("game", gameId);
                MessageUtil.sendTranslated(player, "command.game_not_found", placeholders);
                return true;
            }

            String taskName = targetGame.getCloudnetTask();
            if (taskName == null || taskName.isBlank()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("game", targetGame.getName());
                MessageUtil.sendTranslated(player, "join.game_unavailable", placeholders);
                return true;
            }

            List<ServiceInfoSnapshot> runningServices = CloudNetAPI.getInstance().getServicesByTask(taskName).stream()
                .filter(this::isServiceOnline)
                .sorted(Comparator.comparing(ServiceInfoSnapshot::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

            if (runningServices.isEmpty()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("game", targetGame.getName());
                MessageUtil.sendTranslated(player, "join.game_unavailable", placeholders);
                return true;
            }

            serviceName = runningServices.get(0).name();
        } else {
            // Keep legacy behavior when no game argument is provided.
            if (!session.hasCurrentGame()) {
                MessageUtil.sendTranslated(player, "join.no_game");
                return true;
            }
            serviceName = session.getCurrentGameService();
        }

        String proxyService = plugin.getConfig().getString("proxy-service-name", "Proxy-1");

        try {
            // Execute "send <player> <server>" command on proxy service using Bridge API
            CloudNetAPI api = CloudNetAPI.getInstance();
            String command = "send " + player.getName() + " " + serviceName;
            api.executeServiceCommand(proxyService, command);

            MessageUtil.sendTranslated(player, "join.teleporting");
            plugin.getLogger().info("Sent teleport command for " + player.getName() + " to " + serviceName + " via /vote join");

        } catch (Exception e) {
            MessageUtil.sendTranslated(player, "join.failed");
            plugin.getLogger().severe("Exception sending teleport command for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Handle /vote gamelist - List online minigame services grouped by game ID.
     */
    private boolean handleGameList(Player player) {
        if (!player.hasPermission("gamevoting.vote.admin")) {
            MessageUtil.sendTranslated(player, "command.no_permission");
            return true;
        }

        try {
            List<GameOnlineServices> onlineGames = getOnlineGameServices();
            if (onlineGames.isEmpty()) {
                MessageUtil.sendTranslated(player, "command.gamelist_empty");
                return true;
            }

            MessageUtil.sendTranslated(player, "command.gamelist_header");

            for (GameOnlineServices group : onlineGames) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("id", group.game().getId());
                placeholders.put("name", group.game().getName());
                placeholders.put("count", String.valueOf(group.services().size()));
                placeholders.put("services", group.services().stream()
                    .map(ServiceInfoSnapshot::name)
                    .collect(Collectors.joining(", ")));
                MessageUtil.sendTranslated(player, "command.gamelist_entry", placeholders);
            }
        } catch (Exception e) {
            MessageUtil.sendTranslated(player, "command.gamelist_failed");
            plugin.getLogger().severe("Failed to fetch online minigame servers: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Handle /vote stopgame <game-id> - Stop all online minigame services for a game.
     */
    private boolean handleStopGame(Player player, String[] args) {
        if (!player.hasPermission("gamevoting.vote.admin")) {
            MessageUtil.sendTranslated(player, "command.no_permission");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendTranslated(player, "command.stopgame_usage");
            return true;
        }

        String targetGameId = args[1];

        try {
            List<GameOnlineServices> onlineGames = getOnlineGameServices();
            GameOnlineServices target = onlineGames.stream()
                .filter(group -> group.game().getId().equalsIgnoreCase(targetGameId))
                .findFirst()
                .orElse(null);

            if (target == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("game", targetGameId);
                MessageUtil.sendTranslated(player, "command.stopgame_not_found", placeholders);
                return true;
            }

            CloudNetAPI api = CloudNetAPI.getInstance();
            int success = 0;
            int failed = 0;

            for (ServiceInfoSnapshot service : target.services()) {
                try {
                    api.stopService(service.serviceId().uniqueId());
                    success++;
                    plugin.getLogger().info("Stopped minigame service " + service.name() + " for game " + target.game().getId());
                } catch (Exception ex) {
                    failed++;
                    plugin.getLogger().severe("Failed to stop minigame service " + service.name() + ": " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("game", target.game().getId());
            placeholders.put("success", String.valueOf(success));
            placeholders.put("failed", String.valueOf(failed));

            if (failed == 0) {
                MessageUtil.sendTranslated(player, "command.stopgame_success", placeholders);
            } else {
                MessageUtil.sendTranslated(player, "command.stopgame_partial", placeholders);
            }
        } catch (Exception e) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("game", targetGameId);
            MessageUtil.sendTranslated(player, "command.stopgame_failed", placeholders);
            plugin.getLogger().severe("Failed to stop minigame services for " + targetGameId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private List<GameOnlineServices> getOnlineGameServices() {
        List<GameOnlineServices> result = new ArrayList<>();
        CloudNetAPI api = CloudNetAPI.getInstance();

        for (GameConfig game : gamesManager.getGames()) {
            String taskName = game.getCloudnetTask();
            if (taskName == null || taskName.isBlank()) {
                continue;
            }

            List<ServiceInfoSnapshot> services = api.getServicesByTask(taskName).stream()
                .filter(this::isServiceOnline)
                .sorted(Comparator.comparing(ServiceInfoSnapshot::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

            if (!services.isEmpty()) {
                result.add(new GameOnlineServices(game, services));
            }
        }

        return result;
    }

    private boolean isServiceOnline(ServiceInfoSnapshot service) {
        return "RUNNING".equalsIgnoreCase(service.lifeCycle().name());
    }

    /**
     * Service is considered ready when lifecycle is RUNNING, connected, and bridge no longer marks it as starting.
     */
    private boolean isServiceReadyForTeleport(ServiceInfoSnapshot service) {
        if (!isServiceOnline(service) || !service.connected()) {
            return false;
        }
        try {
            return !BridgeServiceHelper.startingService(service);
        } catch (Exception ex) {
            plugin.getLogger().warning("Bridge readiness check failed for " + service.name() + ", fallback to RUNNING+connected.");
            return true;
        }
    }

    /**
     * Parse /vote start duration argument.
     * Supports minute values like: 1, 0.5, 0.1min.
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
            ThreadLocalRandom.current()
        );
    }

    static String selectRandomWinner(Map<String, Integer> results, Predicate<String> isAvailable, Random random) {
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
            var voteHistory = new com.talexck.gameVoting.voting.VoteHistory.Builder()
                .sessionId(UUID.randomUUID())
                .timestamp(java.time.Instant.now())
                .winningGameId(winner.getId())
                .winningGameName(winner.getName())
                .totalVotes(session.getTotalVoteCount())
                .playerCount(Bukkit.getOnlinePlayers().size())
                .voteDetails(new HashMap<>(session.getVoteCounts()))
                .build();
            
            // Save to database asynchronously
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean success = repository.saveSession(voteHistory);
                if (success) {
                    plugin.getLogger().info("Saved vote result to database: " + winner.getName() + " won with " + session.getTotalVoteCount() + " votes");
                } else {
                    plugin.getLogger().warning("Failed to save vote result to database");
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Exception saving vote result to database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Update all hologram displays based on current voting state.
     */
    private void updateHologramDisplays() {
        var hologramConfig = plugin.getHologramConfigManager();
        var displayManager = plugin.getHologramDisplayManager();
        var locations = hologramConfig.getAllLocations();
        
        if (locations.isEmpty()) {
            return;
        }
        
        VotingSession session = VotingSession.getInstance();
        var state = com.talexck.gameVoting.utils.hologram.HologramDisplayManager.DisplayState.NOT_VOTING;
        
        if (session.isPreVotingReady()) {
            state = com.talexck.gameVoting.utils.hologram.HologramDisplayManager.DisplayState.PRE_VOTING_READY;
        } else if (session.isActive()) {
            state = com.talexck.gameVoting.utils.hologram.HologramDisplayManager.DisplayState.VOTING_ACTIVE;
        } else if (session.isReadyPhase()) {
            state = com.talexck.gameVoting.utils.hologram.HologramDisplayManager.DisplayState.VOTE_ENDED;
        }
        
        displayManager.updateAllHolograms(state, locations);
    }

    private record GameOnlineServices(GameConfig game, List<ServiceInfoSnapshot> services) {}
}
