package com.talexck.gameVoting;

import com.talexck.gameVoting.api.cloudnet.CloudNetAPI;
import com.talexck.gameVoting.utils.gui.ChestUIListener;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import org.bukkit.plugin.java.JavaPlugin;

public final class GameVoting extends JavaPlugin {

    @Override
    public void onEnable() {
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

        // Register ChestUI listener
        getServer().getPluginManager().registerEvents(new ChestUIListener(), this);
        getLogger().info("ChestUI utility loaded successfully!");

        getLogger().info("GameVoting plugin enabled!");
    }

    @Override
    public void onDisable() {
        // Clear all active menus
        ChestUIListener.clearAll();

        getLogger().info("GameVoting plugin disabled!");
    }
}
