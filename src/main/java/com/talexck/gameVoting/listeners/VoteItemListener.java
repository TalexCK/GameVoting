package com.talexck.gameVoting.listeners;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.commands.VoteCommand;
import com.talexck.gameVoting.ui.VotingUI;
import com.talexck.gameVoting.utils.item.VoteItem;
import com.talexck.gameVoting.utils.message.MessageUtil;
import com.talexck.gameVoting.voting.VotingSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for vote item interactions.
 * Handles right-click to open voting/ready UI and prevents dropping the item.
 */
public class VoteItemListener implements Listener {
    
    // Cooldown map to prevent double-clicking (UUID -> timestamp in milliseconds)
    private final Map<UUID, Long> readyCooldowns = new HashMap<>();
    private static final long READY_COOLDOWN_MS = 1000; // 1 second cooldown

    /**
     * Handle player interaction with vote item (right-click).
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if player is clicking with the vote item
        if (item == null || !VoteItem.isVoteItem(item)) {
            return;
        }

        // Only handle right-click
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        event.setCancelled(true);

        String itemType = VoteItem.getVoteItemType(item);
        if (itemType == null) {
            return;
        }

        VotingSession session = VotingSession.getInstance();

        switch (itemType) {
            case "insufficient_players":
                // Show insufficient players message
                int currentPlayers = Bukkit.getOnlinePlayers().size();
                com.talexck.gameVoting.utils.display.ActionBarUtil.sendActionBar(player,
                    "&c&lInsufficient Players! &7(" + currentPlayers + "/6) &eAdmin can use &6/vote start");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                break;
                
            case "start_voting":
                // Handle pre-voting ready phase
                // Check cooldown to prevent double-clicking
                long currentTimeStart = System.currentTimeMillis();
                Long lastClickTimeStart = readyCooldowns.get(player.getUniqueId());
                
                if (lastClickTimeStart != null && (currentTimeStart - lastClickTimeStart) < READY_COOLDOWN_MS) {
                    return;
                }
                
                readyCooldowns.put(player.getUniqueId(), currentTimeStart);
                
                if (session.isPreVotingReady()) {
                    if (session.isPreVotingPlayerReady(player.getUniqueId())) {
                        // Unready
                        session.unmarkPreVotingReady(player.getUniqueId());
                        VoteItem.updateStartVotingItem(player, false);
                        
                        int readyCount = session.getPreVotingReadyCount();
                        int totalPlayers = Bukkit.getOnlinePlayers().size();
                        
                        MessageUtil.sendMessage(player, "&cYou are no longer ready to start voting!");
                        MessageUtil.broadcast("&e" + player.getName() + " &cis no longer ready! &7(" + readyCount + "/" + totalPlayers + ")");
                    } else {
                        // Ready up
                        session.markPreVotingReady(player.getUniqueId());
                        VoteItem.updateStartVotingItem(player, true);
                        
                        int readyCount = session.getPreVotingReadyCount();
                        int totalPlayers = Bukkit.getOnlinePlayers().size();
                        
                        MessageUtil.sendMessage(player, "&aYou are ready to start voting!");
                        MessageUtil.broadcast("&e" + player.getName() + " &ais ready! &7(" + readyCount + "/" + totalPlayers + ")");
                        
                        // Check if all players are ready
                        if (session.allPlayersReadyToVote()) {
                            MessageUtil.broadcast("&a&lAll players are ready! Starting voting session...");
                            
                            // Actually start voting with stored duration
                            Bukkit.getScheduler().runTask(GameVoting.getInstance(), () -> {
                                GameVoting plugin = GameVoting.getInstance();
                                VoteCommand voteCommand = new VoteCommand(plugin);
                                voteCommand.setGamesManager(plugin.getGamesManager());
                                voteCommand.actuallyStartVoting(session.getPendingVotingDuration());
                            });
                        }
                    }
                } else {
                    MessageUtil.sendMessage(player, "&cPre-voting ready phase is not active!");
                }
                break;
                
            case "vote":
                // Open voting UI
                if (session.isActive()) {
                    VotingUI ui = new VotingUI(player, GameVoting.getInstance().getGamesManager());
                    ui.open(player);
                } else {
                    MessageUtil.sendMessage(player, "&cVoting is not currently active!");
                }
                break;

            case "ready":
                // Check cooldown to prevent double-clicking
                long currentTime = System.currentTimeMillis();
                Long lastClickTime = readyCooldowns.get(player.getUniqueId());
                
                if (lastClickTime != null && (currentTime - lastClickTime) < READY_COOLDOWN_MS) {
                    // Still in cooldown, ignore click
                    return;
                }
                
                // Update cooldown timestamp
                readyCooldowns.put(player.getUniqueId(), currentTime);
                
                // Toggle ready status
                if (session.isReadyPhase()) {
                    if (session.isPlayerReady(player.getUniqueId())) {
                        // Unready - cancel ready status
                        session.unmarkPlayerReady(player.getUniqueId());
                        VoteItem.updateReadyItem(player, false);

                        int readyCount = session.getReadyCount();
                        int totalPlayers = Bukkit.getOnlinePlayers().size();

                        MessageUtil.sendMessage(player, "&cYou are no longer ready!");
                        MessageUtil.broadcast("&e" + player.getName() + " &cis no longer ready! &7(" + readyCount + "/" + totalPlayers + ")");

                        // Stop countdown if it was running
                        if (session.isCountdownActive()) {
                            session.stopCountdown();
                            MessageUtil.broadcast("&c&lCountdown cancelled! Waiting for all players to ready up...");
                        }
                    } else {
                        // Ready up
                        session.markPlayerReady(player.getUniqueId());
                        VoteItem.updateReadyItem(player, true);

                        int readyCount = session.getReadyCount();
                        int totalPlayers = Bukkit.getOnlinePlayers().size();

                        MessageUtil.sendMessage(player, "&aYou are now ready!");
                        MessageUtil.broadcast("&e" + player.getName() + " &ais ready! &7(" + readyCount + "/" + totalPlayers + ")");

                        // Check if all players are ready
                        if (session.allPlayersReady()) {
                            MessageUtil.broadcast("&a&lAll players are ready! Starting countdown...");

                            // Start 10-second countdown
                            session.startCountdown(GameVoting.getInstance(), () -> {
                                // After countdown, start the game
                                Bukkit.getScheduler().runTask(GameVoting.getInstance(), () -> {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vote gamestart");
                                });
                            });
                        }
                    }
                } else {
                    MessageUtil.sendMessage(player, "&cReady phase is not active!");
                }
                break;
        }
    }

    /**
     * Prevent players from dropping the vote item.
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        if (VoteItem.isVoteItem(item)) {
            event.setCancelled(true);
            MessageUtil.sendMessage(event.getPlayer(), "&cYou cannot drop this item!");
        }
    }

    /**
     * Prevent players from moving the vote item in their inventory.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item != null && VoteItem.isVoteItem(item)) {
            // Check if player is trying to move it from slot 8
            if (event.getSlot() == VoteItem.getVoteItemSlot()) {
                event.setCancelled(true);
            }
        }

        // Also check cursor item
        ItemStack cursor = event.getCursor();
        if (cursor != null && VoteItem.isVoteItem(cursor)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent players from picking up vote items if they already have one.
     */
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        ItemStack item = event.getItem().getItemStack();
        if (VoteItem.isVoteItem(item)) {
            event.setCancelled(true);
        }
    }

    /**
     * Remove vote item when player quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Vote item will be automatically removed when player logs out
        // No special handling needed
    }
}
