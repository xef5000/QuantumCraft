package ca.xef5000.quantumcraft.region;

import ca.xef5000.quantumcraft.util.RegionBounds;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a quantum region that can have multiple states.
 * Each region can contain multiple RegionState objects representing different versions.
 */
public class QuantumRegion {
    private final String id;
    private final String name;
    private final RegionBounds bounds;
    private final Map<String, RegionState> states;
    private String defaultStateName;
    private long createdTime;
    private long lastModified;

    /**
     * Creates a new QuantumRegion.
     *
     * @param id     Unique identifier for this region
     * @param name   Human-readable name for this region
     * @param bounds The bounds of this region
     */
    public QuantumRegion(String id, String name, RegionBounds bounds) {
        this.id = id;
        this.name = name;
        this.bounds = bounds;
        this.states = new ConcurrentHashMap<>();
        this.defaultStateName = "default";
        this.createdTime = System.currentTimeMillis();
        this.lastModified = createdTime;
    }

    /**
     * Creates a new state for this region.
     *
     * @param stateName The name of the new state
     * @return The created RegionState
     * @throws IllegalArgumentException If a state with this name already exists
     */
    public RegionState createState(String stateName) {
        if (states.containsKey(stateName)) {
            throw new IllegalArgumentException("State '" + stateName + "' already exists in region '" + name + "'");
        }

        RegionState state = new RegionState(stateName, bounds);
        states.put(stateName, state);
        this.lastModified = System.currentTimeMillis();
        
        return state;
    }

    /**
     * Gets a state by name.
     *
     * @param stateName The name of the state
     * @return The RegionState, or null if not found
     */
    public RegionState getState(String stateName) {
        return states.get(stateName);
    }

    /**
     * Gets the default state for this region.
     *
     * @return The default RegionState, or null if not set
     */
    public RegionState getDefaultState() {
        return states.get(defaultStateName);
    }

    /**
     * Sets the default state name.
     *
     * @param stateName The name of the state to set as default
     * @throws IllegalArgumentException If the state doesn't exist
     */
    public void setDefaultState(String stateName) {
        if (!states.containsKey(stateName)) {
            throw new IllegalArgumentException("State '" + stateName + "' does not exist in region '" + name + "'");
        }
        this.defaultStateName = stateName;
        this.lastModified = System.currentTimeMillis();
    }

    /**
     * Removes a state from this region.
     *
     * @param stateName The name of the state to remove
     * @return true if the state was removed, false if it didn't exist
     */
    public boolean removeState(String stateName) {
        if (stateName.equals(defaultStateName)) {
            throw new IllegalArgumentException("Cannot remove the default state");
        }
        
        boolean removed = states.remove(stateName) != null;
        if (removed) {
            this.lastModified = System.currentTimeMillis();
        }
        return removed;
    }

    /**
     * Gets all state names in this region.
     *
     * @return A set of state names
     */
    public Set<String> getStateNames() {
        return new HashSet<>(states.keySet());
    }

    /**
     * Gets all states in this region.
     *
     * @return A collection of RegionState objects
     */
    public Collection<RegionState> getStates() {
        return new ArrayList<>(states.values());
    }

    /**
     * Checks if this region contains the specified location.
     *
     * @param location The location to check
     * @return true if the location is within this region
     */
    public boolean contains(Location location) {
        return bounds.contains(location);
    }

    /**
     * Checks if this region contains the specified block coordinates.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return true if the coordinates are within this region
     */
    public boolean contains(int x, int y, int z) {
        return bounds.contains(x, y, z);
    }

    /**
     * Gets the block data at the specified coordinates for a given state.
     *
     * @param stateName The name of the state
     * @param x         X coordinate
     * @param y         Y coordinate
     * @param z         Z coordinate
     * @return The BlockData, or null if not found
     */
    public BlockData getBlockData(String stateName, int x, int y, int z) {
        RegionState state = states.get(stateName);
        if (state == null) {
            return null;
        }
        return state.getBlockData(x, y, z);
    }

    /**
     * Compresses all states in this region to save memory.
     *
     * @throws IOException If compression fails
     */
    public void compressAllStates() throws IOException {
        for (RegionState state : states.values()) {
            state.compress();
        }
    }

    /**
     * Gets the total memory usage of this region.
     *
     * @return Memory usage in bytes
     */
    public long getTotalMemoryUsage() {
        return states.values().stream()
            .mapToLong(RegionState::getMemoryUsage)
            .sum();
    }

    /**
     * Gets the total number of blocks across all states.
     *
     * @return Total block count
     */
    public int getTotalBlockCount() {
        return states.values().stream()
            .mapToInt(RegionState::getBlockCount)
            .sum();
    }

    /**
     * Gets statistics about this region.
     *
     * @return A map containing various statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("id", id);
        stats.put("name", name);
        stats.put("stateCount", states.size());
        stats.put("totalBlocks", getTotalBlockCount());
        stats.put("memoryUsage", getTotalMemoryUsage());
        stats.put("volume", bounds.getVolume());
        stats.put("createdTime", createdTime);
        stats.put("lastModified", lastModified);
        stats.put("defaultState", defaultStateName);
        
        Map<String, Object> stateStats = new HashMap<>();
        for (RegionState state : states.values()) {
            Map<String, Object> stateStat = new HashMap<>();
            stateStat.put("blockCount", state.getBlockCount());
            stateStat.put("memoryUsage", state.getMemoryUsage());
            stateStat.put("compressed", state.isCompressed());
            stateStat.put("lastModified", state.getLastModified());
            stateStats.put(state.getName(), stateStat);
        }
        stats.put("states", stateStats);
        
        return stats;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public RegionBounds getBounds() { return bounds; }
    public String getDefaultStateName() { return defaultStateName; }
    public long getCreatedTime() { return createdTime; }
    public long getLastModified() { return lastModified; }

    @Override
    public String toString() {
        return String.format("QuantumRegion{id='%s', name='%s', states=%d, bounds=%s}",
            id, name, states.size(), bounds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        QuantumRegion that = (QuantumRegion) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
