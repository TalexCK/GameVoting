package com.talexck.gameVoting.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ColorUtilTest {

  @Test
  @DisplayName("Should reset formatting after a colored game name")
  void shouldResetFormattingAfterColoredGameName() {
    assertEquals("&b&lBedWars&r", ColorUtil.withReset("&b&lBedWars"));
  }

  @Test
  @DisplayName("Should not duplicate an existing reset")
  void shouldNotDuplicateExistingReset() {
    assertEquals("&bBedWars&r", ColorUtil.withReset("&bBedWars&r"));
  }
}
