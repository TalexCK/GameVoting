package com.talexck.gameVoting.utils.hologram;

import com.talexck.gameVoting.api.hologram.HologramProvider;
import com.talexck.gameVoting.utils.ColorUtil;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DecentHolograms implementation of the HologramProvider interface.
 * Creates persistent holograms that are saved to DecentHolograms' configuration.
 */
public class DecentHologramsProvider implements HologramProvider {

    private static final String NAMESPACE = "gamevoting_";

    /**
     * Get the full namespaced ID for a hologram.
     *
     * @param id The base hologram ID
     * @return The namespaced ID (gamevoting_<id>)
     */
    private String getNamespacedId(String id) {
        return id.startsWith(NAMESPACE) ? id : NAMESPACE + id;
    }

    /**
     * Colorize all lines using ColorUtil and convert Components to strings.
     *
     * @param lines The lines to colorize
     * @return List of colorized strings
     */
    private List<String> colorizeLines(List<String> lines) {
        return lines.stream()
                .map(line -> ColorUtil.serialize(ColorUtil.colorize(line)))
                .collect(Collectors.toList());
    }

    @Override
    public boolean createHologram(String id, Location location, List<String> lines) {
        if (id == null || location == null || lines == null || lines.isEmpty()) {
            return false;
        }

        try {
            String namespacedId = getNamespacedId(id);

            // Delete existing hologram if present
            if (exists(id)) {
                deleteHologram(id);
            }

            // Colorize lines
            List<String> colorizedLines = colorizeLines(lines);

            // Create hologram using DHAPI
            Hologram hologram = DHAPI.createHologram(namespacedId, location, colorizedLines);

            // Save to config for persistence
            if (hologram != null) {
                hologram.save();
                return true;
            }

            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean updateLines(String id, List<String> lines) {
        if (id == null || lines == null || lines.isEmpty()) {
            return false;
        }

        try {
            String namespacedId = getNamespacedId(id);
            Hologram hologram = DHAPI.getHologram(namespacedId);

            if (hologram == null) {
                return false;
            }

            // Colorize lines
            List<String> colorizedLines = colorizeLines(lines);

            // Update hologram lines
            DHAPI.setHologramLines(hologram, colorizedLines);

            // Save changes to config
            hologram.save();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean deleteHologram(String id) {
        if (id == null) {
            return false;
        }

        try {
            String namespacedId = getNamespacedId(id);
            Hologram hologram = DHAPI.getHologram(namespacedId);

            if (hologram == null) {
                return false;
            }

            // Delete hologram (removes from config too)
            hologram.delete();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean exists(String id) {
        if (id == null) {
            return false;
        }

        try {
            String namespacedId = getNamespacedId(id);
            return DHAPI.getHologram(namespacedId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int deleteAll() {
        try {
            // Get all holograms by iterating through possible IDs
            // Since we namespace our holograms, we can track them if needed
            // For now, this is a placeholder that returns 0
            // In practice, you'd maintain a list of created hologram IDs
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // Try to access DHAPI to verify DecentHolograms is loaded
            // Use a simple method that exists in the API
            DHAPI.getHologram("test_availability_check");
            return true;
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }
}
