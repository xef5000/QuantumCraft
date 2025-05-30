package ca.xef5000.quantumcraft.region;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a 3D cuboid region with a unique ID.
 * Each region can have multiple versions of block states.
 */
public class Region implements ConfigurationSerializable {
    private final String id;
    private final World world;
    private final Vector min;
    private final Vector max;
    private final Map<String, RegionVersion> versions;

    /**
     * Creates a new region with the specified ID and boundaries.
     *
     * @param id    The unique ID of the region
     * @param world The world the region is in
     * @param min   The minimum corner of the region
     * @param max   The maximum corner of the region
     */
    public Region(String id, World world, Vector min, Vector max) {
        this.id = id;
        this.world = world;
        this.min = Vector.getMinimum(min, max);
        this.max = Vector.getMaximum(min, max);
        this.versions = new HashMap<>();
    }

    /**
     * Creates a region from a serialized map.
     *
     * @param map The serialized map
     */
    @SuppressWarnings("unchecked")
    public Region(Map<String, Object> map) {
        this.id = (String) map.get("id");
        this.world = UUID.fromString((String) map.get("world")).equals(UUID.fromString("00000000-0000-0000-0000-000000000000")) ? null : org.bukkit.Bukkit.getWorld(UUID.fromString((String) map.get("world")));
        this.min = (Vector) map.get("min");
        this.max = (Vector) map.get("max");
        this.versions = new HashMap<>();
        
        Map<String, Map<String, Object>> versionMap = (Map<String, Map<String, Object>>) map.get("versions");
        if (versionMap != null) {
            for (Map.Entry<String, Map<String, Object>> entry : versionMap.entrySet()) {
                this.versions.put(entry.getKey(), new RegionVersion(entry.getValue()));
            }
        }
    }

    /**
     * Gets the unique ID of the region.
     *
     * @return The region ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the world the region is in.
     *
     * @return The world
     */
    public World getWorld() {
        return world;
    }

    /**
     * Gets the minimum corner of the region.
     *
     * @return The minimum corner
     */
    public Vector getMin() {
        return min.clone();
    }

    /**
     * Gets the maximum corner of the region.
     *
     * @return The maximum corner
     */
    public Vector getMax() {
        return max.clone();
    }

    /**
     * Checks if the specified location is within this region.
     *
     * @param location The location to check
     * @return True if the location is within the region, false otherwise
     */
    public boolean contains(Location location) {
        if (!location.getWorld().equals(world)) {
            return false;
        }
        
        Vector vec = location.toVector();
        return vec.isInAABB(min, max);
    }

    /**
     * Adds a version to this region.
     *
     * @param versionName The name of the version
     * @param version     The version to add
     */
    public void addVersion(String versionName, RegionVersion version) {
        versions.put(versionName, version);
    }

    /**
     * Removes a version from this region.
     *
     * @param versionName The name of the version to remove
     * @return True if the version was removed, false if it didn't exist
     */
    public boolean removeVersion(String versionName) {
        return versions.remove(versionName) != null;
    }

    /**
     * Gets a version by name.
     *
     * @param versionName The name of the version
     * @return The version, or null if it doesn't exist
     */
    public RegionVersion getVersion(String versionName) {
        return versions.get(versionName);
    }

    /**
     * Gets all versions of this region.
     *
     * @return A map of version names to versions
     */
    public Map<String, RegionVersion> getVersions() {
        return new HashMap<>(versions);
    }

    /**
     * Serializes this region to a map.
     *
     * @return The serialized map
     */
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("world", world != null ? world.getUID().toString() : "00000000-0000-0000-0000-000000000000");
        map.put("min", min);
        map.put("max", max);
        
        Map<String, Map<String, Object>> versionMap = new HashMap<>();
        for (Map.Entry<String, RegionVersion> entry : versions.entrySet()) {
            versionMap.put(entry.getKey(), entry.getValue().serialize());
        }
        map.put("versions", versionMap);
        
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Region region = (Region) o;
        return Objects.equals(id, region.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}