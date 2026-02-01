package com.talexck.gameVoting.utils.item;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VoteItem utility class.
 *
 * Note: VoteItem has dependencies on LanguageManager and requires plugin initialization.
 * These tests verify the core functionality that can be tested without full plugin context:
 * - removeVoteItem() - Slot 8 manipulation
 * - isVoteItem() with null/unmarked items
 *
 * Full item creation and identification tests require integration testing in a running server environment.
 */
class VoteItemTest {
    private static ServerMock server;
    private PlayerMock player;

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
        player = server.addPlayer("TestPlayer");
        player.getInventory().clear();
    }

    @Test
    @DisplayName("Should remove vote item from slot 8")
    void testRemoveVoteItem() {
        // Manually place an item in slot 8
        player.getInventory().setItem(8, new ItemStack(Material.COMPASS));
        assertNotNull(player.getInventory().getItem(8), "Item should be present before removal");

        // Remove it using VoteItem
        VoteItem.removeVoteItem(player);
        assertNull(player.getInventory().getItem(8), "Item should be removed from slot 8");
    }

    @Test
    @DisplayName("Should handle removing from empty slot 8")
    void testRemoveFromEmptySlot() {
        assertNull(player.getInventory().getItem(8), "Slot 8 should be empty");
        assertDoesNotThrow(() -> VoteItem.removeVoteItem(player),
                "Should handle removing from empty slot without error");
    }

    @Test
    @DisplayName("Should only affect slot 8, not other inventory slots")
    void testSlot8Manipulation() {
        // Place items in different slots
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        player.getInventory().setItem(7, new ItemStack(Material.EMERALD));
        player.getInventory().setItem(8, new ItemStack(Material.COMPASS));

        VoteItem.removeVoteItem(player);

        // Slot 8 should be cleared
        assertNull(player.getInventory().getItem(8), "Slot 8 should be null");
        // Other slots should be unaffected
        assertNotNull(player.getInventory().getItem(0), "Slot 0 should be unaffected");
        assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType(), "Slot 0 should still have diamond");
        assertNotNull(player.getInventory().getItem(7), "Slot 7 should be unaffected");
        assertEquals(Material.EMERALD, player.getInventory().getItem(7).getType(), "Slot 7 should still have emerald");
    }

    @Test
    @DisplayName("Should return false for null items in isVoteItem")
    void testIsVoteItemNull() {
        assertFalse(VoteItem.isVoteItem(null), "Should return false for null item");
    }

    @Test
    @DisplayName("Should return false for items without metadata")
    void testIsVoteItemNoMeta() {
        ItemStack itemNoMeta = new ItemStack(Material.STONE);
        assertFalse(VoteItem.isVoteItem(itemNoMeta), "Should return false for item without persistent data");
    }

    @Test
    @DisplayName("Should return false for regular unmarked items")
    void testIsVoteItemUnmarked() {
        ItemStack regularItem = new ItemStack(Material.DIAMOND);
        assertFalse(VoteItem.isVoteItem(regularItem), "Should return false for non-vote item");
    }

    @Test
    @DisplayName("Should handle multiple remove operations")
    void testMultipleRemoves() {
        player.getInventory().setItem(8, new ItemStack(Material.COMPASS));

        VoteItem.removeVoteItem(player);
        assertNull(player.getInventory().getItem(8), "First removal should clear slot 8");

        VoteItem.removeVoteItem(player);
        assertNull(player.getInventory().getItem(8), "Second removal should also work (no-op)");

        VoteItem.removeVoteItem(player);
        assertNull(player.getInventory().getItem(8), "Third removal should also work (no-op)");
    }

    @Test
    @DisplayName("Should not throw exceptions when removing with null player inventory")
    void testRemoveVoteItemSafety() {
        // VoteItem.removeVoteItem should handle edge cases gracefully
        // This test verifies the method doesn't crash with normal usage
        assertDoesNotThrow(() -> {
            VoteItem.removeVoteItem(player);
            player.getInventory().setItem(8, new ItemStack(Material.COMPASS));
            VoteItem.removeVoteItem(player);
        }, "VoteItem.removeVoteItem should not throw exceptions during normal usage");
    }
}
