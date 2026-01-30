package com.talexck.gameVoting.listeners;

import com.talexck.gameVoting.voting.VotingSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for player quit events related to voting.
 * Updates items for remaining players when someone leaves.
 */
public class VotingPlayerQuitListener implements Listener {

    /**
     * Handle player quit - update items for remaining players if needed.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        VotingSession session = VotingSession.getInstance();

        // Wait 1 tick to ensure player count is updated
        Bukkit.getScheduler().runTaskLater(com.talexck.gameVoting.GameVoting.getInstance(), () -> {
            int onlineCount = Bukkit.getOnlinePlayers().size();

            // Only update if no voting is active and player count dropped below 6
            if (!session.isActive() && !session.isPreVotingReady() && !session.isReadyPhase()) {
                if (onlineCount < 6 && onlineCount > 0) {
                    // Give insufficient players item to all remaining players
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        com.talexck.gameVoting.utils.item.VoteItem.giveInsufficientPlayersItem(player);
                    }
                }
            }
        }, 1L);
    }
}
