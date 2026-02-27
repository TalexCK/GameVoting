package com.talexck.gameVoting.proxy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paper side proxy bridge for player version queries.
 * Uses plugin message channel to fetch version data from velocity plugin.
 */
public final class ProxyVersionBridge implements PluginMessageListener {

    public static final String CHANNEL = "gamevoting:version";
    private static final String REQUEST_VERSION = "REQ_VERSION";
    private static final String RESPONSE_VERSION = "RESP_VERSION";

    private final JavaPlugin plugin;
    private final Map<UUID, String> versionCache = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;

    public ProxyVersionBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Start channel registration and periodic refresh.
     */
    public void start() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);

        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                requestPlayerVersion(player, player.getUniqueId());
            }
        }, 40L, 200L);
    }

    /**
     * Shutdown bridge and cleanup state.
     */
    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        versionCache.clear();
    }

    /**
     * Request version for the target player UUID via a connected player tunnel.
     *
     * @param tunnelPlayer connected player used to send plugin message
     * @param targetPlayerId target uuid to query
     */
    public void requestPlayerVersion(Player tunnelPlayer, UUID targetPlayerId) {
        if (tunnelPlayer == null || targetPlayerId == null || !tunnelPlayer.isOnline()) {
            return;
        }

        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteOut)) {
            out.writeUTF(REQUEST_VERSION);
            out.writeUTF(targetPlayerId.toString());
            tunnelPlayer.sendPluginMessage(plugin, CHANNEL, byteOut.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send version request to proxy: " + e.getMessage());
        }
    }

    /**
     * Get cached player version from proxy.
     *
     * @param playerId player uuid
     * @return version string if present
     */
    public Optional<String> getCachedVersion(UUID playerId) {
        return Optional.ofNullable(versionCache.get(playerId));
    }

    /**
     * Remove one player from cache.
     *
     * @param playerId player uuid
     */
    public void invalidate(UUID playerId) {
        if (playerId != null) {
            versionCache.remove(playerId);
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String type = in.readUTF();
            if (!RESPONSE_VERSION.equals(type)) {
                return;
            }

            UUID target = UUID.fromString(in.readUTF());
            String version = in.readUTF();
            if (version == null || version.isBlank() || "unknown".equalsIgnoreCase(version)) {
                return;
            }
            versionCache.put(target, version);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse proxy version response: " + e.getMessage());
        }
    }
}

