package com.talexck.gameVoting;

import com.talexck.gameVoting.api.cloudnet.CloudNetAPI;
import com.talexck.gameVoting.commands.VoteCommand;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.listeners.BossBarListener;
import com.talexck.gameVoting.party.commands.PartyCommand;
import com.talexck.gameVoting.party.listeners.PartyQuitListener;
import com.talexck.gameVoting.utils.display.BossBarManager;
import com.talexck.gameVoting.utils.gui.ChestUIListener;
import com.talexck.gameVoting.utils.hologram.HologramManager;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class GameVoting extends JavaPlugin {

    private static GameVoting instance;
    private GamesConfigManager gamesManager;
    private com.talexck.gameVoting.config.HologramConfigManager hologramConfigManager;
    private com.talexck.gameVoting.utils.hologram.HologramDisplayManager hologramDisplayManager;

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

    @Override
    public void onEnable() {
        instance = this;
        // Save if it doesn't exist
        saveDefaultConfig();

        try {
            // Initialize CloudNet API (using Driver API injection)
            CloudNetAPI.initialize();
            
            // Test connection by getting services
            int serviceCount = CloudNetAPI.getInstance().getServices().size();
            getLogger().info("Successfully connected to CloudNet v4!");
            getLogger().info("Currently running services: " + serviceCount);
            
            // Log some service info
            for (ServiceInfoSnapshot service : CloudNetAPI.getInstance().getServices()) {
                getLogger().info("  - " + service.name() + " (" + service.serviceId().taskName() + ")");
            }
            
        } catch (Exception ex) {
            getLogger().warning("Failed to connect to CloudNet: " + ex.getMessage());
            getLogger().warning("Make sure this plugin is running on a CloudNet service!");
            getLogger().warning("Plugin will continue to load but CloudNet features will be unavailable.");
        }

        // Initialize LanguageManager
        com.talexck.gameVoting.utils.language.LanguageManager.initialize(this);
        getLogger().info("LanguageManager initialized");

        // Initialize DatabaseManager
        com.talexck.gameVoting.utils.database.DatabaseManager.initialize(this);
        getLogger().info("DatabaseManager initialized");

        // Register ChestUI listener
        getServer().getPluginManager().registerEvents(new ChestUIListener(), this);
        getLogger().info("ChestUI utility loaded successfully!");

        // Register BossBar listener for cleanup
        getServer().getPluginManager().registerEvents(new BossBarListener(), this);
        getLogger().info("BossBarManager initialized");

        // Initialize VoteItem system
        com.talexck.gameVoting.utils.item.VoteItem.initialize(this);
        getLogger().info("VoteItem system initialized");

        // Register VoteItem listener
        getServer().getPluginManager().registerEvents(new com.talexck.gameVoting.listeners.VoteItemListener(), this);
        getLogger().info("VoteItemListener registered");

        // Register PlayerJoin listener
        getServer().getPluginManager().registerEvents(new com.talexck.gameVoting.listeners.PlayerJoinListener(), this);
        getLogger().info("PlayerJoinListener registered");

        // Register VotingPlayerQuit listener
        getServer().getPluginManager().registerEvents(new com.talexck.gameVoting.listeners.VotingPlayerQuitListener(), this);
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
            VoteCommand voteCommand = new VoteCommand(this);
            voteCommand.setGamesManager(gamesManager);
            voteCmd.setExecutor(voteCommand);
            voteCmd.setTabCompleter(new com.talexck.gameVoting.commands.VoteTabCompleter(gamesManager));
            getLogger().info("Registered /vote command with tab completion");
        } else {
            getLogger().warning("Failed to register /vote command - check plugin.yml");
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
        Bukkit.getScheduler().runTaskLater(this, () -> {
            int onlineCount = Bukkit.getOnlinePlayers().size();
            if (onlineCount > 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (onlineCount >= 6) {
                        // Give green emerald for ready system
                        com.talexck.gameVoting.utils.item.VoteItem.giveStartVotingItem(player);
                    } else {
                        // Give redstone block for insufficient players
                        com.talexck.gameVoting.utils.item.VoteItem.giveInsufficientPlayersItem(player);
                    }
                }
                getLogger().info("Given startup items to " + onlineCount + " players");
            }
        }, 20L); // Delay 1 second to ensure all players are loaded

        getLogger().info("GameVoting plugin enabled!");
    }

    @Override
    public void onDisable() {
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

        getLogger().info("GameVoting plugin disabled!");
    }
}
