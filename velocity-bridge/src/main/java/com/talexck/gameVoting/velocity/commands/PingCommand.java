package com.talexck.gameVoting.velocity.commands;

import com.talexck.gameVoting.velocity.utils.LegacyColorUtil;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PingCommand implements SimpleCommand {

    private final ProxyServer proxyServer;

    public PingCommand(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Override
    public void execute(Invocation invocation) {
        Map<PlayerCategory, List<PlayerPingInfo>> groupedPlayers = new EnumMap<>(PlayerCategory.class);
        for (PlayerCategory category : PlayerCategory.values()) {
            groupedPlayers.put(category, new ArrayList<>());
        }

        for (Player player : proxyServer.getAllPlayers()) {
            String entryIp = resolveEntryIp(player);
            PlayerCategory category = classify(entryIp);
            long ping = Math.max(player.getPing(), 0L);
            groupedPlayers.get(category).add(new PlayerPingInfo(player.getUsername(), ping, entryIp));
        }

        invocation.source().sendMessage(LegacyColorUtil.colorize("&6=== 全服在线玩家 Ping 列表 ==="));
        if (proxyServer.getPlayerCount() == 0) {
            invocation.source().sendMessage(LegacyColorUtil.colorize("&7当前没有在线玩家。"));
            return;
        }

        for (PlayerCategory category : PlayerCategory.values()) {
            List<PlayerPingInfo> players = groupedPlayers.get(category);
            players.sort(Comparator.comparingLong(PlayerPingInfo::ping).thenComparing(PlayerPingInfo::name));

            invocation.source().sendMessage(LegacyColorUtil.colorize(
                "&e[" + category.displayName + "] &7在线: &f" + players.size()
            ));

            if (players.isEmpty()) {
                invocation.source().sendMessage(LegacyColorUtil.colorize("&8- 无玩家"));
                invocation.source().sendMessage(LegacyColorUtil.colorize("&b平均 Ping: &fN/A"));
                continue;
            }

            long pingTotal = 0L;
            for (PlayerPingInfo info : players) {
                pingTotal += info.ping();
                invocation.source().sendMessage(LegacyColorUtil.colorize(
                    "&7- &f" + info.name() + " &7: &a" + info.ping() + "ms"
                ));
            }

            double average = pingTotal / (double) players.size();
            invocation.source().sendMessage(LegacyColorUtil.colorize(
                "&b平均 Ping: &f" + String.format(Locale.ROOT, "%.1f", average) + "ms"
            ));
        }
    }

    private PlayerCategory classify(String ip) {
        if (ip.startsWith("10.")) {
            return PlayerCategory.INSIDE;
        }
        return PlayerCategory.OUTSIDE;
    }

    private String resolveEntryIp(Player player) {
        Optional<InetSocketAddress> virtualHost = player.getVirtualHost();
        if (virtualHost.isPresent()) {
            return normalizeHost(virtualHost.get().getHostString());
        }

        SocketAddress remoteAddress = player.getRemoteAddress();
        if (remoteAddress instanceof InetSocketAddress inetSocketAddress) {
            return normalizeHost(inetSocketAddress.getHostString());
        }
        return "unknown";
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "unknown";
        }
        String normalized = host.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private enum PlayerCategory {
        OUTSIDE("校外"),
        INSIDE("校内");

        private final String displayName;

        PlayerCategory(String displayName) {
            this.displayName = displayName;
        }
    }

    private record PlayerPingInfo(String name, long ping, String entryIp) {
    }
}
