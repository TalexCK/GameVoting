package com.talexck.gameVoting.commands;

import com.schedulerbridge.common.ServerInstance;
import com.schedulerbridge.common.SoloSession;
import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.ui.SoloCreateUI;
import com.talexck.gameVoting.ui.SoloPlayerPickerUI;
import com.talexck.gameVoting.ui.SoloUI;
import com.talexck.gameVoting.utils.ColorUtil;
import com.talexck.gameVoting.utils.gui.ChestUI;
import com.talexck.gameVoting.utils.gui.ChestUIListener;
import com.talexck.gameVoting.utils.language.LanguageManager;
import com.talexck.gameVoting.utils.message.MessageUtil;
import com.talexck.gameVoting.utils.version.ReadyVersionValidator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public final class SoloCommand implements CommandExecutor, TabCompleter, Listener {
  enum OperationPhase {
    CHECKING,
    LAUNCHING,
    DESTROYING
  }

  record PlayerSelection(UUID owner, List<UUID> players, String existingSessionId) {}

  record WorldOperation(UUID token, OperationPhase phase, Player player) {}

  private static final long INVITE_TIMEOUT_MILLIS = 60_000L;
  private final GameVoting plugin;
  private final GamesConfigManager gamesManager;
  private final SoloCreationManager creationManager = new SoloCreationManager();
  private final Map<UUID, WorldOperation> worldOperations = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> inviteOperations = new ConcurrentHashMap<>();

  public SoloCommand(GameVoting plugin, GamesConfigManager gamesManager) {
    this.plugin = plugin;
    this.gamesManager = gamesManager;
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use /solo.");
      return true;
    }
    if (!player.hasPermission("gamevoting.solo")) {
      MessageUtil.sendTranslated(player, "solo.no_permission");
      return true;
    }
    if (args.length == 0) {
      cancelWorldCheck(player);
      openCatalog(player);
      return true;
    }
    if (args[0].equalsIgnoreCase("start") && args.length == 2) {
      GameConfig game = gamesManager.getSoloGame(args[1]);
      if (game == null) {
        sendGameNotFound(player, args[1]);
        return true;
      }
      select(player, game);
      return true;
    }
    if (args[0].equalsIgnoreCase("destroy") && args.length == 2) {
      destroy(player, args[1]);
      return true;
    }
    if (args[0].equalsIgnoreCase("accept") && args.length == 2) {
      acceptInvite(player, args[1]);
      return true;
    }
    if (args[0].equalsIgnoreCase("decline") && args.length == 2) {
      declineInvite(player, args[1]);
      return true;
    }
    MessageUtil.sendTranslated(player, "solo.usage");
    return true;
  }

  public void select(Player player, GameConfig game) {
    if (!game.isSolo()) {
      sendGameNotFound(player, game.getId());
      return;
    }
    if (game.getSoloMode().equals("shared")) {
      launch(
          player,
          game,
          new PlayerSelection(player.getUniqueId(), List.of(player.getUniqueId()), null));
      return;
    }
    UUID operation = beginWorldOperation(player, OperationPhase.CHECKING);
    if (operation == null) {
      MessageUtil.sendTranslated(player, "solo.request_in_progress");
      return;
    }
    player.closeInventory();
    Map<String, String> placeholders = gamePlaceholders(game);
    MessageUtil.sendTranslated(player, "solo.checking_world", placeholders);
    plugin
        .getServerScheduler()
        .findSoloSession(game.getId(), player.getUniqueId())
        .whenComplete(
            (session, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> handleWorldLookup(player, game, operation, session, error)));
  }

  private void handleWorldLookup(
      Player player,
      GameConfig game,
      UUID operation,
      Optional<SoloSession> session,
      Throwable error) {
    if (!isCurrentWorldOperation(player, operation)) {
      return;
    }
    Map<String, String> placeholders = gamePlaceholders(game);
    if (error != null) {
      finishWorldOperation(player, operation);
      placeholders.put("error", ColorUtil.stripColors(rootMessage(error)));
      MessageUtil.sendTranslated(player, "solo.world_check_failed", placeholders);
      plugin
          .getLogger()
          .severe(
              "Failed to query solo sessions for "
                  + game.getId()
                  + ": "
                  + rootMessage(error));
      return;
    }
    if (!sameConnection(player)) {
      finishWorldOperation(player, operation);
      return;
    }
    if (hasBlockingInventory(player)) {
      finishWorldOperation(player, operation);
      MessageUtil.sendTranslated(player, "solo.world_check_cancelled");
      return;
    }
    if (session != null && session.isPresent()) {
      if (!transitionWorldOperation(player, operation, OperationPhase.LAUNCHING)) {
        return;
      }
      creationManager.clearOwner(player.getUniqueId());
      validateSelectionAndLaunch(
          player,
          game,
          new PlayerSelection(
              player.getUniqueId(), List.of(player.getUniqueId()), session.get().sessionId()),
          operation);
      return;
    }
    finishWorldOperation(player, operation);
    try {
      creationManager.begin(player.getUniqueId(), game.getId());
    } catch (IllegalStateException reserved) {
      MessageUtil.sendTranslated(player, "solo.player_reserved");
      return;
    }
    openCreateMenu(player, game);
  }

  private void openCatalog(Player player) {
    new SoloUI(player, gamesManager, this::queueSelect).open(player);
  }

  private void queueSelect(Player player, GameConfig game) {
    nextTick(() -> select(player, game));
  }

  private void openCreateMenu(Player player, GameConfig game) {
    Optional<SoloCreationManager.DraftView> current =
        creationManager.draft(player.getUniqueId(), game.getId());
    SoloCreationManager.DraftView draft;
    try {
      draft =
          current.orElseGet(() -> creationManager.begin(player.getUniqueId(), game.getId()));
    } catch (IllegalStateException reserved) {
      MessageUtil.sendTranslated(player, "solo.player_reserved");
      return;
    }
    new SoloCreateUI(
            player,
            game,
            draft,
            ignored -> nextTick(() -> openPlayerPicker(player, game)),
            ignored -> nextTick(() -> createDraft(player, game)),
            ignored -> nextTick(() -> clearDraftSelection(player, game)),
            ignored -> nextTick(() -> openCatalog(player)))
        .open(player);
  }

  private void openPlayerPicker(Player player, GameConfig game) {
    if (creationManager.draft(player.getUniqueId(), game.getId()).isEmpty()) {
      try {
        creationManager.begin(player.getUniqueId(), game.getId());
      } catch (IllegalStateException reserved) {
        MessageUtil.sendTranslated(player, "solo.player_reserved");
        return;
      }
    }
    new SoloPlayerPickerUI(
            player,
            game,
            (owner, target) -> nextTick(() -> invite(owner, target, game)),
            ignored -> nextTick(() -> openCreateMenu(player, game)))
        .open(player);
  }

  private void invite(Player owner, Player target, GameConfig game) {
    if (!sameConnection(owner)
        || !sameConnection(target)
        || owner.getUniqueId().equals(target.getUniqueId())) {
      MessageUtil.sendTranslated(owner, "solo.invite_target_unavailable");
      openPlayerPicker(owner, game);
      return;
    }
    if (creationManager.draft(owner.getUniqueId(), game.getId()).isEmpty()) {
      MessageUtil.sendTranslated(owner, "solo.create_draft_missing");
      openCreateMenu(owner, game);
      return;
    }
    if (creationManager.isReserved(game.getId(), target.getUniqueId())) {
      MessageUtil.sendTranslated(owner, "solo.invite_player_reserved");
      openPlayerPicker(owner, game);
      return;
    }
    UUID operation = beginInviteOperation(owner);
    if (operation == null) {
      MessageUtil.sendTranslated(owner, "solo.request_in_progress");
      return;
    }
    MessageUtil.sendTranslated(owner, "solo.checking_invite_target");
    plugin
        .getServerScheduler()
        .soloSessions()
        .whenComplete(
            (sessions, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () ->
                            finishInviteCheck(owner, target, game, operation, sessions, error)));
  }

  private void finishInviteCheck(
      Player owner,
      Player target,
      GameConfig game,
      UUID operation,
      List<SoloSession> sessions,
      Throwable error) {
    if (!finishInviteOperation(owner, operation) || !sameConnection(owner)) {
      return;
    }
    if (error != null) {
      MessageUtil.sendTranslated(owner, "solo.invite_check_failed");
      plugin
          .getLogger()
          .severe(
              "Failed to check solo invite target for "
                  + game.getId()
                  + ": "
                  + rootMessage(error));
      return;
    }
    if (!sameConnection(target)) {
      MessageUtil.sendTranslated(owner, "solo.invite_target_unavailable");
      openPlayerPicker(owner, game);
      return;
    }
    if (creationManager.draft(owner.getUniqueId(), game.getId()).isEmpty()) {
      MessageUtil.sendTranslated(owner, "solo.create_draft_missing");
      return;
    }
    if (hasSoloSession(sessions, game.getId(), owner.getUniqueId())
        || hasSoloSession(sessions, game.getId(), target.getUniqueId())) {
      MessageUtil.sendTranslated(owner, "solo.invite_player_has_world");
      openPlayerPicker(owner, game);
      return;
    }
    if (creationManager.isReserved(game.getId(), target.getUniqueId())) {
      MessageUtil.sendTranslated(owner, "solo.invite_player_reserved");
      openPlayerPicker(owner, game);
      return;
    }
    SoloCreationManager.Invite invite;
    try {
      invite =
          creationManager.invite(
              owner.getUniqueId(),
              game.getId(),
              target.getUniqueId(),
              System.currentTimeMillis(),
              INVITE_TIMEOUT_MILLIS);
    } catch (IllegalStateException missing) {
      MessageUtil.sendTranslated(owner, "solo.create_draft_missing");
      return;
    }
    Map<String, String> placeholders = gamePlaceholders(game);
    placeholders.put("player", target.getName());
    placeholders.put("owner", owner.getName());
    MessageUtil.sendTranslated(owner, "solo.invite_sent", placeholders);
    sendInviteRequest(target, invite, placeholders);
    openCreateMenu(owner, game);
    scheduleInviteExpiry(invite);
  }

  private void sendInviteRequest(
      Player target, SoloCreationManager.Invite invite, Map<String, String> placeholders) {
    LanguageManager language = LanguageManager.getInstance();
    Component request = ColorUtil.colorize(language.getMessage("solo.invite_request", placeholders));
    Component accept =
        ColorUtil.colorize(language.getMessage("solo.invite_accept_button"))
            .clickEvent(ClickEvent.runCommand("/solo accept " + invite.token()))
            .hoverEvent(
                HoverEvent.showText(
                    ColorUtil.colorize(language.getMessage("solo.invite_accept_hover"))));
    Component decline =
        ColorUtil.colorize(language.getMessage("solo.invite_decline_button"))
            .clickEvent(ClickEvent.runCommand("/solo decline " + invite.token()))
            .hoverEvent(
                HoverEvent.showText(
                    ColorUtil.colorize(language.getMessage("solo.invite_decline_hover"))));
    target.sendMessage(
        request
            .append(Component.space())
            .append(accept)
            .append(Component.space())
            .append(decline));
  }

  private void scheduleInviteExpiry(SoloCreationManager.Invite invite) {
    long remaining = Math.max(1L, invite.expiresAt() - System.currentTimeMillis());
    long ticks = Math.max(1L, (remaining + 49L) / 50L);
    Bukkit.getScheduler().runTaskLater(plugin, () -> expireInvite(invite), ticks);
  }

  private void expireInvite(SoloCreationManager.Invite expected) {
    long remaining = expected.expiresAt() - System.currentTimeMillis();
    if (remaining > 0L) {
      scheduleInviteExpiry(expected);
      return;
    }
    Optional<SoloCreationManager.Invite> expired =
        creationManager.expire(expected.token(), System.currentTimeMillis());
    if (expired.isEmpty()) {
      return;
    }
    Player owner = Bukkit.getPlayer(expected.owner());
    Player target = Bukkit.getPlayer(expected.target());
    if (target != null && target.isOnline()) {
      MessageUtil.sendTranslated(target, "solo.invite_expired");
    }
    if (owner != null && owner.isOnline()) {
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("player", target == null ? expected.target().toString() : target.getName());
      MessageUtil.sendTranslated(owner, "solo.invite_expired_owner", placeholders);
      refreshCreateMenu(owner, expected.gameId());
    }
  }

  private void acceptInvite(Player target, String tokenValue) {
    UUID token = parseInviteToken(target, tokenValue);
    if (token == null) {
      return;
    }
    SoloCreationManager.Resolution inspected =
        creationManager.inspect(token, target.getUniqueId(), System.currentTimeMillis());
    if (inspected.status() != SoloCreationManager.ResolutionStatus.PENDING) {
      handleInviteResolution(target, inspected);
      return;
    }
    SoloCreationManager.Invite invite = inspected.invite();
    Player owner = Bukkit.getPlayer(invite.owner());
    GameConfig game = gamesManager.getSoloGame(invite.gameId());
    if (owner == null || !owner.isOnline() || game == null) {
      creationManager.invalidate(token);
      MessageUtil.sendTranslated(target, "solo.invite_unavailable");
      return;
    }
    UUID operation = beginInviteOperation(target);
    if (operation == null) {
      MessageUtil.sendTranslated(target, "solo.request_in_progress");
      return;
    }
    MessageUtil.sendTranslated(target, "solo.checking_invitation");
    plugin
        .getServerScheduler()
        .soloSessions()
        .whenComplete(
            (sessions, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () ->
                            finishInviteAcceptance(
                                target, invite, operation, sessions, error)));
  }

  private void finishInviteAcceptance(
      Player target,
      SoloCreationManager.Invite invite,
      UUID operation,
      List<SoloSession> sessions,
      Throwable error) {
    if (!finishInviteOperation(target, operation) || !sameConnection(target)) {
      return;
    }
    if (error != null) {
      MessageUtil.sendTranslated(target, "solo.invite_check_failed");
      plugin
          .getLogger()
          .severe(
              "Failed to validate solo invitation for "
                  + invite.gameId()
                  + ": "
                  + rootMessage(error));
      return;
    }
    if (hasSoloSession(sessions, invite.gameId(), invite.owner())
        || hasSoloSession(sessions, invite.gameId(), invite.target())
        || creationManager.isReserved(invite.gameId(), invite.target())) {
      creationManager.invalidate(invite.token());
      notifyInviteConflict(target, invite);
      return;
    }
    SoloCreationManager.Resolution resolution =
        creationManager.accept(invite.token(), target.getUniqueId(), System.currentTimeMillis());
    if (resolution.status() == SoloCreationManager.ResolutionStatus.ALREADY_RESERVED) {
      creationManager.invalidate(invite.token());
      notifyInviteConflict(target, invite);
      return;
    }
    handleInviteResolution(target, resolution);
  }

  private void declineInvite(Player target, String tokenValue) {
    UUID token = parseInviteToken(target, tokenValue);
    if (token == null) {
      return;
    }
    SoloCreationManager.Resolution resolution =
        creationManager.decline(token, target.getUniqueId(), System.currentTimeMillis());
    handleInviteResolution(target, resolution);
  }

  private UUID parseInviteToken(Player player, String tokenValue) {
    try {
      return UUID.fromString(tokenValue);
    } catch (IllegalArgumentException error) {
      MessageUtil.sendTranslated(player, "solo.invite_unavailable");
      return null;
    }
  }

  private void handleInviteResolution(
      Player target, SoloCreationManager.Resolution resolution) {
    switch (resolution.status()) {
      case ACCEPTED -> handleAcceptedInvite(target, resolution.invite());
      case DECLINED -> handleDeclinedInvite(target, resolution.invite());
      case EXPIRED -> MessageUtil.sendTranslated(target, "solo.invite_expired");
      case ALREADY_RESERVED -> {
        if (resolution.invite() != null) {
          creationManager.invalidate(resolution.invite().token());
          notifyInviteConflict(target, resolution.invite());
        } else {
          MessageUtil.sendTranslated(target, "solo.invite_unavailable");
        }
      }
      case NOT_FOUND, INVALID_TARGET, DRAFT_MISSING ->
          MessageUtil.sendTranslated(target, "solo.invite_unavailable");
      case PENDING -> MessageUtil.sendTranslated(target, "solo.checking_invitation");
    }
  }

  private void notifyInviteConflict(Player target, SoloCreationManager.Invite invite) {
    MessageUtil.sendTranslated(target, "solo.invite_conflict");
    Player owner = Bukkit.getPlayer(invite.owner());
    if (owner != null && owner.isOnline()) {
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("player", target.getName());
      MessageUtil.sendTranslated(owner, "solo.invite_conflict_owner", placeholders);
      refreshCreateMenu(owner, invite.gameId());
    }
  }

  private void handleAcceptedInvite(Player target, SoloCreationManager.Invite invite) {
    Player owner = Bukkit.getPlayer(invite.owner());
    GameConfig game = gamesManager.getSoloGame(invite.gameId());
    if (owner == null || !owner.isOnline() || game == null) {
      creationManager.clearOwner(invite.owner());
      MessageUtil.sendTranslated(target, "solo.invite_unavailable");
      return;
    }
    Map<String, String> placeholders = gamePlaceholders(game);
    placeholders.put("player", target.getName());
    placeholders.put("owner", owner.getName());
    MessageUtil.sendTranslated(target, "solo.invite_accepted", placeholders);
    MessageUtil.sendTranslated(owner, "solo.teammate_added", placeholders);
    refreshCreateMenu(owner, game.getId());
  }

  private void handleDeclinedInvite(Player target, SoloCreationManager.Invite invite) {
    MessageUtil.sendTranslated(target, "solo.invite_declined");
    Player owner = Bukkit.getPlayer(invite.owner());
    if (owner == null || !owner.isOnline()) {
      return;
    }
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("player", target.getName());
    MessageUtil.sendTranslated(owner, "solo.invite_declined_owner", placeholders);
    refreshCreateMenu(owner, invite.gameId());
  }

  private void refreshCreateMenu(Player owner, String gameId) {
    ChestUI active = ChestUIListener.getActiveMenu(owner);
    if (!(active instanceof SoloCreateUI menu) || !menu.gameId().equalsIgnoreCase(gameId)) {
      return;
    }
    GameConfig game = gamesManager.getSoloGame(gameId);
    if (game != null) {
      openCreateMenu(owner, game);
    }
  }

  private void clearDraftSelection(Player player, GameConfig game) {
    try {
      creationManager.clearSelection(player.getUniqueId(), game.getId());
    } catch (IllegalStateException error) {
      try {
        creationManager.begin(player.getUniqueId(), game.getId());
      } catch (IllegalStateException reserved) {
        MessageUtil.sendTranslated(player, "solo.player_reserved");
        return;
      }
    }
    openCreateMenu(player, game);
  }

  private void createDraft(Player player, GameConfig game) {
    Optional<SoloCreationManager.DraftView> draft =
        creationManager.draft(player.getUniqueId(), game.getId());
    if (draft.isEmpty()) {
      MessageUtil.sendTranslated(player, "solo.create_draft_missing");
      openCreateMenu(player, game);
      return;
    }
    if (draft.get().pendingPlayer() != null) {
      MessageUtil.sendTranslated(player, "solo.invite_pending");
      return;
    }
    UUID teammate = draft.get().acceptedPlayer();
    if (teammate != null) {
      Player target = Bukkit.getPlayer(teammate);
      if (target == null || !target.isOnline()) {
        MessageUtil.sendTranslated(player, "solo.accepted_player_offline");
        return;
      }
    }
    List<UUID> players = creationManager.roster(player.getUniqueId(), game.getId());
    launch(player, game, new PlayerSelection(player.getUniqueId(), players, null));
  }

  private void launch(Player player, GameConfig game, PlayerSelection selection) {
    UUID operation = beginWorldOperation(player, OperationPhase.LAUNCHING);
    if (operation == null) {
      MessageUtil.sendTranslated(player, "solo.request_in_progress");
      return;
    }
    validateSelectionAndLaunch(player, game, selection, operation);
  }

  private void validateSelectionAndLaunch(
      Player player, GameConfig game, PlayerSelection selection, UUID operation) {
    int playerCount = selection.players().size();
    int minimumPlayers = game.getSoloMode().equals("shared") ? 1 : game.getMinPlayers();
    int maximumPlayers = Math.min(game.getMaxPlayers(), game.getSoloMaxPlayers());
    if (playerCount < minimumPlayers || playerCount > maximumPlayers) {
      finishWorldOperation(player, operation);
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("current", String.valueOf(playerCount));
      placeholders.put("min", String.valueOf(minimumPlayers));
      placeholders.put("max", String.valueOf(maximumPlayers));
      MessageUtil.sendTranslated(player, "solo.player_count_invalid", placeholders);
      return;
    }
    validateAndLaunch(player, game, selection, operation, 0);
  }

  private void validateAndLaunch(
      Player player,
      GameConfig game,
      PlayerSelection selection,
      UUID operation,
      int index) {
    if (!isCurrentWorldOperation(player, operation)) {
      return;
    }
    if (!sameConnection(player)) {
      finishWorldOperation(player, operation);
      return;
    }
    if (index >= selection.players().size()) {
      submitLaunch(player, game, selection, operation);
      return;
    }
    Player member = Bukkit.getPlayer(selection.players().get(index));
    if (member == null || !member.isOnline()) {
      finishWorldOperation(player, operation);
      MessageUtil.sendTranslated(player, "solo.party_member_offline");
      return;
    }
    ReadyVersionValidator.validateAsync(member, game)
        .whenComplete(
            (version, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!isCurrentWorldOperation(player, operation)) {
                            return;
                          }
                          if (error != null || version == null || !version.allowed()) {
                            finishWorldOperation(player, operation);
                            sendVersionFailure(player, member, game, version);
                            return;
                          }
                          validateAndLaunch(player, game, selection, operation, index + 1);
                        }));
  }

  private void sendVersionFailure(
      Player player,
      Player member,
      GameConfig game,
      ReadyVersionValidator.ValidationResult version) {
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("player", member.getName());
    placeholders.put(
        "expected",
        version == null
            ? com.talexck.gameVoting.utils.version.ClientVersionUtil.formatVersionRange(
                game.getVersion(), game.getMinVersion(), game.getMaxVersion())
            : version.expectedVersion());
    placeholders.put(
        "current",
        version == null || version.playerVersion() == null ? "Unknown" : version.playerVersion());
    MessageUtil.sendTranslated(
        player,
        version == null || version.detectionFailed()
            ? "solo.version_not_detected"
            : "solo.version_mismatch",
        placeholders);
  }

  private void submitLaunch(
      Player player, GameConfig game, PlayerSelection selection, UUID operation) {
    int playerCount = selection.players().size();
    player.closeInventory();
    Map<String, String> placeholders = gamePlaceholders(game);
    placeholders.put("count", String.valueOf(playerCount));
    sendToPlayers(
        selection.players(),
        game.getSoloMode().equals("shared")
            ? "solo.launching_shared"
            : "solo.launching_player_world",
        placeholders);
    CompletableFuture<ServerInstance> request =
        selection.existingSessionId() == null
            ? plugin
                .getServerScheduler()
                .launchSolo(game.getId(), selection.owner(), selection.players())
            : plugin
                .getServerScheduler()
                .startSoloSession(selection.existingSessionId(), selection.players());
    request.whenComplete(
        (instance, error) ->
            Bukkit.getScheduler()
                .runTask(
                    plugin,
                    () -> {
                      if (!finishWorldOperation(player, operation)) {
                        return;
                      }
                      if (error != null) {
                        placeholders.put("error", ColorUtil.stripColors(rootMessage(error)));
                        sendToPlayers(
                            selection.players(),
                            selection.existingSessionId() != null && isMissingSession(error)
                                ? "solo.saved_world_changed"
                                : "solo.launch_failed",
                            placeholders);
                        plugin
                            .getLogger()
                            .severe(
                                "Failed to launch solo game "
                                    + game.getId()
                                    + ": "
                                    + rootMessage(error));
                        return;
                      }
                      if (game.getSoloMode().equals("player_world")) {
                        creationManager.clearOwner(selection.owner());
                      }
                      sendLaunchAccepted(selection.players(), game, instance);
                    }));
  }

  private void destroy(Player player, String gameId) {
    GameConfig game = gamesManager.getSoloGame(gameId);
    if (game == null) {
      sendGameNotFound(player, gameId);
      return;
    }
    if (!game.getSoloMode().equals("player_world")) {
      MessageUtil.sendTranslated(player, "solo.destroy_shared_denied");
      return;
    }
    UUID operation = beginWorldOperation(player, OperationPhase.DESTROYING);
    if (operation == null) {
      MessageUtil.sendTranslated(player, "solo.request_in_progress");
      return;
    }
    Map<String, String> placeholders = gamePlaceholders(game);
    MessageUtil.sendTranslated(player, "solo.destroying", placeholders);
    plugin
        .getServerScheduler()
        .destroySolo(game.getId(), player.getUniqueId())
        .whenComplete(
            (ignored, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!finishWorldOperation(player, operation)) {
                            return;
                          }
                          if (error != null) {
                            placeholders.put("error", ColorUtil.stripColors(rootMessage(error)));
                            MessageUtil.sendTranslated(player, "solo.destroy_failed", placeholders);
                            plugin
                                .getLogger()
                                .severe(
                                    "Failed to destroy solo world for "
                                        + game.getId()
                                        + ": "
                                        + rootMessage(error));
                            return;
                          }
                          creationManager.clearOwner(player.getUniqueId());
                          MessageUtil.sendTranslated(player, "solo.destroyed", placeholders);
                        }));
  }

  private void sendLaunchAccepted(
      List<UUID> players, GameConfig game, ServerInstance instance) {
    Map<String, String> placeholders = gamePlaceholders(game);
    placeholders.put("server", instance.serverId());
    sendToPlayers(
        players,
        game.getSoloMode().equals("shared")
            ? "solo.launch_accepted_shared"
            : "solo.launch_accepted_player_world",
        placeholders);
  }

  private void sendToPlayers(
      List<UUID> players, String message, Map<String, String> placeholders) {
    for (UUID playerId : players) {
      Player target = Bukkit.getPlayer(playerId);
      if (target != null && target.isOnline()) {
        MessageUtil.sendTranslated(target, message, placeholders);
      }
    }
  }

  private void sendGameNotFound(Player player, String gameId) {
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("game", gameId);
    MessageUtil.sendTranslated(player, "solo.game_not_found", placeholders);
  }

  private UUID beginWorldOperation(Player player, OperationPhase phase) {
    UUID token = UUID.randomUUID();
    WorldOperation created = new WorldOperation(token, phase, player);
    return worldOperations.putIfAbsent(player.getUniqueId(), created) == null ? token : null;
  }

  private boolean transitionWorldOperation(
      Player player, UUID token, OperationPhase phase) {
    UUID playerId = player.getUniqueId();
    WorldOperation current = worldOperations.get(playerId);
    if (current == null || !current.token().equals(token) || current.player() != player) {
      return false;
    }
    return worldOperations.replace(
        playerId, current, new WorldOperation(token, phase, player));
  }

  private boolean isCurrentWorldOperation(Player player, UUID token) {
    WorldOperation current = worldOperations.get(player.getUniqueId());
    return current != null && current.token().equals(token) && current.player() == player;
  }

  private boolean finishWorldOperation(Player player, UUID token) {
    WorldOperation current = worldOperations.get(player.getUniqueId());
    return current != null
        && current.token().equals(token)
        && current.player() == player
        && worldOperations.remove(player.getUniqueId(), current);
  }

  private void cancelWorldCheck(Player player) {
    worldOperations.computeIfPresent(
        player.getUniqueId(),
        (ignored, current) -> current.phase() == OperationPhase.CHECKING ? null : current);
  }

  private UUID beginInviteOperation(Player player) {
    UUID token = UUID.randomUUID();
    return inviteOperations.putIfAbsent(player.getUniqueId(), token) == null ? token : null;
  }

  private boolean finishInviteOperation(Player player, UUID token) {
    return inviteOperations.remove(player.getUniqueId(), token);
  }

  private static boolean hasSoloSession(
      List<SoloSession> sessions, String gameId, UUID player) {
    return sessions.stream()
        .anyMatch(
            session ->
                session.gameId().equalsIgnoreCase(gameId)
                    && session.players().contains(player));
  }

  private static boolean sameConnection(Player player) {
    return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
  }

  private static boolean hasBlockingInventory(Player player) {
    return player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING;
  }

  private void nextTick(Runnable task) {
    Bukkit.getScheduler().runTask(plugin, task);
  }

  private static Map<String, String> gamePlaceholders(GameConfig game) {
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("game", ColorUtil.withReset(game.getName()));
    return placeholders;
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    Optional<SoloCreationManager.DraftView> ownedDraft = creationManager.draft(playerId);
    Optional<SoloCreationManager.Invite> pending = creationManager.clearOwnerAndGetInvite(playerId);
    pending.ifPresent(
        invite -> {
          Player target = Bukkit.getPlayer(invite.target());
          if (target != null && target.isOnline()) {
            MessageUtil.sendTranslated(target, "solo.invite_owner_left");
          }
        });
    ownedDraft
        .map(SoloCreationManager.DraftView::acceptedPlayer)
        .map(Bukkit::getPlayer)
        .filter(Player::isOnline)
        .ifPresent(target -> MessageUtil.sendTranslated(target, "solo.creation_owner_left"));
    for (SoloCreationManager.DraftView draft : creationManager.clearTarget(playerId)) {
      Player owner = Bukkit.getPlayer(draft.owner());
      if (owner != null && owner.isOnline()) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        MessageUtil.sendTranslated(owner, "solo.invite_target_left", placeholders);
        refreshCreateMenu(owner, draft.gameId());
      }
    }
    for (SoloCreationManager.DraftView draft : creationManager.acceptedDrafts(playerId)) {
      Player owner = Bukkit.getPlayer(draft.owner());
      if (owner != null && owner.isOnline()) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        MessageUtil.sendTranslated(owner, "solo.accepted_player_left", placeholders);
        refreshCreateMenu(owner, draft.gameId());
      }
    }
    worldOperations.remove(playerId);
    inviteOperations.remove(playerId);
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    for (SoloCreationManager.DraftView draft : creationManager.acceptedDrafts(playerId)) {
      Player owner = Bukkit.getPlayer(draft.owner());
      if (owner != null && owner.isOnline()) {
        refreshCreateMenu(owner, draft.gameId());
      }
    }
  }

  public void shutdown() {
    creationManager.clear();
    worldOperations.clear();
    inviteOperations.clear();
  }

  @Override
  public List<String> onTabComplete(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) {
      return filter(List.of("start", "destroy"), args[0]);
    }
    if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
      return filter(
          gamesManager.getSoloGames().stream().map(GameConfig::getId).sorted().toList(), args[1]);
    }
    if (args.length == 2 && args[0].equalsIgnoreCase("destroy")) {
      return filter(
          gamesManager.getSoloGames().stream()
              .filter(game -> game.getSoloMode().equals("player_world"))
              .map(GameConfig::getId)
              .sorted()
              .toList(),
          args[1]);
    }
    return Collections.emptyList();
  }

  private static List<String> filter(List<String> values, String prefix) {
    String normalized = prefix.toLowerCase(Locale.ROOT);
    return values.stream()
        .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
        .toList();
  }

  private static boolean isMissingSession(Throwable error) {
    return rootMessage(error).contains("HTTP 404");
  }

  private static String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }
}
