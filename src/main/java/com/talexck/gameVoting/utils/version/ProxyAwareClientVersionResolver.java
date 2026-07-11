package com.talexck.gameVoting.utils.version;

import com.talexck.gameVoting.proxy.ProxyVersionBridge;
import java.util.function.Function;
import org.bukkit.entity.Player;

public final class ProxyAwareClientVersionResolver {
  private ProxyAwareClientVersionResolver() {}

  public static String resolve(Player player, ProxyVersionBridge proxyVersionBridge) {
    return resolve(player, proxyVersionBridge, ClientVersionUtil::detectPlayerVersion);
  }

  static String resolve(
      Player player,
      ProxyVersionBridge proxyVersionBridge,
      Function<Player, String> backendDetector) {
    if (proxyVersionBridge == null) {
      return backendDetector.apply(player);
    }
    String cached = proxyVersionBridge.getCachedVersion(player.getUniqueId()).orElse(null);
    if (cached != null) {
      return cached;
    }
    proxyVersionBridge.requestPlayerVersion(player, player.getUniqueId());
    return null;
  }
}
