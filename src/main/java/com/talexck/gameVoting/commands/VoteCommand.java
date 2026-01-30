package com.talexck.gameVoting.commands;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.api.cloudnet.CloudNetAPI;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.ui.VotingUI;
import com.talexck.gameVoting.utils.message.MessageUtil;
import com.talexck.gameVoting.voting.VotingSession;
import eu.cloudnetservice.driver.service.ServiceCreateResult;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

public class VoteCommand implements CommandExecutor {
    private final GameVoting plugin;
    private GamesConfigManager gamesManager;
    private static final int DEFAULT_VOTING_DURATION = 3; // 3 minutes
    
    // Store players who voted, for teleportation after session is cleared
    private Set<UUID> playersToTeleport = new HashSet<>();

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
                    return handleJoin(player);
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
     * @param duration Voting duration in minutes
     */
    public void actuallyStartVoting(int duration) {
        VotingSession session = VotingSession.getInstance();
        
        // End pre-voting ready phase
        session.endPreVotingReady();
        
        // Start the voting session with timer and callback
        session.startVoting(duration, plugin, () -> {
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
        placeholders.put("time", String.valueOf(duration));
        
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
        // Check permission
        if (!player.hasPermission("gamevoting.vote.admin")) {
            MessageUtil.sendTranslated(player, "voting.no_permission_start");
            return true;
        }

        VotingSession session = VotingSession.getInstance();

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
        int duration = DEFAULT_VOTING_DURATION;
        if (args.length > 1) {
            try {
                duration = Integer.parseInt(args[1]);
                if (duration <= 0) {
                    MessageUtil.sendTranslated(player, "command.duration_must_positive");
                    return true;
                }
            } catch (NumberFormatException e) {
                MessageUtil.sendTranslated(player, "command.invalid_duration");
                return true;
            }
        }

        // Store duration for later use
        final int finalDuration = duration;
        
        // Always start voting directly when /vote start is executed
        session.setVoteStarter(player.getUniqueId());
        
        // Start the voting session with timer and callback
        session.startVoting(finalDuration, plugin, () -> {
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
        placeholders.put("time", String.valueOf(finalDuration));
        
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

        // Give appropriate items based on player count (replace compass with redstone block/emerald)
        int onlineCount = Bukkit.getOnlinePlayers().size();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (onlineCount >= 6) {
                // Enough players - give start voting item (emerald)
                com.talexck.gameVoting.utils.item.VoteItem.giveStartVotingItem(online);
            } else {
                // Not enough players - give insufficient players item (redstone block)
                com.talexck.gameVoting.utils.item.VoteItem.giveInsufficientPlayersItem(online);
            }
        }

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
        String winnerId = session.getWinner();
        if (winnerId == null) {
            MessageUtil.broadcastTranslated("voting.no_votes_cast");
            session.clear();
            // Remove vote items from all players
            for (Player online : Bukkit.getOnlinePlayers()) {
                com.talexck.gameVoting.utils.item.VoteItem.removeVoteItem(online);
            }
            return;
        }

        // Get winner GameConfig
        GameConfig winner = gamesManager.getGame(winnerId);
        if (winner == null) {
            MessageUtil.broadcastTranslated("voting.winner_not_found");
            session.clear();
            for (Player online : Bukkit.getOnlinePlayers()) {
                com.talexck.gameVoting.utils.item.VoteItem.removeVoteItem(online);
            }
            return;
        }

        // Start ready phase instead of immediately starting game
        session.startReadyPhase();
        
        // Update holograms to show vote results
        updateHologramDisplays();

        // Give ready items to all players
        for (Player online : Bukkit.getOnlinePlayers()) {
            com.talexck.gameVoting.utils.item.VoteItem.giveReadyItem(online);
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
     * Start a CloudNet service for the given game and begin 60-second countdown.
     *
     * @param game The game to start
     * @param initiator The player who initiated (null if automatic)
     */
    private void startGame(GameConfig game, Player initiator) {
        String taskName = game.getCloudnetTask();

        if (taskName == null || taskName.isEmpty()) {
            plugin.getLogger().warning("Game " + game.getId() + " has no CloudNet task configured!");
            if (initiator != null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("game", game.getName());
                MessageUtil.sendTranslated(initiator, "game.no_cloudnet_task", placeholders);
            }
            return;
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

            // Announce game starting
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("game", game.getName());
            
            MessageUtil.broadcast("");
            MessageUtil.broadcastTranslated("game.creating_service", placeholders);
            MessageUtil.broadcastTranslated("game.teleporting_in");
            MessageUtil.broadcast("");

            plugin.getLogger().info("Successfully started CloudNet service for " + game.getName() + ": " + serviceName);
            
            // Start 60-second countdown before teleporting
            startTeleportCountdown(serviceName, game, 60);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start CloudNet service for " + game.getName() + ": " + e.getMessage());
            e.printStackTrace();
            if (initiator != null) {
                MessageUtil.sendTranslated(initiator, "game.service_creation_failed");
            }
        }
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
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Only teleport players who voted (using saved list from before session was cleared)
            if (!playersToTeleport.contains(player.getUniqueId())) {
                plugin.getLogger().info("Skipping teleport for " + player.getName() + " - did not vote");
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

        // Save list of players who voted for teleportation (before clearing session)
        playersToTeleport.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (session.hasVoted(online)) {
                playersToTeleport.add(online.getUniqueId());
            }
        }

        // Start the game
        startGame(winner, initiator);

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

        // Stop countdown if running
        if (session.isCountdownActive()) {
            session.stopCountdown();
        }

        // Clear session completely
        session.clear();

        // Clear BossBar display for all players
        com.talexck.gameVoting.utils.display.BossBarManager bossBarManager = 
            com.talexck.gameVoting.utils.display.BossBarManager.getInstance();
        for (Player online : Bukkit.getOnlinePlayers()) {
            bossBarManager.removeBar(online);
        }

        // Give appropriate items based on player count
        int onlineCount = Bukkit.getOnlinePlayers().size();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (onlineCount >= 6) {
                // Enough players - give start voting item (emerald)
                com.talexck.gameVoting.utils.item.VoteItem.giveStartVotingItem(online);
            } else {
                // Not enough players - give insufficient players item (redstone block)
                com.talexck.gameVoting.utils.item.VoteItem.giveInsufficientPlayersItem(online);
            }
        }

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
     * Handle /vote join - Join current running game.
     */
    private boolean handleJoin(Player player) {
        VotingSession session = VotingSession.getInstance();
        
        // Check if there's a game running
        if (!session.hasCurrentGame()) {
            MessageUtil.sendTranslated(player, "join.no_game");
            return true;
        }
        
        String serviceName = session.getCurrentGameService();
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
}
