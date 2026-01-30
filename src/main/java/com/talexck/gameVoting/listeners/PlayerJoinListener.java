package com.talexck.gameVoting.listeners;

import com.talexck.gameVoting.voting.VotingSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener for player join events.
 * Manages giving appropriate items based on current state.
 */
public class PlayerJoinListener implements Listener {

    /**
     * Handle player join - give appropriate item based on player count and voting state.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        VotingSession session = VotingSession.getInstance();

        // Wait 1 tick to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(com.talexck.gameVoting.GameVoting.getInstance(), () -> {
            int onlineCount = Bukkit.getOnlinePlayers().size();

            // Check current voting state
            if (session.isPreVotingReady()) {
                // Already in pre-voting ready phase - give emerald
                com.talexck.gameVoting.utils.item.VoteItem.giveStartVotingItem(player);
            } else if (session.isActive()) {
                // Voting is active - give compass
                com.talexck.gameVoting.utils.item.VoteItem.giveVotingItem(player);
            } else if (session.isReadyPhase()) {
                // In ready phase after voting - give gray dye (not ready yet)
                com.talexck.gameVoting.utils.item.VoteItem.giveReadyItem(player);
            } else {
                // No voting active - give appropriate waiting item based on player count
                if (onlineCount >= 6) {
                    // Give emerald for ready system
                    com.talexck.gameVoting.utils.item.VoteItem.giveStartVotingItem(player);
                } else {
                    // Not enough players - give redstone block
                    com.talexck.gameVoting.utils.item.VoteItem.giveInsufficientPlayersItem(player);
                }
            }
        }, 1L);
    }
}
