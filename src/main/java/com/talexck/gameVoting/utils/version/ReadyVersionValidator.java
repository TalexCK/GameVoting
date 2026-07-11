package com.talexck.gameVoting.utils.version;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.voting.VotingSession;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/** Ready phase version validation for winning game. */
public final class ReadyVersionValidator {

  private ReadyVersionValidator() {}

  public record ValidationResult(
      boolean allowed, boolean detectionFailed, String expectedVersion, String playerVersion) {}

  /**
   * Validate whether player can ready up in ready phase according to winner version rule.
   *
   * @param player player
   * @param gamesManager games manager
   * @param session voting session
   * @return validation result
   */
  public static ValidationResult validate(
      Player player, GamesConfigManager gamesManager, VotingSession session) {
    if (!session.isReadyPhase()) {
      return new ValidationResult(true, false, null, null);
    }

    String winnerId = session.getWinner();
    if (winnerId == null) {
      return new ValidationResult(true, false, null, null);
    }

    GameConfig winner = gamesManager.getGame(winnerId);
    if (winner == null) {
      return new ValidationResult(true, false, null, null);
    }

    return validate(player, winner);
  }

  public static ValidationResult validate(Player player, GameConfig game) {
    GameVoting plugin = GameVoting.getInstance();
    String playerVersion =
        ProxyAwareClientVersionResolver.resolve(
            player, plugin == null ? null : plugin.getProxyVersionBridge());
    return validate(game, playerVersion);
  }

  public static CompletableFuture<ValidationResult> validateAsync(Player player, GameConfig game) {
    GameVoting plugin = GameVoting.getInstance();
    if (plugin == null || plugin.getProxyVersionBridge() == null) {
      return CompletableFuture.completedFuture(validate(player, game));
    }
    return plugin
        .getProxyVersionBridge()
        .resolvePlayerVersion(player, player.getUniqueId())
        .thenApply(version -> validate(game, version.orElse(null)));
  }

  public static ValidationResult validate(GameConfig game, String playerVersion) {
    boolean hasVersionRange =
        isVersionBoundConfigured(game.getMinVersion())
            || isVersionBoundConfigured(game.getMaxVersion());
    String expectedVersion =
        hasVersionRange
            ? ClientVersionUtil.formatVersionRange(
                game.getVersion(), game.getMinVersion(), game.getMaxVersion())
            : game.getVersion();
    if (isUnrestrictedVersion(expectedVersion)) {
      return new ValidationResult(true, false, expectedVersion, null);
    }

    if (playerVersion == null) {
      return new ValidationResult(false, true, expectedVersion, null);
    }

    boolean matches =
        hasVersionRange
            ? ClientVersionUtil.isVersionInRange(
                playerVersion, game.getMinVersion(), game.getMaxVersion())
            : ClientVersionUtil.isVersionMatch(playerVersion, game.getVersion());
    if (!matches) {
      return new ValidationResult(false, false, expectedVersion, playerVersion);
    }

    return new ValidationResult(true, false, expectedVersion, playerVersion);
  }

  private static boolean isVersionBoundConfigured(String version) {
    return !isUnrestrictedVersion(version);
  }

  private static boolean isUnrestrictedVersion(String version) {
    return version == null
        || version.trim().isEmpty()
        || "any".equalsIgnoreCase(version.trim())
        || "*".equals(version.trim());
  }
}
