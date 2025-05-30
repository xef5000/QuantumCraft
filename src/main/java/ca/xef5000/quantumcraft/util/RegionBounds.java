package ca.xef5000.quantumcraft.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Utility class for handling region boundaries and coordinate operations.
 */
public class RegionBounds {
    private final World world;
    private final Vector min;
    private final Vector max;

    /**
     * Creates a new RegionBounds instance.
     *
     * @param world The world this region exists in
     * @param min   The minimum coordinates
     * @param max   The maximum coordinates
     */
    public RegionBounds(World world, Vector min, Vector max) {
        this.world = world;
        this.min = new Vector(
            Math.min(min.getX(), max.getX()),
            Math.min(min.getY(), max.getY()),
            Math.min(min.getZ(), max.getZ())
        );
        this.max = new Vector(
            Math.max(min.getX(), max.getX()),
            Math.max(min.getY(), max.getY()),
            Math.max(min.getZ(), max.getZ())
        );
    }

    /**
     * Creates a RegionBounds from two locations.
     *
     * @param loc1 First location
     * @param loc2 Second location
     * @return RegionBounds instance
     */
    public static RegionBounds fromLocations(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            throw new IllegalArgumentException("Locations must be in the same world");
        }
        return new RegionBounds(loc1.getWorld(), loc1.toVector(), loc2.toVector());
    }

    /**
     * Checks if a location is within this region.
     *
     * @param location The location to check
     * @return true if the location is within the region
     */
    public boolean contains(Location location) {
        if (!location.getWorld().equals(world)) {
            return false;
        }
        Vector pos = location.toVector();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() &&
               pos.getY() >= min.getY() && pos.getY() <= max.getY() &&
               pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /**
     * Checks if a block position is within this region.
     *
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @return true if the position is within the region
     */
    public boolean contains(int x, int y, int z) {
        return x >= min.getBlockX() && x <= max.getBlockX() &&
               y >= min.getBlockY() && y <= max.getBlockY() &&
               z >= min.getBlockZ() && z <= max.getBlockZ();
    }

    /**
     * Gets the volume of this region in blocks.
     *
     * @return The volume in blocks
     */
    public long getVolume() {
        return (long) (max.getX() - min.getX() + 1) *
               (long) (max.getY() - min.getY() + 1) *
               (long) (max.getZ() - min.getZ() + 1);
    }

    /**
     * Gets the minimum chunk coordinates.
     *
     * @return Vector containing minimum chunk coordinates
     */
    public Vector getMinChunk() {
        return new Vector(min.getBlockX() >> 4, 0, min.getBlockZ() >> 4);
    }

    /**
     * Gets the maximum chunk coordinates.
     *
     * @return Vector containing maximum chunk coordinates
     */
    public Vector getMaxChunk() {
        return new Vector(max.getBlockX() >> 4, 0, max.getBlockZ() >> 4);
    }

    // Getters
    public World getWorld() { return world; }
    public Vector getMin() { return min.clone(); }
    public Vector getMax() { return max.clone(); }
    
    public int getMinX() { return min.getBlockX(); }
    public int getMinY() { return min.getBlockY(); }
    public int getMinZ() { return min.getBlockZ(); }
    
    public int getMaxX() { return max.getBlockX(); }
    public int getMaxY() { return max.getBlockY(); }
    public int getMaxZ() { return max.getBlockZ(); }

    @Override
    public String toString() {
        return String.format("RegionBounds{world=%s, min=(%d,%d,%d), max=(%d,%d,%d)}",
            world.getName(), getMinX(), getMinY(), getMinZ(), getMaxX(), getMaxY(), getMaxZ());
    }
}
