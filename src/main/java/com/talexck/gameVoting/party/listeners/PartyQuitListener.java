package com.talexck.gameVoting.party.listeners;

import com.talexck.gameVoting.party.PartyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener that handles player disconnects to clean up party state.
 * Removes players from parties and pending invitations when they quit.
 */
public class PartyQuitListener implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PartyManager.getInstance().handlePlayerQuit(event.getPlayer().getUniqueId());
    }
}
