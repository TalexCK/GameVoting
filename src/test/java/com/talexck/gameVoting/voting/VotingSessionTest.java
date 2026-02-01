package com.talexck.gameVoting.voting;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.talexck.gameVoting.config.GameConfig;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VotingSession class.
 */
class VotingSessionTest {
    private static ServerMock server;
    private VotingSession session;
    private PlayerMock player1;
    private PlayerMock player2;
    private GameConfig game1;
    private GameConfig game2;
    private GameConfig game3;
    private GameConfig game4;

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
        // Reset singleton instance
        session = VotingSession.getInstance();
        session.clear();

        // Create mock players
        player1 = server.addPlayer("Player1");
        player2 = server.addPlayer("Player2");

        // Create mock game configs
        game1 = new GameConfig("game1", "Game 1", java.util.Arrays.asList("Description 1"),
            org.bukkit.Material.DIAMOND_SWORD, 0, "task1");
        game2 = new GameConfig("game2", "Game 2", java.util.Arrays.asList("Description 2"),
            org.bukkit.Material.BOW, 0, "task2");
        game3 = new GameConfig("game3", "Game 3", java.util.Arrays.asList("Description 3"),
            org.bukkit.Material.FISHING_ROD, 0, "task3");
        game4 = new GameConfig("game4", "Game 4", java.util.Arrays.asList("Description 4"),
            org.bukkit.Material.GOLDEN_APPLE, 0, "task4");
    }

    @Test
    @DisplayName("Should return singleton instance")
    void testSingletonInstance() {
        VotingSession instance1 = VotingSession.getInstance();
        VotingSession instance2 = VotingSession.getInstance();
        assertSame(instance1, instance2, "Should return the same singleton instance");
    }

    @Test
    @DisplayName("Should not be active initially")
    void testInitialState() {
        assertFalse(session.isActive(), "Session should not be active initially");
        assertFalse(session.isReadyPhase(), "Should not be in ready phase initially");
        assertFalse(session.isPreVotingReady(), "Should not be in pre-voting ready phase initially");
    }

    @Test
    @DisplayName("Should reject votes when session is inactive")
    void testVoteWhenInactive() {
        VoteResult result = session.vote(player1, game1);
        assertEquals(VoteResult.SESSION_INACTIVE, result, "Should reject vote when session is inactive");
    }

    @Test
    @DisplayName("Should accept votes when session is active")
    void testVoteWhenActive() {
        session.startVoting();

        VoteResult result = session.vote(player1, game1);
        assertEquals(VoteResult.ADDED, result, "Should accept vote when session is active");
        assertTrue(session.hasVotedFor(player1, "game1"), "Player should have voted for game1");
        assertEquals(1, session.getVoteCount("game1"), "Game1 should have 1 vote");
    }

    @Test
    @DisplayName("Should toggle votes (add and remove)")
    void testVoteToggle() {
        session.startVoting();

        // Add vote
        VoteResult addResult = session.vote(player1, game1);
        assertEquals(VoteResult.ADDED, addResult, "First vote should be added");
        assertTrue(session.hasVotedFor(player1, "game1"), "Player should have voted for game1");

        // Remove vote (toggle)
        VoteResult removeResult = session.vote(player1, game1);
        assertEquals(VoteResult.REMOVED, removeResult, "Second vote should remove the vote");
        assertFalse(session.hasVotedFor(player1, "game1"), "Player should no longer have voted for game1");
        assertEquals(0, session.getVoteCount("game1"), "Game1 should have 0 votes");
    }

    @Test
    @DisplayName("Should enforce 3-vote limit per player")
    void testVoteLimit() {
        session.startVoting();

        // Cast 3 votes (max)
        assertEquals(VoteResult.ADDED, session.vote(player1, game1), "First vote should succeed");
        assertEquals(VoteResult.ADDED, session.vote(player1, game2), "Second vote should succeed");
        assertEquals(VoteResult.ADDED, session.vote(player1, game3), "Third vote should succeed");

        // Try to cast 4th vote (should fail)
        VoteResult fourthVote = session.vote(player1, game4);
        assertEquals(VoteResult.LIMIT_REACHED, fourthVote, "Fourth vote should be rejected");

        assertEquals(3, session.getPlayerVoteCount(player1), "Player should have exactly 3 votes");
    }

    @Test
    @DisplayName("Should allow voting again after removing a vote")
    void testVoteAfterRemoval() {
        session.startVoting();

        // Cast 3 votes
        session.vote(player1, game1);
        session.vote(player1, game2);
        session.vote(player1, game3);

        // Remove one vote
        session.vote(player1, game1);  // Toggle off
        assertEquals(2, session.getPlayerVoteCount(player1), "Player should have 2 votes after removal");

        // Should be able to vote for game4 now
        VoteResult result = session.vote(player1, game4);
        assertEquals(VoteResult.ADDED, result, "Should be able to vote after removing one");
        assertEquals(3, session.getPlayerVoteCount(player1), "Player should have 3 votes again");
    }

    @Test
    @DisplayName("Should track votes correctly for multiple players")
    void testMultiplePlayerVoting() {
        session.startVoting();

        session.vote(player1, game1);
        session.vote(player2, game1);
        session.vote(player2, game2);

        assertEquals(2, session.getVoteCount("game1"), "Game1 should have 2 votes");
        assertEquals(1, session.getVoteCount("game2"), "Game2 should have 1 vote");
        assertEquals(1, session.getPlayerVoteCount(player1), "Player1 should have 1 vote");
        assertEquals(2, session.getPlayerVoteCount(player2), "Player2 should have 2 votes");
    }

    @Test
    @DisplayName("Should return player votes correctly")
    void testGetPlayerVotes() {
        session.startVoting();

        session.vote(player1, game1);
        session.vote(player1, game2);

        Set<String> votes = session.getPlayerVotes(player1);
        assertEquals(2, votes.size(), "Should return 2 votes");
        assertTrue(votes.contains("game1"), "Should contain game1");
        assertTrue(votes.contains("game2"), "Should contain game2");
    }

    @Test
    @DisplayName("Should return empty set when player has no votes")
    void testGetPlayerVotesEmpty() {
        session.startVoting();
        Set<String> votes = session.getPlayerVotes(player1);
        assertNotNull(votes, "Should return non-null set");
        assertTrue(votes.isEmpty(), "Should return empty set");
    }

    @Test
    @DisplayName("Should stop voting and return results sorted by count")
    void testStopVoting() {
        session.startVoting();

        // Create voting scenario
        session.vote(player1, game1);
        session.vote(player2, game1);
        session.vote(player1, game2);

        Map<String, Integer> results = session.stopVoting();

        assertFalse(session.isActive(), "Session should not be active after stopping");
        assertEquals(2, results.get("game1"), "Game1 should have 2 votes");
        assertEquals(1, results.get("game2"), "Game2 should have 1 vote");

        // Verify results are sorted by count (descending)
        var iterator = results.entrySet().iterator();
        var first = iterator.next();
        var second = iterator.next();
        assertTrue(first.getValue() >= second.getValue(), "Results should be sorted by count descending");
    }

    @Test
    @DisplayName("Should get winner correctly")
    void testGetWinner() {
        session.startVoting();

        session.vote(player1, game1);
        session.vote(player2, game1);
        session.vote(player1, game2);

        String winner = session.getWinner();
        assertEquals("game1", winner, "Game1 should be the winner with most votes");
    }

    @Test
    @DisplayName("Should return null winner when no votes")
    void testGetWinnerNoVotes() {
        session.startVoting();
        String winner = session.getWinner();
        assertNull(winner, "Should return null when no votes");
    }

    @Test
    @DisplayName("Should calculate total vote count correctly")
    void testGetTotalVoteCount() {
        session.startVoting();

        session.vote(player1, game1);
        session.vote(player2, game1);
        session.vote(player1, game2);
        session.vote(player2, game2);

        assertEquals(4, session.getTotalVoteCount(), "Total vote count should be 4");
    }

    @Test
    @DisplayName("Should clear all data")
    void testClear() {
        session.startVoting();
        session.vote(player1, game1);
        session.vote(player2, game2);

        session.clear();

        assertFalse(session.isActive(), "Should not be active after clear");
        assertEquals(0, session.getTotalVoteCount(), "Should have no votes after clear");
        assertEquals(0, session.getPlayerVoteCount(player1), "Player1 should have no votes after clear");
    }

    @Test
    @DisplayName("Should handle ready phase correctly")
    void testReadyPhase() {
        session.startVoting();
        session.stopVoting();
        session.startReadyPhase();

        assertTrue(session.isReadyPhase(), "Should be in ready phase");
        assertFalse(session.isActive(), "Should not be active during ready phase");
    }

    @Test
    @DisplayName("Should track ready players")
    void testReadyPlayers() {
        session.startReadyPhase();

        session.markPlayerReady(player1.getUniqueId());
        assertTrue(session.isPlayerReady(player1.getUniqueId()), "Player1 should be marked as ready");
        assertFalse(session.isPlayerReady(player2.getUniqueId()), "Player2 should not be ready");

        assertEquals(1, session.getReadyCount(), "Should have 1 ready player");
    }

    @Test
    @DisplayName("Should handle pre-voting ready phase")
    void testPreVotingReadyPhase() {
        session.startPreVotingReady();

        assertTrue(session.isPreVotingReady(), "Should be in pre-voting ready phase");
        assertFalse(session.isActive(), "Should not be active during pre-voting ready phase");
    }

    @Test
    @DisplayName("Should track pre-voting ready players")
    void testPreVotingReadyPlayers() {
        session.startPreVotingReady();

        session.markPreVotingReady(player1.getUniqueId());
        assertTrue(session.isPreVotingPlayerReady(player1.getUniqueId()), "Player1 should be marked as pre-voting ready");
        assertFalse(session.isPreVotingPlayerReady(player2.getUniqueId()), "Player2 should not be pre-voting ready");
    }
}
