package com.talexck.gameVoting.utils.hologram;

import com.talexck.gameVoting.api.hologram.HologramProvider;
import org.bukkit.Location;

import java.util.List;

/**
 * No-operation implementation of HologramProvider.
 * Used when DecentHolograms is not available.
 * All operations are no-ops and return false/empty results.
 */
public class NoOpHologramProvider implements HologramProvider {

    @Override
    public boolean createHologram(String id, Location location, List<String> lines) {
        // No-op: DecentHolograms not available
        return false;
    }

    @Override
    public boolean updateLines(String id, List<String> lines) {
        // No-op: DecentHolograms not available
        return false;
    }

    @Override
    public boolean deleteHologram(String id) {
        // No-op: DecentHolograms not available
        return false;
    }

    @Override
    public boolean exists(String id) {
        // No holograms exist if provider is unavailable
        return false;
    }

    @Override
    public int deleteAll() {
        // No holograms to delete
        return 0;
    }

    @Override
    public boolean isAvailable() {
        // This provider is always "available" but does nothing
        return false;
    }
}
