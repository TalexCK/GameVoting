package com.talexck.gameVoting.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoteCommandTest {

    @Test
    @DisplayName("Should randomly choose among tied eligible games")
    void testFindEligibleWinnerChoosesFromTopTie() {
        Map<String, Integer> results = new LinkedHashMap<>();
        results.put("game1", 5);
        results.put("game2", 5);
        results.put("game3", 4);

        String winner = VoteCommand.selectRandomWinner(
            results,
            Set.of("game1", "game2", "game3")::contains,
            new FixedRandom(1)
        );

        assertEquals("game2", winner);
    }

    @Test
    @DisplayName("Should ignore unavailable games before random tie-break")
    void testFindEligibleWinnerSkipsUnavailableGames() {
        Map<String, Integer> results = new LinkedHashMap<>();
        results.put("game1", 5);
        results.put("game2", 4);
        results.put("game3", 4);

        String winner = VoteCommand.selectRandomWinner(
            results,
            Set.of("game2", "game3")::contains,
            new FixedRandom(1)
        );

        assertEquals("game3", winner);
    }

    private static final class FixedRandom extends Random {
        private final int fixedIndex;

        private FixedRandom(int fixedIndex) {
            this.fixedIndex = fixedIndex;
        }

        @Override
        public int nextInt(int bound) {
            if (fixedIndex >= bound) {
                throw new AssertionError("固定随机值超出候选范围");
            }
            return fixedIndex;
        }
    }
}
