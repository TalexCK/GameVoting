package com.talexck.gameVoting.listeners;

import com.talexck.gameVoting.utils.display.BossBarManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for boss bar cleanup on player disconnect.
 * Ensures that boss bars are properly removed when players leave the server
 * to prevent memory leaks.
 */
public class BossBarListener implements Listener {

    /**
     * Handle player quit event to clean up their boss bar.
     *
     * @param event The player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Remove any active boss bar for the disconnecting player
        BossBarManager.getInstance().removeBar(event.getPlayer());
    }
}
