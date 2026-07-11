package com.talexck.gameVoting.listeners;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.commands.VoteCommand;
import com.talexck.gameVoting.ui.VotingUI;
import com.talexck.gameVoting.utils.item.VoteItem;
import com.talexck.gameVoting.utils.message.MessageUtil;
import com.talexck.gameVoting.utils.version.ReadyVersionValidator;
import com.talexck.gameVoting.voting.VotingSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener for vote item interactions. Handles right-click to open voting/ready UI and prevents
 * dropping the item.
 */
public class VoteItemListener implements Listener {

  // Cooldown map to prevent double-clicking (UUID -> timestamp in milliseconds)
  private final Map<UUID, Long> readyCooldowns = new HashMap<>();
  private static final long READY_COOLDOWN_MS = 1000; // 1 second cooldown

  /** Handle player interaction with vote item (right-click). */
  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem();

    // Check if player is clicking with the vote item
    if (item == null || !VoteItem.isVoteItem(item)) {
      return;
    }

    // Only handle right-click
    if (event.getAction() != Action.RIGHT_CLICK_AIR
        && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
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
        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
        int currentPlayers = Bukkit.getOnlinePlayers().size();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("current", String.valueOf(currentPlayers));
        placeholders.put("required", String.valueOf(session.getRequiredPlayers()));
        com.talexck.gameVoting.utils.display.ActionBarUtil.sendActionBar(
            player, langManager.getMessage("ready.insufficient_players_action", placeholders));
        break;

      case "start_voting":
        // Handle pre-voting ready phase
        // Check cooldown to prevent double-clicking
        long currentTimeStart = System.currentTimeMillis();
        Long lastClickTimeStart = readyCooldowns.get(player.getUniqueId());

        if (lastClickTimeStart != null
            && (currentTimeStart - lastClickTimeStart) < READY_COOLDOWN_MS) {
          return;
        }

        readyCooldowns.put(player.getUniqueId(), currentTimeStart);

        var langMgr = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
        if (session.isPreVotingReady()) {
          if (session.isPreVotingPlayerReady(player.getUniqueId())) {
            // Unready
            session.unmarkPreVotingReady(player.getUniqueId());
            VoteItem.updateStartVotingItem(player, false);

            int readyCount = session.getPreVotingReadyCount();
            int totalPlayers = Bukkit.getOnlinePlayers().size();

            MessageUtil.sendMessage(player, langMgr.getMessage("ready.unready_start"));
            Map<String, String> unreadyPlaceholders = new HashMap<>();
            unreadyPlaceholders.put("player", player.getName());
            unreadyPlaceholders.put("count", String.valueOf(readyCount));
            unreadyPlaceholders.put("total", String.valueOf(totalPlayers));
            MessageUtil.broadcast(
                langMgr.getMessage("ready.player_unready_start", unreadyPlaceholders));
          } else {
            // Ready up
            session.markPreVotingReady(player.getUniqueId());
            VoteItem.updateStartVotingItem(player, true);

            int readyCount = session.getPreVotingReadyCount();
            int totalPlayers = Bukkit.getOnlinePlayers().size();

            MessageUtil.sendMessage(player, langMgr.getMessage("ready.ready_start"));
            Map<String, String> readyPlaceholders = new HashMap<>();
            readyPlaceholders.put("player", player.getName());
            readyPlaceholders.put("count", String.valueOf(readyCount));
            readyPlaceholders.put("total", String.valueOf(totalPlayers));
            MessageUtil.broadcast(
                langMgr.getMessage("ready.player_ready_start", readyPlaceholders));

            // Check if all players are ready
            if (session.allPlayersReadyToVote()) {
              MessageUtil.broadcast(langMgr.getMessage("ready.all_ready_start"));

              // Actually start voting with stored duration
              Bukkit.getScheduler()
                  .runTask(
                      GameVoting.getInstance(),
                      () -> {
                        GameVoting plugin = GameVoting.getInstance();
                        VoteCommand voteCommand = new VoteCommand(plugin);
                        voteCommand.setGamesManager(plugin.getGamesManager());
                        voteCommand.actuallyStartVoting(session.getPendingVotingDuration());
                      });
            }
          }
        } else {
          MessageUtil.sendMessage(player, langMgr.getMessage("ready.prevoting_not_active"));
        }
        break;

      case "vote":
        // Open voting UI
        var voteLangMgr = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
        if (session.isActive()) {
          VotingUI ui = new VotingUI(player, GameVoting.getInstance().getGamesManager());
          ui.open(player);
        } else {
          MessageUtil.sendMessage(player, voteLangMgr.getMessage("ready.voting_not_active"));
        }
        break;

      case "solo":
        player.performCommand("solo");
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
        var readyLangMgr = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
        if (session.isReadyPhase()) {
          if (session.isPlayerReady(player.getUniqueId())) {
            // Unready - cancel ready status
            session.unmarkPlayerReady(player.getUniqueId());
            VoteItem.updateReadyItem(player, false);

            int readyCount = session.getReadyCount();
            int totalPlayers = Bukkit.getOnlinePlayers().size();

            MessageUtil.sendMessage(player, readyLangMgr.getMessage("ready.unready_message"));
            Map<String, String> unreadyPlaceholders2 = new HashMap<>();
            unreadyPlaceholders2.put("player", player.getName());
            unreadyPlaceholders2.put("count", String.valueOf(readyCount));
            unreadyPlaceholders2.put("total", String.valueOf(totalPlayers));
            MessageUtil.broadcast(
                readyLangMgr.getMessage("ready.player_unready_broadcast", unreadyPlaceholders2));

            // Stop countdown if it was running
            if (session.isCountdownActive()) {
              session.stopCountdown();
              MessageUtil.broadcast(readyLangMgr.getMessage("ready.countdown_cancelled_broadcast"));
            }
          } else {
            ReadyVersionValidator.ValidationResult versionResult =
                ReadyVersionValidator.validate(
                    player, GameVoting.getInstance().getGamesManager(), session);
            if (!versionResult.allowed()) {
              Map<String, String> versionPlaceholders = new HashMap<>();
              versionPlaceholders.put("expected", versionResult.expectedVersion());
              versionPlaceholders.put(
                  "current",
                  versionResult.playerVersion() == null
                      ? "Unknown"
                      : versionResult.playerVersion());
              if (versionResult.detectionFailed()) {
                MessageUtil.sendTranslated(
                    player, "ready.version_not_detected", versionPlaceholders);
              } else {
                MessageUtil.sendTranslated(player, "ready.version_mismatch", versionPlaceholders);
              }
              return;
            }

            // Ready up
            session.markPlayerReady(player.getUniqueId());
            VoteItem.updateReadyItem(player, true);

            int readyCount = session.getReadyCount();
            int totalPlayers = Bukkit.getOnlinePlayers().size();

            MessageUtil.sendMessage(player, readyLangMgr.getMessage("ready.ready_message"));
            Map<String, String> readyPlaceholders2 = new HashMap<>();
            readyPlaceholders2.put("player", player.getName());
            readyPlaceholders2.put("count", String.valueOf(readyCount));
            readyPlaceholders2.put("total", String.valueOf(totalPlayers));
            MessageUtil.broadcast(
                readyLangMgr.getMessage("ready.player_ready_broadcast", readyPlaceholders2));

            // Check if all players are ready
            if (session.allPlayersReady()) {
              MessageUtil.broadcast(readyLangMgr.getMessage("ready.all_ready_countdown"));

              // Start 10-second countdown
              session.startCountdown(
                  GameVoting.getInstance(),
                  () -> {
                    // After countdown, start the game
                    Bukkit.getScheduler()
                        .runTask(
                            GameVoting.getInstance(),
                            () -> {
                              Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vote gamestart");
                            });
                  });
            }
          }
        } else {
          MessageUtil.sendMessage(player, readyLangMgr.getMessage("ready.not_in_ready_phase"));
        }
        break;
    }
  }

  /** Prevent players from dropping the vote item. */
  @EventHandler
  public void onPlayerDropItem(PlayerDropItemEvent event) {
    ItemStack item = event.getItemDrop().getItemStack();

    if (VoteItem.isVoteItem(item)) {
      event.setCancelled(true);
      var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();
      MessageUtil.sendMessage(event.getPlayer(), langManager.getMessage("item.cannot_drop"));
    }
  }

  /** Prevent players from moving the vote item in their inventory. */
  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }

    ItemStack item = event.getCurrentItem();
    if (item != null && VoteItem.isVoteItem(item)) {
      event.setCancelled(true);
    }
    if (event.getClickedInventory() != null
        && event.getClickedInventory().equals(player.getInventory())
        && VoteItem.isProtectedSlot(event.getSlot())) {
      event.setCancelled(true);
    }
    if (event.getClick() == ClickType.NUMBER_KEY
        && VoteItem.isProtectedSlot(event.getHotbarButton())) {
      event.setCancelled(true);
    }

    // Also check cursor item
    ItemStack cursor = event.getCursor();
    if (cursor != null && VoteItem.isVoteItem(cursor)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) {
      return;
    }
    int topSize = event.getView().getTopInventory().getSize();
    for (int rawSlot : event.getRawSlots()) {
      if (rawSlot >= topSize && VoteItem.isProtectedSlot(event.getView().convertSlot(rawSlot))) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler
  public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
    if (VoteItem.isVoteItem(event.getMainHandItem())
        || VoteItem.isVoteItem(event.getOffHandItem())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    event.getDrops().removeIf(VoteItem::isVoteItem);
  }

  /** Prevent players from picking up vote items if they already have one. */
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

  /** Remove vote item when player quits. */
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    if (GameVoting.getInstance().getProxyVersionBridge() != null) {
      GameVoting.getInstance().getProxyVersionBridge().invalidate(event.getPlayer().getUniqueId());
    }
  }
}
