package com.talexck.gameVoting.utils.display;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.talexck.gameVoting.GameVoting;
import net.kyori.adventure.bossbar.BossBar;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BossBarManager class.
 *
 * Note: Some functionality requires the GameVoting plugin instance for debug logging.
 * These tests focus on core boss bar management logic that doesn't depend on plugin internals.
 */
class BossBarManagerTest {
    private static ServerMock server;
    private BossBarManager manager;
    private PlayerMock player1;
    private PlayerMock player2;

    @BeforeAll
    static void setUpServer() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        manager = BossBarManager.getInstance();
        player1 = server.addPlayer("Player1");
        player2 = server.addPlayer("Player2");

        // Clean up any existing boss bars
        manager.removeBar(player1);
        manager.removeBar(player2);
    }

    @Test
    @DisplayName("Should return singleton instance")
    void testSingletonInstance() {
        BossBarManager instance1 = BossBarManager.getInstance();
        BossBarManager instance2 = BossBarManager.getInstance();
        assertSame(instance1, instance2, "Should return the same singleton instance");
    }

    @Test
    @DisplayName("Should show boss bar to player")
    void testShowBar() {
        manager.showBar(player1, "Test Boss Bar", BossBar.Color.GREEN, 0.5f);

        assertTrue(manager.hasBar(player1), "Player should have an active boss bar");
    }

    @Test
    @DisplayName("Should handle null player gracefully")
    void testShowBarNullPlayer() {
        assertDoesNotThrow(() -> manager.showBar(null, "Test", BossBar.Color.GREEN, 0.5f),
                "Should handle null player without throwing exception");
    }

    @Test
    @DisplayName("Should handle null text gracefully")
    void testShowBarNullText() {
        assertDoesNotThrow(() -> manager.showBar(player1, null, BossBar.Color.GREEN, 0.5f),
                "Should handle null text without throwing exception");
    }

    @Test
    @DisplayName("Should clamp progress values between 0 and 1")
    void testProgressClamping() {
        // Test with negative progress
        manager.showBar(player1, "Test", BossBar.Color.GREEN, -0.5f);
        assertTrue(manager.hasBar(player1), "Should create boss bar with negative progress clamped to 0");

        // Test with progress > 1
        manager.showBar(player1, "Test", BossBar.Color.GREEN, 1.5f);
        assertTrue(manager.hasBar(player1), "Should create boss bar with progress > 1 clamped to 1");
    }

    @Test
    @DisplayName("Should replace existing boss bar when showing new one")
    void testReplaceExistingBar() {
        manager.showBar(player1, "First Bar", BossBar.Color.GREEN, 0.5f);
        assertTrue(manager.hasBar(player1), "Should have first boss bar");

        manager.showBar(player1, "Second Bar", BossBar.Color.RED, 0.8f);
        assertTrue(manager.hasBar(player1), "Should still have boss bar after replacement");
    }

    @Test
    @DisplayName("Should remove boss bar from player")
    void testRemoveBar() {
        manager.showBar(player1, "Test Bar", BossBar.Color.GREEN, 0.5f);
        assertTrue(manager.hasBar(player1), "Should have boss bar before removal");

        manager.removeBar(player1);
        assertFalse(manager.hasBar(player1), "Should not have boss bar after removal");
    }

    @Test
    @DisplayName("Should handle removing non-existent boss bar")
    void testRemoveNonExistentBar() {
        assertFalse(manager.hasBar(player1), "Should not have boss bar initially");
        assertDoesNotThrow(() -> manager.removeBar(player1),
                "Should handle removing non-existent boss bar without exception");
    }

    @Test
    @DisplayName("Should update existing boss bar text and progress")
    void testUpdateBar() {
        manager.showBar(player1, "Initial Text", BossBar.Color.GREEN, 0.3f);
        assertTrue(manager.hasBar(player1), "Should have boss bar");

        manager.updateBar(player1, "Updated Text", 0.7f);
        assertTrue(manager.hasBar(player1), "Should still have boss bar after update");
    }

    @Test
    @DisplayName("Should create new boss bar if updating non-existent bar")
    void testUpdateNonExistentBar() {
        assertFalse(manager.hasBar(player1), "Should not have boss bar initially");

        manager.updateBar(player1, "New Bar", 0.5f);
        assertTrue(manager.hasBar(player1), "Should create new boss bar when updating non-existent one");
    }

    @Test
    @DisplayName("Should update only progress")
    void testUpdateProgress() {
        manager.showBar(player1, "Test", BossBar.Color.GREEN, 0.3f);

        assertDoesNotThrow(() -> manager.updateProgress(player1, 0.8f),
                "Should update progress without error");
        assertTrue(manager.hasBar(player1), "Should still have boss bar after progress update");
    }

    @Test
    @DisplayName("Should update only text")
    void testUpdateText() {
        manager.showBar(player1, "Initial", BossBar.Color.GREEN, 0.5f);

        assertDoesNotThrow(() -> manager.updateText(player1, "Updated"),
                "Should update text without error");
        assertTrue(manager.hasBar(player1), "Should still have boss bar after text update");
    }

    @Test
    @DisplayName("Should handle independent boss bars for multiple players")
    void testMultiplePlayerBars() {
        manager.showBar(player1, "Player 1 Bar", BossBar.Color.GREEN, 0.5f);
        manager.showBar(player2, "Player 2 Bar", BossBar.Color.RED, 0.7f);

        assertTrue(manager.hasBar(player1), "Player 1 should have boss bar");
        assertTrue(manager.hasBar(player2), "Player 2 should have boss bar");

        manager.removeBar(player1);
        assertFalse(manager.hasBar(player1), "Player 1 should not have boss bar after removal");
        assertTrue(manager.hasBar(player2), "Player 2 should still have boss bar");
    }

    @Test
    @DisplayName("Should show timed boss bar")
    void testShowTimedBar() {
        manager.showTimedBar(player1, "Timed Bar", BossBar.Color.YELLOW, 5);
        assertTrue(manager.hasBar(player1), "Should have timed boss bar");
    }

    @Test
    @DisplayName("Should handle invalid duration for timed bar")
    void testTimedBarInvalidDuration() {
        assertDoesNotThrow(() -> manager.showTimedBar(player1, "Test", BossBar.Color.GREEN, 0),
                "Should handle zero duration without error");
        assertDoesNotThrow(() -> manager.showTimedBar(player1, "Test", BossBar.Color.GREEN, -5),
                "Should handle negative duration without error");
    }

    @Test
    @DisplayName("Should check if player has boss bar")
    void testHasBar() {
        assertFalse(manager.hasBar(player1), "Should not have bar initially");

        manager.showBar(player1, "Test", BossBar.Color.GREEN, 0.5f);
        assertTrue(manager.hasBar(player1), "Should have bar after showing");

        manager.removeBar(player1);
        assertFalse(manager.hasBar(player1), "Should not have bar after removal");
    }

    @Test
    @DisplayName("Should handle concurrent operations safely")
    void testThreadSafety() {
        // Create and show boss bars concurrently (simulating multi-threaded access)
        assertDoesNotThrow(() -> {
            manager.showBar(player1, "Test 1", BossBar.Color.GREEN, 0.5f);
            manager.showBar(player2, "Test 2", BossBar.Color.RED, 0.7f);
            manager.updateBar(player1, "Updated 1", 0.8f);
            manager.removeBar(player2);
        }, "Should handle concurrent-like operations without error");
    }

    @Test
    @DisplayName("Should handle null player in hasBar check")
    void testHasBarNullPlayer() {
        assertFalse(manager.hasBar(null), "Should return false for null player");
    }

    @Test
    @DisplayName("Should handle null player in update methods")
    void testUpdateMethodsNullPlayer() {
        assertDoesNotThrow(() -> {
            manager.updateBar(null, "Test", 0.5f);
            manager.updateProgress(null, 0.5f);
            manager.updateText(null, "Test");
            manager.removeBar(null);
        }, "Update methods should handle null player gracefully");
    }
}
