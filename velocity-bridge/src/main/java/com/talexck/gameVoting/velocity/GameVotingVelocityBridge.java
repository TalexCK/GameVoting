package com.talexck.gameVoting.velocity;

import com.google.inject.Inject;
import com.talexck.gameVoting.velocity.commands.GameInfoCommand;
import com.talexck.gameVoting.velocity.commands.ProxyHelpCommand;
import com.talexck.gameVoting.velocity.config.BridgeConfig;
import com.talexck.gameVoting.velocity.config.BridgeConfigLoader;
import com.talexck.gameVoting.velocity.config.SchedulerGameCatalogClient;
import com.viaversion.viaversion.api.Via;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

@Plugin(
    id = "gamevoting-velocity-bridge",
    name = "GameVotingVelocityBridge",
    version = "1.0.0",
    authors = {"talexck"},
    dependencies = {@Dependency(id = "viaversion")})
public final class GameVotingVelocityBridge {

  private static final MinecraftChannelIdentifier CHANNEL =
      MinecraftChannelIdentifier.create("gamevoting", "version");
  private static final String REQUEST_VERSION = "REQ_VERSION";
  private static final String RESPONSE_VERSION = "RESP_VERSION";

  private final ProxyServer proxyServer;
  private final Logger logger;
  private final BridgeConfigLoader bridgeConfigLoader;
  private final Optional<SchedulerGameCatalogClient> schedulerGameCatalogClient;
  private final Map<UUID, String> versionCache = new ConcurrentHashMap<>();
  private volatile BridgeConfig bridgeConfig;
  private CommandMeta gameCommandMeta;
  private CommandMeta helpCommandMeta;

  @Inject
  public GameVotingVelocityBridge(
      ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
    this.proxyServer = proxyServer;
    this.logger = logger;
    this.bridgeConfigLoader = new BridgeConfigLoader(dataDirectory);
    this.schedulerGameCatalogClient = SchedulerGameCatalogClient.fromEnvironment();
    this.bridgeConfig = BridgeConfig.empty();
  }

  @Subscribe
  public void onProxyInit(ProxyInitializeEvent event) {
    proxyServer.getChannelRegistrar().register(CHANNEL);
    logger.info("Registered plugin message channel: {}", CHANNEL.getId());
    reloadBridgeConfig();
    registerCommands(proxyServer.getCommandManager());
  }

  @Subscribe
  public void onProxyShutdown(ProxyShutdownEvent event) {
    unregisterCommands(proxyServer.getCommandManager());
    proxyServer.getChannelRegistrar().unregister(CHANNEL);
    versionCache.clear();
  }

  @Subscribe
  public void onPostLogin(PostLoginEvent event) {
    updatePlayerVersion(event.getPlayer());
  }

  @Subscribe
  public void onServerConnected(ServerConnectedEvent event) {
    updatePlayerVersion(event.getPlayer());
  }

  @Subscribe
  public void onDisconnect(DisconnectEvent event) {
    versionCache.remove(event.getPlayer().getUniqueId());
  }

  @Subscribe
  public void onPluginMessage(PluginMessageEvent event) {
    if (!CHANNEL.equals(event.getIdentifier())) {
      return;
    }

    event.setResult(PluginMessageEvent.ForwardResult.handled());
    if (!(event.getSource() instanceof ServerConnection backend)) {
      return;
    }

    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
      String type = in.readUTF();
      if (!REQUEST_VERSION.equals(type)) {
        return;
      }

      UUID target = UUID.fromString(in.readUTF());
      String version = resolveVersion(target).orElse("unknown");
      sendVersionResponse(backend, target, version);
    } catch (Exception e) {
      logger.warn("Failed to handle version request message", e);
    }
  }

  private void updatePlayerVersion(Player player) {
    resolvePlayerVersion(player).ifPresent(version -> versionCache.put(player.getUniqueId(), version));
  }

  private Optional<String> resolveVersion(UUID playerId) {
    Optional<Player> player = proxyServer.getPlayer(playerId);
    if (player.isPresent()) {
      Optional<String> resolved = resolvePlayerVersion(player.get());
      resolved.ifPresent(version -> versionCache.put(playerId, version));
      return resolved;
    }
    return Optional.ofNullable(versionCache.get(playerId));
  }

  private Optional<String> resolvePlayerVersion(Player player) {
    return ClientVersionResolver.resolve(
        player.getUniqueId(), player.getProtocolVersion().getProtocol(), this::viaProtocol);
  }

  private ClientVersionResolver.ViaProtocol viaProtocol(UUID playerId) {
    com.viaversion.viaversion.api.protocol.version.ProtocolVersion protocol =
        Via.getAPI().getPlayerProtocolVersion(playerId);
    if (protocol == null) {
      return null;
    }
    return new ClientVersionResolver.ViaProtocol(
        protocol.getOriginalVersion(), protocol.getName());
  }

  private void sendVersionResponse(ServerConnection backend, UUID target, String version)
      throws IOException {
    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut)) {
      out.writeUTF(RESPONSE_VERSION);
      out.writeUTF(target.toString());
      out.writeUTF(version);
      backend.sendPluginMessage(CHANNEL, byteOut.toByteArray());
    }
  }

  private void reloadBridgeConfig() {
    BridgeConfig loaded;
    try {
      loaded = bridgeConfigLoader.load();
    } catch (IOException e) {
      logger.error("Failed to load Velocity command config", e);
      loaded = BridgeConfig.empty();
    }

    if (schedulerGameCatalogClient.isPresent()) {
      try {
        var games = schedulerGameCatalogClient.orElseThrow().load();
        if (!games.isEmpty()) {
          loaded = loaded.withGames(games);
          logger.info("Loaded {} games from Scheduler for /game", games.size());
        } else {
          logger.warn("Scheduler game catalog is empty; using local Velocity config");
        }
      } catch (IOException error) {
        logger.warn(
            "Failed to load Scheduler game catalog; using local Velocity config: {}",
            error.getMessage());
      }
    }
    bridgeConfig = loaded;
    logger.info("Loaded Velocity command config, {} games available", loaded.getGames().size());
  }

  private void registerCommands(CommandManager commandManager) {
    // 覆盖全局 /help
    commandManager.unregister("help");
    helpCommandMeta = commandManager.metaBuilder("help").plugin(this).build();
    commandManager.register(helpCommandMeta, new ProxyHelpCommand(this::currentConfig));

    gameCommandMeta = commandManager.metaBuilder("game").plugin(this).build();
    commandManager.register(gameCommandMeta, new GameInfoCommand(this::currentConfig));
  }

  private void unregisterCommands(CommandManager commandManager) {
    if (helpCommandMeta != null) {
      commandManager.unregister(helpCommandMeta);
    }
    if (gameCommandMeta != null) {
      commandManager.unregister(gameCommandMeta);
    }
  }

  private BridgeConfig currentConfig() {
    return bridgeConfig;
  }

}
