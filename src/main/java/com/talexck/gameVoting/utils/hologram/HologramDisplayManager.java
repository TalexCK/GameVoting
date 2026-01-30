package com.talexck.gameVoting.utils.hologram;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.api.database.VoteHistoryRepository;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.utils.database.DatabaseManager;
import com.talexck.gameVoting.voting.VotingSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager for hologram display content based on voting state.
 * Updates hologram text dynamically as voting progresses.
 */
public class HologramDisplayManager {
    private final GameVoting plugin;
    private final HologramManager hologramManager;
    private final GamesConfigManager gamesManager;
    
    public enum DisplayState {
        NOT_VOTING,         // Show historical top 10 wins
        PRE_VOTING_READY,   // Show waiting for players to ready up
        VOTING_ACTIVE,      // Show current game list
        VOTE_ENDED,         // Show vote results with winner highlighted
        GAME_STARTED        // Reset to NOT_VOTING after teleport
    }

    public HologramDisplayManager(GameVoting plugin) {
        this.plugin = plugin;
        this.hologramManager = HologramManager.getInstance();
        this.gamesManager = plugin.getGamesManager();
    }

    /**
     * Update all holograms to display content for the given state.
     *
     * @param state The display state
     * @param locations List of hologram locations to update
     */
    public void updateAllHolograms(DisplayState state, List<HologramLocation> locations) {
        for (int i = 0; i < locations.size(); i++) {
            HologramLocation hologramLoc = locations.get(i);
            Location bukkitLoc = hologramLoc.toBukkitLocation();
            
            if (bukkitLoc == null) {
                plugin.getLogger().warning("Hologram #" + i + " world not loaded: " + hologramLoc.getWorldName());
                continue;
            }

            String hologramId = "gamevoting_" + i;
            List<String> lines = generateLines(state);
            
            // Remove old hologram and create new one
            hologramManager.deleteHologram(hologramId);
            hologramManager.createHologram(hologramId, bukkitLoc, lines);
        }
    }

    /**
     * Generate hologram lines based on display state.
     *
     * @param state The display state
     * @return List of text lines for hologram
     */
    private List<String> generateLines(DisplayState state) {
        List<String> lines = new ArrayList<>();

        switch (state) {
            case NOT_VOTING:
                lines.addAll(generateHistoricalTopGames());
                break;
            case PRE_VOTING_READY:
                lines.addAll(generatePreVotingReady());
                break;
            case VOTING_ACTIVE:
                lines.addAll(generateVotingGameList());
                break;
            case VOTE_ENDED:
                lines.addAll(generateVoteResults());
                break;
            case GAME_STARTED:
                lines.addAll(generateHistoricalTopGames());
                break;
        }

        return lines;
    }

    /**
     * Generate hologram lines for historical top 10 winning games.
     *
     * @return List of text lines
     */
    private List<String> generateHistoricalTopGames() {
        List<String> lines = new ArrayList<>();

        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();

        lines.add(langManager.getMessage("hologram.top_games_title"));
        lines.add(langManager.getMessage("hologram.top_games_header"));
        lines.add(langManager.getMessage("hologram.top_games_title"));
        lines.add("");

        DatabaseManager dbManager = DatabaseManager.getInstance();
        if (dbManager != null && dbManager.hasVoteHistoryRepository()) {
            VoteHistoryRepository repository = dbManager.getVoteHistoryRepository();
            Map<String, Integer> topGames = repository.getTopWinningGames(10);

            if (topGames.isEmpty()) {
                lines.add(langManager.getMessage("hologram.no_history"));
            } else {
                int rank = 1;
                for (Map.Entry<String, Integer> entry : topGames.entrySet()) {
                    String gameId = entry.getKey();
                    int wins = entry.getValue();

                    GameConfig gameConfig = gamesManager.getGame(gameId);
                    String gameName = gameConfig != null ? gameConfig.getName() : gameId;

                    String medal = getMedal(rank);
                    lines.add(String.format("&f%s &e%s &7- &a%d wins", medal, gameName, wins));
                    rank++;
                }
            }
        } else {
            lines.add(langManager.getMessage("hologram.database_disabled"));
        }

        lines.add("");
        lines.add(langManager.getMessage("hologram.waiting"));

        return lines;
    }

