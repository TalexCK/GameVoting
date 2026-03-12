package com.talexck.gameVoting.config;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameConfigTest {

    @Test
    @DisplayName("未配置人数范围时应使用默认值")
    void shouldUseDefaultPlayerRange() {
        GameConfig config = new GameConfig(
            "bedwars",
            "BedWars",
            List.of("desc"),
            Material.RED_BED,
            0,
            "BedWars"
        );

        assertEquals(1, config.getMinPlayers());
        assertEquals(50, config.getMaxPlayers());
        assertTrue(config.isAvailableForPlayerCount(1));
        assertTrue(config.isAvailableForPlayerCount(50));
        assertFalse(config.isAvailableForPlayerCount(51));
    }

    @Test
    @DisplayName("应正确判断自定义人数范围")
    void shouldRespectCustomPlayerRange() {
        GameConfig config = new GameConfig(
            "skywars",
            "SkyWars",
            List.of("desc"),
            Material.GRASS_BLOCK,
            0,
            "SkyWars",
            "1.20.1",
            true,
            120,
            2,
            8
        );

        assertFalse(config.isAvailableForPlayerCount(1));
        assertTrue(config.isAvailableForPlayerCount(2));
        assertTrue(config.isAvailableForPlayerCount(8));
        assertFalse(config.isAvailableForPlayerCount(9));
    }
}
