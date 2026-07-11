package com.talexck.gameVoting.utils.version;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Player;

/** Client version detection and matching utility. */
public final class ClientVersionUtil {

  private ClientVersionUtil() {}

  /**
   * Detect player client version text.
   *
   * @param player target player
   * @return version text, null if unavailable
   */
  public static String detectPlayerVersion(Player player) {
    Integer protocol = readProtocolFromPaper(player);
    if (protocol == null) {
      protocol = readProtocolFromViaVersion(player);
    }
    if (protocol == null) {
      return null;
    }
    return mapProtocolToVersion(protocol);
  }

  /**
   * Check if player version matches expected version. Supports values like 1.20, 1.20.1, 1.20.x,
   * 26.1, Minecraft 26.1, any, *.
   *
   * @param playerVersion detected player version
   * @param expectedVersion configured expected version
   * @return true if matches
   */
  public static boolean isVersionMatch(String playerVersion, String expectedVersion) {
    if (playerVersion == null) {
      return false;
    }

    if (isAnyVersion(expectedVersion)) {
      return true;
    }

    String expected = expectedVersion.trim().toLowerCase(Locale.ROOT);
    String[] actualVariants = playerVersion.toLowerCase(Locale.ROOT).split("/");
    String[] expectedVariants = expected.split("/");
    for (String expectedVariant : expectedVariants) {
      Set<String> normalizedExpectedVariants = expandVersionAliases(expectedVariant);
      for (String actualVariant : actualVariants) {
        Set<String> normalizedActualVariants = expandVersionAliases(actualVariant);
        for (String normalizedExpected : normalizedExpectedVariants) {
          for (String normalizedActual : normalizedActualVariants) {
            if (matchesSingle(normalizedActual, normalizedExpected)) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  public static boolean isVersionInRange(
      String playerVersion, String minVersion, String maxVersion) {
    if (playerVersion == null) {
      return false;
    }
    if (isAnyVersion(minVersion) && isAnyVersion(maxVersion)) {
      return true;
    }

    VersionNumber min = bestComparableVersion(minVersion);
    VersionNumber max = bestComparableVersion(maxVersion);
    if (min != null && max != null && min.compareTo(max) > 0) {
      VersionNumber temp = min;
      min = max;
      max = temp;
    }

    for (String actualVariant : playerVersion.toLowerCase(Locale.ROOT).split("/")) {
      for (String actualAlias : expandVersionAliases(actualVariant)) {
        VersionNumber actual = VersionNumber.parse(actualAlias);
        if (actual == null) {
          continue;
        }
        boolean aboveMin = min == null || actual.compareTo(min) >= 0;
        boolean belowMax = max == null || actual.compareTo(max) <= 0;
        if (aboveMin && belowMax) {
          return true;
        }
      }
    }

    return false;
  }

  public static String formatVersionRange(
      String exactVersion, String minVersion, String maxVersion) {
    if (!isAnyVersion(minVersion) || !isAnyVersion(maxVersion)) {
      if (isAnyVersion(minVersion)) {
        return "<= " + normalizeVersionToken(maxVersion);
      }
      if (isAnyVersion(maxVersion)) {
        return ">= " + normalizeVersionToken(minVersion);
      }
      return normalizeVersionToken(minVersion) + " - " + normalizeVersionToken(maxVersion);
    }
    return exactVersion;
  }

  static Set<String> expandVersionAliases(String rawVersion) {
    LinkedHashSet<String> aliases = new LinkedHashSet<>();
    String normalized = normalizeVersionToken(rawVersion);
    if (normalized.isEmpty()) {
      return aliases;
    }

    aliases.add(normalized);

    if ("26.1".equals(normalized)) {
      aliases.add("1.21.12");
      aliases.add("26.1.1");
      aliases.add("26.1.2");
    } else if ("26.1.1".equals(normalized) || "26.1.2".equals(normalized)) {
      aliases.add("26.1");
      aliases.add("1.21.12");
    } else if ("1.21.12".equals(normalized)) {
      aliases.add("26.1");
      aliases.add("26.1.1");
      aliases.add("26.1.2");
    }

    return aliases;
  }

  static String normalizeVersionToken(String rawVersion) {
    if (rawVersion == null) {
      return "";
    }

    String normalized = rawVersion.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("minecraft")) {
      normalized = normalized.substring("minecraft".length()).trim();
    } else if (normalized.startsWith("mc")) {
      normalized = normalized.substring("mc".length()).trim();
    }

    if (normalized.startsWith("v")) {
      normalized = normalized.substring(1).trim();
    }

    return normalized;
  }

  private static boolean isAnyVersion(String version) {
    if (version == null || version.trim().isEmpty()) {
      return true;
    }

    String normalized = version.trim().toLowerCase(Locale.ROOT);
    return "any".equals(normalized) || "*".equals(normalized);
  }

  private static VersionNumber bestComparableVersion(String version) {
    if (isAnyVersion(version)) {
      return null;
    }

    String normalized = normalizeVersionToken(version);
    if ("1.21.12".equals(normalized)) {
      normalized = "26.1";
    }
    return VersionNumber.parse(normalized);
  }

  private static boolean matchesSingle(String actual, String expected) {
    if (actual.equals(expected)) {
      return true;
    }

    if (expected.endsWith(".x")) {
      String prefix = expected.substring(0, expected.length() - 2);
      return actual.equals(prefix) || actual.startsWith(prefix + ".");
    }

    if (expected.endsWith(".*")) {
      String prefix = expected.substring(0, expected.length() - 2);
      return actual.equals(prefix) || actual.startsWith(prefix + ".");
    }

    // 允许 1.20 匹配 1.20.1
    return actual.startsWith(expected + ".");
  }

  private static Integer readProtocolFromPaper(Player player) {
    try {
      Method method = player.getClass().getMethod("getProtocolVersion");
      Object value = method.invoke(player);
      if (value instanceof Integer protocol) {
        return protocol;
      }
    } catch (ReflectiveOperationException ignored) {
    }
    return null;
  }

  private static Integer readProtocolFromViaVersion(Player player) {
    try {
      Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
      Object api = viaClass.getMethod("getAPI").invoke(null);
      Method method = api.getClass().getMethod("getPlayerVersion", java.util.UUID.class);
      Object value = method.invoke(api, player.getUniqueId());
      if (value instanceof Integer protocol) {
        return protocol;
      }
    } catch (ReflectiveOperationException ignored) {
    }
    return null;
  }

  static String mapProtocolToVersion(int protocol) {
    return switch (protocol) {
      case 47 -> "1.8";
      case 107 -> "1.9";
      case 108 -> "1.9.1";
      case 109 -> "1.9.2";
      case 110 -> "1.9.4";
      case 210 -> "1.10";
      case 315 -> "1.11";
      case 316 -> "1.11.1/1.11.2";
      case 335 -> "1.12";
      case 338 -> "1.12.1";
      case 340 -> "1.12.2";
      case 393 -> "1.13";
      case 401 -> "1.13.1";
      case 404 -> "1.13.2";
      case 477 -> "1.14";
      case 480 -> "1.14.1";
      case 485 -> "1.14.2";
      case 490 -> "1.14.3";
      case 498 -> "1.14.4";
      case 573 -> "1.15";
      case 575 -> "1.15.1";
      case 578 -> "1.15.2";
      case 735 -> "1.16";
      case 736 -> "1.16.1";
      case 751 -> "1.16.2";
      case 753 -> "1.16.3";
      case 754 -> "1.16.4/1.16.5";
      case 755 -> "1.17";
      case 756 -> "1.17.1";
      case 757 -> "1.18/1.18.1";
      case 758 -> "1.18.2";
      case 759 -> "1.19";
      case 760 -> "1.19.1/1.19.2";
      case 761 -> "1.19.3";
      case 762 -> "1.19.4";
      case 763 -> "1.20/1.20.1";
      case 764 -> "1.20.2";
      case 765 -> "1.20.3/1.20.4";
      case 766 -> "1.20.5/1.20.6";
      case 767 -> "1.21/1.21.1";
      case 768 -> "1.21.2/1.21.3";
      case 769 -> "1.21.4";
      case 770 -> "1.21.5";
      case 771 -> "1.21.6";
      case 772 -> "1.21.7/1.21.8";
      case 773 -> "1.21.9/1.21.10";
      case 774 -> "1.21.11";
      case 775 -> "26.1/26.1.1/26.1.2";
      case 776 -> "26.2";
      default -> null;
    };
  }

  private record VersionNumber(int[] parts) implements Comparable<VersionNumber> {
    private static VersionNumber parse(String rawVersion) {
      String normalized = normalizeVersionToken(rawVersion);
      if (normalized.isEmpty()) {
        return null;
      }

      String[] tokens = normalized.split("\\.");
      int[] parts = new int[tokens.length];
      for (int i = 0; i < tokens.length; i++) {
        try {
          parts[i] = Integer.parseInt(tokens[i]);
        } catch (NumberFormatException e) {
          return null;
        }
      }
      return new VersionNumber(parts);
    }

    @Override
    public int compareTo(VersionNumber other) {
      int maxLength = Math.max(parts.length, other.parts.length);
      for (int i = 0; i < maxLength; i++) {
        int left = i < parts.length ? parts[i] : 0;
        int right = i < other.parts.length ? other.parts[i] : 0;
        int result = Integer.compare(left, right);
        if (result != 0) {
          return result;
        }
      }
      return 0;
    }
  }
}
