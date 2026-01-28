package com.talexck.gameVoting.utils.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represents a hologram location that can be saved to and loaded from config.
 * Format: "world:x:y:z"
 */
public class HologramLocation {
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;

    public HologramLocation(String worldName, double x, double y, double z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public HologramLocation(Location location) {
        this.worldName = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
    }

    /**
     * Convert this hologram location to a Bukkit Location.
     *
     * @return Bukkit Location, or null if world not found
     */
    public Location toBukkitLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    /**
     * Serialize this location to config format.
     * Format: "world:x:y:z"
     *
     * @return String representation
     */
    public String serialize() {
        return String.format("%s:%.2f:%.2f:%.2f", worldName, x, y, z);
    }

    /**
     * Parse a hologram location from config format.
     * Format: "world:x:y:z"
     *
     * @param serialized Serialized location string
     * @return HologramLocation, or null if invalid format
     */
    public static HologramLocation deserialize(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return null;
        }

        String[] parts = serialized.split(":");
        if (parts.length != 4) {
            return null;
        }

        try {
            String worldName = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new HologramLocation(worldName, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    @Override
    public String toString() {
        return serialize();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HologramLocation)) return false;
        HologramLocation other = (HologramLocation) obj;
        return worldName.equals(other.worldName)
            && Math.abs(x - other.x) < 0.01
            && Math.abs(y - other.y) < 0.01
            && Math.abs(z - other.z) < 0.01;
    }

    @Override
    public int hashCode() {
        return serialize().hashCode();
    }
}
