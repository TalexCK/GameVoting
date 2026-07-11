package com.talexck.gameVoting.utils.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClientVersionUtilTest {

  @Test
  @DisplayName("应将 775 协议号映射为 26.1 系列")
  void shouldMapProtocol775To26_1() {
    assertEquals("26.1/26.1.1/26.1.2", ClientVersionUtil.mapProtocolToVersion(775));
  }

  @Test
  @DisplayName("应将 776 协议号映射为 26.2")
  void shouldMapProtocol776To26_2() {
    assertEquals("26.2", ClientVersionUtil.mapProtocolToVersion(776));
  }

  @Test
  @DisplayName("应支持 Minecraft 26.1 配置写法")
  void shouldMatchMinecraft26_1Config() {
    assertTrue(ClientVersionUtil.isVersionMatch("26.1", "Minecraft 26.1"));
  }

  @Test
  @DisplayName("应兼容 26.1 与旧补丁号别名")
  void shouldMatchLegacyAliasFor26_1() {
    assertTrue(ClientVersionUtil.isVersionMatch("26.1", "1.21.12"));
    assertTrue(ClientVersionUtil.isVersionMatch("1.21.12", "26.1"));
  }

  @Test
  @DisplayName("应支持 26.1 到 26.2 的准备阶段版本范围")
  void shouldMatchVersionRange() {
    assertTrue(ClientVersionUtil.isVersionInRange("26.1.1", "26.1", "26.2"));
    assertTrue(ClientVersionUtil.isVersionInRange("26.1/26.1.1/26.1.2", "26.1.2", "26.2"));
    assertTrue(ClientVersionUtil.isVersionInRange("26.2", "26.1", "26.2"));
  }

  @Test
  @DisplayName("应将范围版本格式化为 min - max")
  void shouldFormatVersionRangeForDisplay() {
    assertEquals("1.21.11 - 26.2", ClientVersionUtil.formatVersionRange(null, "1.21.11", "26.2"));
  }
}
