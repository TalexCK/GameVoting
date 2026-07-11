package com.talexck.gameVoting;

import com.schedulerbridge.common.ServerInstance;
import com.schedulerbridge.common.ServerScheduler;
import com.talexck.gameVoting.commands.VoteCommand;
import com.talexck.gameVoting.commands.SoloCommand;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.listeners.BossBarListener;
import com.talexck.gameVoting.party.commands.PartyCommand;
import com.talexck.gameVoting.party.listeners.PartyQuitListener;
import com.talexck.gameVoting.utils.display.BossBarManager;
import com.talexck.gameVoting.utils.gui.ChestUIListener;
import com.talexck.gameVoting.utils.hologram.HologramManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class GameVoting extends JavaPlugin {

  private static GameVoting instance;
  private GamesConfigManager gamesManager;
  private com.talexck.gameVoting.config.HologramConfigManager hologramConfigManager;
  private com.talexck.gameVoting.utils.hologram.HologramDisplayManager hologramDisplayManager;
  private com.talexck.gameVoting.proxy.ProxyVersionBridge proxyVersionBridge;
  private ServerScheduler serverScheduler;
  private volatile List<ServerInstance> schedulerInstances = List.of();
  private VoteCommand voteCommand;
  private SoloCommand soloCommand;

  /**
   * Get the plugin instance.
   *
   * @return The GameVoting plugin instance
   */
  public static GameVoting getInstance() {
    return instance;
  }

  /**
   * Get the games configuration manager.
   *
   * @return The games configuration manager
   */
  public GamesConfigManager getGamesManager() {
    return gamesManager;
  }

  /**
   * Get the hologram configuration manager.
   *
   * @return The hologram configuration manager
   */
  public com.talexck.gameVoting.config.HologramConfigManager getHologramConfigManager() {
    return hologramConfigManager;
  }

  /**
   * Get the hologram display manager.
   *
   * @return The hologram display manager
   */
  public com.talexck.gameVoting.utils.hologram.HologramDisplayManager getHologramDisplayManager() {
    return hologramDisplayManager;
  }

  /**
   * Get proxy version bridge manager.
   *
   * @return proxy version bridge
   */
  public com.talexck.gameVoting.proxy.ProxyVersionBridge getProxyVersionBridge() {
    return proxyVersionBridge;
  }

  public ServerScheduler getServerScheduler() {
    return serverScheduler;
  }

  public List<ServerInstance> getSchedulerInstances() {
    return schedulerInstances;
  }

  @Override
  public void onEnable() {
    instance = this;
    // Save if it doesn't exist
    saveDefaultConfig();

    serverScheduler = getServer().getServicesManager().load(ServerScheduler.class);
    if (serverScheduler == null) {
      getLogger().severe("SchedulerBridge did not register ServerScheduler");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    serverScheduler
        .list()
        .thenAccept(
            instances -> {
              schedulerInstances = List.copyOf(instances);
              getLogger()
                  .info(
                      "Connected to SchedulerBridge with "
                          + instances.size()
                          + " registered instances");
            })
        .exceptionally(
            error -> {
              getLogger().severe("Failed to query SchedulerBridge: " + error.getMessage());
              return null;
            });
    Bukkit.getScheduler()
        .runTaskTimerAsynchronously(this, this::refreshSchedulerInstances, 20L, 20L);

    // Initialize LanguageManager
    com.talexck.gameVoting.utils.language.LanguageManager.initialize(this);
    getLogger().info("LanguageManager initialized");

    // Initialize DatabaseManager
    com.talexck.gameVoting.utils.database.DatabaseManager.initialize(this);
    getLogger().info("DatabaseManager initialized");

    // Register ChestUI listener
    getServer().getPluginManager().registerEvents(new ChestUIListener(), this);
    getLogger().info("ChestUI utility loaded successfully!");

    // Initialize proxy version bridge (paper <-> velocity plugin message)
    proxyVersionBridge = new com.talexck.gameVoting.proxy.ProxyVersionBridge(this);
    proxyVersionBridge.start();
    getLogger().info("ProxyVersionBridge initialized");

    // Register BossBar listener for cleanup
    getServer().getPluginManager().registerEvents(new BossBarListener(), this);
    getLogger().info("BossBarManager initialized");

    // Initialize VoteItem system
    com.talexck.gameVoting.utils.item.VoteItem.initialize(this);
    getLogger().info("VoteItem system initialized");

    // Register VoteItem listener
    getServer()
        .getPluginManager()
        .registerEvents(new com.talexck.gameVoting.listeners.VoteItemListener(), this);
    getLogger().info("VoteItemListener registered");

    // Register PlayerJoin listener
    getServer()
        .getPluginManager()
        .registerEvents(new com.talexck.gameVoting.listeners.PlayerJoinListener(), this);
    getLogger().info("PlayerJoinListener registered");

    // Register VotingPlayerQuit listener
    getServer()
        .getPluginManager()
        .registerEvents(new com.talexck.gameVoting.listeners.VotingPlayerQuitListener(), this);
    getLogger().info("VotingPlayerQuitListener registered");

    // Initialize HologramManager (only if DecentHolograms is present)
    if (getServer().getPluginManager().getPlugin("DecentHolograms") != null) {
      HologramManager.initialize(this);
    } else {
      getLogger().info("DecentHolograms not found - hologram features disabled");
    }

    // Initialize games configuration manager
    gamesManager = new GamesConfigManager(this);
    getLogger().info("Games configuration manager initialized");

    // Initialize hologram configuration manager
    hologramConfigManager = new com.talexck.gameVoting.config.HologramConfigManager(this);
    getLogger().info("Hologram configuration manager initialized");

    // Initialize hologram display manager
    hologramDisplayManager = new com.talexck.gameVoting.utils.hologram.HologramDisplayManager(this);
    getLogger().info("Hologram display manager initialized");

    // Register vote command using legacy Bukkit API
    PluginCommand voteCmd = this.getCommand("vote");
    if (voteCmd != null) {
      voteCommand = new VoteCommand(this);
      voteCommand.setGamesManager(gamesManager);
      voteCmd.setExecutor(voteCommand);
      voteCmd.setTabCompleter(
          new com.talexck.gameVoting.commands.VoteTabCompleter(this, gamesManager));
      getServer().getPluginManager().registerEvents(voteCommand, this);
      getLogger().info("Registered /vote command with tab completion");
    } else {
      getLogger().warning("Failed to register /vote command - check plugin.yml");
    }

    PluginCommand soloCmd = this.getCommand("solo");
    if (soloCmd != null) {
      soloCommand = new SoloCommand(this, gamesManager);
      soloCmd.setExecutor(soloCommand);
      soloCmd.setTabCompleter(soloCommand);
      getServer().getPluginManager().registerEvents(soloCommand, this);
      getLogger().info("Registered /solo command");
    } else {
      getLogger().warning("Failed to register /solo command - check plugin.yml");
    }

    // Register party command
    PluginCommand partyCmd = this.getCommand("party");
    if (partyCmd != null) {
      partyCmd.setExecutor(new PartyCommand(this));
      getLogger().info("Registered /party command");
    } else {
      getLogger().warning("Failed to register /party command - check plugin.yml");
    }

    // Register party listener
    getServer().getPluginManager().registerEvents(new PartyQuitListener(), this);
    getLogger().info("Party system initialized");

    // Give appropriate item to all online players on startup
    Bukkit.getScheduler()
        .runTaskLater(
            this,
            () -> {
              int onlineCount = Bukkit.getOnlinePlayers().size();
              int requiredPlayers =
                  com.talexck.gameVoting.voting.VotingSession.getInstance().getRequiredPlayers();
              if (onlineCount > 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                  if (onlineCount >= requiredPlayers) {
                    // Give green emerald for ready system
                    com.talexck.gameVoting.utils.item.VoteItem.giveStartVotingItem(player);
                  } else {
                    // Give redstone block for insufficient players
                    com.talexck.gameVoting.utils.item.VoteItem.giveInsufficientPlayersItem(player);
                  }
                  com.talexck.gameVoting.utils.item.VoteItem.giveSoloItem(player);
                }
                getLogger().info("Given startup items to " + onlineCount + " players");

                if (onlineCount >= requiredPlayers) {
                  Map<String, String> placeholders = new HashMap<>();
                  placeholders.put("required", String.valueOf(requiredPlayers));
                  com.talexck.gameVoting.utils.message.MessageUtil.broadcastTranslated(
                      "ready.reached_min_players_start", placeholders);
                }
              }

              // Initialize hologram displays to show NOT_VOTING state (top games)
              var locations = hologramConfigManager.getAllLocations();
              if (!locations.isEmpty()) {
                hologramDisplayManager.updateAllHolograms(
                    com.talexck
                        .gameVoting
                        .utils
                        .hologram
                        .HologramDisplayManager
                        .DisplayState
                        .NOT_VOTING,
                    locations);
                getLogger()
                    .info(
                        "Initialized "
                            + locations.size()
                            + " hologram(s) with popular games display");
              }
            },
            20L); // Delay 1 second to ensure all players are loaded

    getLogger().info("GameVoting plugin enabled!");
  }

  private void refreshSchedulerInstances() {
    serverScheduler
        .list()
        .thenAccept(instances -> schedulerInstances = List.copyOf(instances))
        .exceptionally(error -> null);
  }

  @Override
  public void onDisable() {
    if (voteCommand != null) {
      voteCommand.shutdown();
    }
    if (soloCommand != null) {
      soloCommand.shutdown();
    }

    // Cleanup boss bars
    BossBarManager.getInstance().shutdown();

    // Cleanup holograms (if initialized)
    if (HologramManager.isInitialized()) {
      HologramManager.getInstance().shutdown();
    }

    // Remove all voting holograms
    if (hologramDisplayManager != null && hologramConfigManager != null) {
      hologramDisplayManager.removeAllHolograms(hologramConfigManager.getAllLocations());
    }

    // Shutdown database connections
    var dbManager = com.talexck.gameVoting.utils.database.DatabaseManager.getInstance();
    if (dbManager != null) {
      dbManager.shutdown();
    }

    // Clear all active menus
    ChestUIListener.clearAll();

    if (proxyVersionBridge != null) {
      proxyVersionBridge.shutdown();
    }

    getLogger().info("GameVoting plugin disabled!");
  }
}
