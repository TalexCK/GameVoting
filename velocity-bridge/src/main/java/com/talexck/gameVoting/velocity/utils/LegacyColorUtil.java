package com.talexck.gameVoting.velocity.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class LegacyColorUtil {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
        LegacyComponentSerializer.legacyAmpersand();

    private LegacyColorUtil() {
    }

    public static Component colorize(String message) {
        return LEGACY_SERIALIZER.deserialize(message == null ? "" : message);
    }
}