    /**
     * Generate hologram lines for pre-voting ready phase.
     *
     * @return List of text lines
     */
    private List<String> generatePreVotingReady() {
        List<String> lines = new ArrayList<>();

        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();

        lines.add(langManager.getMessage("hologram.waiting_to_start_title"));
        lines.add(langManager.getMessage("hologram.waiting_to_start_header"));
        lines.add(langManager.getMessage("hologram.waiting_to_start_title"));
        lines.add("");

        VotingSession session = VotingSession.getInstance();
        int readyCount = session.getPreVotingReadyCount();
        int totalPlayers = Bukkit.getOnlinePlayers().size();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("ready", String.valueOf(readyCount));
        placeholders.put("total", String.valueOf(totalPlayers));
        lines.add(langManager.getMessage("hologram.ready_status", placeholders));
        lines.add("");
        lines.add(langManager.getMessage("hologram.ready_instruction_1"));
        lines.add(langManager.getMessage("hologram.ready_instruction_2"));

        return lines;
    }

    /**
     * Generate hologram lines for active voting game list.
     *
     * @return List of text lines
     */
    private List<String> generateVotingGameList() {
        List<String> lines = new ArrayList<>();

        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();

        lines.add(langManager.getMessage("hologram.voting_active_title"));
        lines.add(langManager.getMessage("hologram.voting_active_header"));
        lines.add(langManager.getMessage("hologram.voting_active_title"));
        lines.add("");
        lines.add(langManager.getMessage("hologram.available_games"));
        lines.add("");

        for (GameConfig game : gamesManager.getGames()) {
            lines.add("&f• &e" + game.getName());
        }

        lines.add("");
        lines.add(langManager.getMessage("hologram.vote_instruction"));

        return lines;
    }

    /**
     * Generate hologram lines for vote results with winner highlighted.
     *
     * @return List of text lines
     */
    private List<String> generateVoteResults() {
        List<String> lines = new ArrayList<>();

        var langManager = com.talexck.gameVoting.utils.language.LanguageManager.getInstance();

        lines.add(langManager.getMessage("hologram.vote_results_title"));
        lines.add(langManager.getMessage("hologram.vote_results_header"));
        lines.add(langManager.getMessage("hologram.vote_results_title"));
        lines.add("");

        VotingSession session = VotingSession.getInstance();
        Map<String, Integer> voteCounts = session.getVoteCounts();
        String winnerId = session.getWinner();

        if (voteCounts.isEmpty()) {
            lines.add(langManager.getMessage("hologram.no_votes"));
        } else {
            int rank = 1;
            int maxDisplay = 10; // Show top 10 only

            for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
                if (rank > maxDisplay) {
                    break; // Stop after top 10
                }

                String gameId = entry.getKey();
                int votes = entry.getValue();

                GameConfig gameConfig = gamesManager.getGame(gameId);
                String gameName = gameConfig != null ? gameConfig.getName() : gameId;

                boolean isWinner = gameId.equals(winnerId);

                if (isWinner) {
                    String medal = getMedal(rank);
                    lines.add(String.format("&a&l%s %s - %d votes", medal, gameName.toUpperCase(), votes));
                    lines.add(langManager.getMessage("hologram.winner_label"));
                } else {
                    String medal = getMedal(rank);
                    lines.add(String.format("&f%s &e%s &7- &a%d votes", medal, gameName, votes));
                }

                rank++;
            }
        }

        lines.add("");
        lines.add(langManager.getMessage("hologram.get_ready"));

        return lines;
    }

    /**
     * Get medal emoji for ranking.
     *
     * @param rank Ranking position (1-based)
     * @return Medal emoji string
     */
    private String getMedal(int rank) {
        switch (rank) {
            case 1: return "&6\uD83E\uDD47"; // 🥇
            case 2: return "&7\uD83E\uDD48"; // 🥈
            case 3: return "&c\uD83E\uDD49"; // 🥉
            default: return "&f#" + rank;
        }
    }

    /**
     * Remove all holograms.
     *
     * @param locations List of hologram locations
     */
    public void removeAllHolograms(List<HologramLocation> locations) {
        for (int i = 0; i < locations.size(); i++) {
            String hologramId = "gamevoting_" + i;
            hologramManager.deleteHologram(hologramId);
        }
    }
}
