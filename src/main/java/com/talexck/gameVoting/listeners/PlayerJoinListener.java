package com.talexck.gameVoting.listeners;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.ui.VotingUI;
import com.talexck.gameVoting.voting.VotingSession;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Listener for player join events. Manages giving appropriate items based on current state. */
public class PlayerJoinListener implements Listener {

  /** Handle player join - give appropriate item based on player count and voting state. */
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    VotingSession session = VotingSession.getInstance();

    // Wait 1 tick to ensure player is fully loaded
    Bukkit.getScheduler()
        .runTaskLater(
            com.talexck.gameVoting.GameVoting.getInstance(),
            () -> {
              applyForcedSpawnpoint(player);

              int onlineCount = Bukkit.getOnlinePlayers().size();
              int requiredPlayers = session.getRequiredPlayers();

              // Check current voting state
              if (session.isPreVotingReady()) {
                // Already in pre-voting ready phase - give emerald
                com.talexck.gameVoting.utils.item.VoteItem.giveStartVotingItem(player);
              } else if (session.isActive()) {
                // Voting is active - give compass
                com.talexck.gameVoting.utils.item.VoteItem.giveVotingItem(player);
                VotingUI.refreshOpenVotingUIs();
                refreshVotingHolograms();
              } else if (session.isReadyPhase()) {
                // 准备阶段默认已准备：加入即绿色状态
                session.markPlayerReady(player.getUniqueId());
                com.talexck.gameVoting.utils.item.VoteItem.updateReadyItem(player, true);
              } else {
                // No voting active - give appropriate waiting item based on player count
                if (onlineCount >= requiredPlayers) {
                  // Give emerald for ready system
                  com.talexck.gameVoting.utils.item.VoteItem.giveStartVotingItem(player);

                  // If this join reached the required threshold, update all players and announce
                  // start tip.
                  if (onlineCount - 1 < requiredPlayers) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                      com.talexck.gameVoting.utils.item.VoteItem.giveStartVotingItem(online);
                    }
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("required", String.valueOf(requiredPlayers));
                    com.talexck.gameVoting.utils.message.MessageUtil.broadcastTranslated(
                        "ready.reached_min_players_start", placeholders);
                  }
                } else {
                  // Not enough players - give redstone block
                  com.talexck.gameVoting.utils.item.VoteItem.giveInsufficientPlayersItem(player);
                }
              }
              com.talexck.gameVoting.utils.item.VoteItem.giveSoloItem(player);

              // Query client version from velocity bridge.
              if (GameVoting.getInstance().getProxyVersionBridge() != null) {
                GameVoting.getInstance()
                    .getProxyVersionBridge()
                    .requestPlayerVersion(player, player.getUniqueId());
              }
            },
            1L);
  }

  private void refreshVotingHolograms() {
    GameVoting plugin = GameVoting.getInstance();
    if (plugin == null
        || plugin.getHologramConfigManager() == null
        || plugin.getHologramDisplayManager() == null) {
      return;
    }

    plugin
        .getHologramDisplayManager()
        .updateAllHolograms(
            com.talexck.gameVoting.utils.hologram.HologramDisplayManager.DisplayState.VOTING_ACTIVE,
            plugin.getHologramConfigManager().getAllLocations());
  }

  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    GameVoting plugin = GameVoting.getInstance();
    if (plugin != null) {
      Bukkit.getScheduler()
          .runTaskLater(
              plugin,
              () ->
                  com.talexck.gameVoting.utils.item.VoteItem.giveSoloItem(event.getPlayer()),
              1L);
    }
    if (plugin == null || !plugin.getConfig().getBoolean("spawnpoint.enable", false)) {
      return;
    }

    Location spawnLocation = getConfiguredSpawnLocation(plugin);
    if (spawnLocation == null) {
      return;
    }

    event.setRespawnLocation(spawnLocation);
  }

  private void applyForcedSpawnpoint(Player player) {
    GameVoting plugin = GameVoting.getInstance();
    if (plugin == null || !plugin.getConfig().getBoolean("spawnpoint.enable", false)) {
      return;
    }

    Location spawnLocation = getConfiguredSpawnLocation(plugin);
    if (spawnLocation == null) {
      return;
    }

    player.setBedSpawnLocation(spawnLocation, true);
  }

  private Location getConfiguredSpawnLocation(GameVoting plugin) {
    World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    if (world == null) {
      return null;
    }

    double x = plugin.getConfig().getDouble("spawnpoint.x", 0.0D);
    double y = plugin.getConfig().getDouble("spawnpoint.y", 64.0D);
    double z = plugin.getConfig().getDouble("spawnpoint.z", 0.0D);
    return new Location(world, x, y, z);
  }
}
