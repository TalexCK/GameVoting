package com.talexck.gameVoting.voting;

import com.talexck.gameVoting.utils.display.BossBarManager;
import com.talexck.gameVoting.utils.language.LanguageManager;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Task that displays countdown timer via boss bar during voting session.
 * Updates every second with remaining time and progress bar.
 */
public class VotingCountdownTask extends BukkitRunnable {
    private final VotingSession session;
    private final int totalSeconds;

    public VotingCountdownTask(VotingSession session, int durationMinutes) {
        this.session = session;
        this.totalSeconds = durationMinutes * 60;
    }

    @Override
    public void run() {
        if (!session.isActive()) {
            cleanup();
            cancel();
            return;
        }

        int remaining = session.getRemainingSeconds();
        if (remaining <= 0) {
            cleanup();
            cancel();
            return;
        }

        // Calculate progress (1.0 at start, 0.0 at end)
        float progress = (float) remaining / totalSeconds;

        // Determine color based on remaining time
        BossBar.Color color;
        if (remaining > 60) {
            color = BossBar.Color.GREEN;
        } else if (remaining > 30) {
            color = BossBar.Color.YELLOW;
        } else {
            color = BossBar.Color.RED;
        }

        // Format time display
        String timeStr = formatTime(remaining);

        // Get translated title with placeholder
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("time", timeStr);
        String title = LanguageManager.getInstance().getMessage("voting.countdown_bossbar", placeholders);

        // Update boss bar for all online players
        BossBarManager manager = BossBarManager.getInstance();
        for (Player player : Bukkit.getOnlinePlayers()) {
            manager.showBar(player, title, color, progress);
        }
    }

    /**
     * Format seconds into MM:SS or Ss format.
     *
     * @param seconds Total seconds
     * @return Formatted time string
     */
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        if (minutes > 0) {
            return String.format("%d:%02d", minutes, secs);
        } else {
            return secs + "s";
        }
    }

    /**
     * Clean up boss bars for all players.
     */
    private void cleanup() {
        BossBarManager manager = BossBarManager.getInstance();
        for (Player player : Bukkit.getOnlinePlayers()) {
            manager.removeBar(player);
        }
    }
}
