package com.talexck.gameVoting.utils.version;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.voting.VotingSession;
import org.bukkit.entity.Player;

/**
 * Ready phase version validation for winning game.
 */
public final class ReadyVersionValidator {

    private ReadyVersionValidator() {
    }

    public record ValidationResult(boolean allowed, boolean detectionFailed, String expectedVersion, String playerVersion) {
    }

    /**
     * Validate whether player can ready up in ready phase according to winner version rule.
     *
     * @param player player
     * @param gamesManager games manager
     * @param session voting session
     * @return validation result
     */
    public static ValidationResult validate(Player player, GamesConfigManager gamesManager, VotingSession session) {
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

        String expectedVersion = winner.getVersion();
        if (expectedVersion == null || expectedVersion.trim().isEmpty()
            || "any".equalsIgnoreCase(expectedVersion.trim())
            || "*".equals(expectedVersion.trim())) {
            return new ValidationResult(true, false, expectedVersion, null);
        }

        String playerVersion = null;
        GameVoting plugin = GameVoting.getInstance();
        if (plugin != null && plugin.getProxyVersionBridge() != null) {
            playerVersion = plugin.getProxyVersionBridge().getCachedVersion(player.getUniqueId()).orElse(null);
            if (playerVersion == null) {
                plugin.getProxyVersionBridge().requestPlayerVersion(player, player.getUniqueId());
            }
        }

        if (playerVersion == null) {
            playerVersion = ClientVersionUtil.detectPlayerVersion(player);
        }
        if (playerVersion == null) {
            return new ValidationResult(false, true, expectedVersion, null);
        }

        if (!ClientVersionUtil.isVersionMatch(playerVersion, expectedVersion)) {
            return new ValidationResult(false, false, expectedVersion, playerVersion);
        }

        return new ValidationResult(true, false, expectedVersion, playerVersion);
    }
}
