package com.talexck.gameVoting.velocity;

import com.google.inject.Inject;
import com.talexck.gameVoting.velocity.commands.GameInfoCommand;
import com.talexck.gameVoting.velocity.commands.ProxyHelpCommand;
import com.talexck.gameVoting.velocity.config.BridgeConfig;
import com.talexck.gameVoting.velocity.config.BridgeConfigLoader;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

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

@Plugin(
    id = "gamevoting-velocity-bridge",
    name = "GameVotingVelocityBridge",
    version = "1.0.0",
    authors = {"talexck"}
)
public final class GameVotingVelocityBridge {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("gamevoting", "version");
    private static final String REQUEST_VERSION = "REQ_VERSION";
    private static final String RESPONSE_VERSION = "RESP_VERSION";

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final BridgeConfigLoader bridgeConfigLoader;
    private final Map<UUID, String> versionCache = new ConcurrentHashMap<>();
    private volatile BridgeConfig bridgeConfig;
    private CommandMeta gameCommandMeta;
    private CommandMeta helpCommandMeta;

    @Inject
    public GameVotingVelocityBridge(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.bridgeConfigLoader = new BridgeConfigLoader(dataDirectory);
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
        String version = protocolToVersion(player.getProtocolVersion());
        if (version != null) {
            versionCache.put(player.getUniqueId(), version);
        }
    }

    private Optional<String> resolveVersion(UUID playerId) {
        String cached = versionCache.get(playerId);
        if (cached != null) {
            return Optional.of(cached);
        }

        return proxyServer.getPlayer(playerId).map(player -> {
            String resolved = protocolToVersion(player.getProtocolVersion());
            if (resolved != null) {
                versionCache.put(playerId, resolved);
            }
            return resolved;
        });
    }

    private void sendVersionResponse(ServerConnection backend, UUID target, String version) throws IOException {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteOut)) {
            out.writeUTF(RESPONSE_VERSION);
            out.writeUTF(target.toString());
            out.writeUTF(version);
            backend.sendPluginMessage(CHANNEL, byteOut.toByteArray());
        }
    }

    private void reloadBridgeConfig() {
        try {
            bridgeConfig = bridgeConfigLoader.load();
            logger.info("Loaded Velocity command config, {} games available", bridgeConfig.getGames().size());
        } catch (IOException e) {
            bridgeConfig = BridgeConfig.empty();
            logger.error("Failed to load Velocity command config", e);
        }
    }

    private void registerCommands(CommandManager commandManager) {
        // 覆盖全局 /help
        commandManager.unregister("help");
        helpCommandMeta = commandManager.metaBuilder("help")
            .plugin(this)
            .build();
        commandManager.register(helpCommandMeta, new ProxyHelpCommand(this::currentConfig));

        gameCommandMeta = commandManager.metaBuilder("game")
            .plugin(this)
            .build();
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

    private String protocolToVersion(ProtocolVersion protocolVersion) {
        int protocol = protocolVersion.getProtocol();
        return switch (protocol) {
            case 47 -> "1.8";
            case 107 -> "1.9";
            case 108 -> "1.9.1";
            case 109 -> "1.9.2";
            case 110 -> "1.9.4";
            case 210 -> "1.10";
            case 315 -> "1.11";
            case 316 -> "1.11.1/1.11.2";
            case 335 -> "1.12";
            case 338 -> "1.12.1";
            case 340 -> "1.12.2";
            case 393 -> "1.13";
            case 401 -> "1.13.1";
            case 404 -> "1.13.2";
            case 477 -> "1.14";
            case 480 -> "1.14.1";
            case 485 -> "1.14.2";
            case 490 -> "1.14.3";
            case 498 -> "1.14.4";
            case 573 -> "1.15";
            case 575 -> "1.15.1";
            case 578 -> "1.15.2";
            case 735 -> "1.16";
            case 736 -> "1.16.1";
            case 751 -> "1.16.2";
            case 753 -> "1.16.3";
            case 754 -> "1.16.4/1.16.5";
            case 755 -> "1.17";
            case 756 -> "1.17.1";
            case 757 -> "1.18/1.18.1";
            case 758 -> "1.18.2";
            case 759 -> "1.19";
            case 760 -> "1.19.1/1.19.2";
            case 761 -> "1.19.3";
            case 762 -> "1.19.4";
            case 763 -> "1.20/1.20.1";
            case 764 -> "1.20.2";
            case 765 -> "1.20.3/1.20.4";
            case 766 -> "1.20.5/1.20.6";
            case 767 -> "1.21/1.21.1";
            case 768 -> "1.21.2/1.21.3";
            case 769 -> "1.21.4";
            case 770 -> "1.21.5";
            case 771 -> "1.21.6";
            case 772 -> "1.21.7/1.21.8";
            case 773 -> "1.21.9/1.21.10";
            case 774 -> "1.21.11";
            default -> null;
        };
    }
}
