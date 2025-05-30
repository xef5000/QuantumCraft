package ca.xef5000.quantumcraft.region;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.storage.StateStorage;
import ca.xef5000.quantumcraft.util.RegionBounds;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manages all quantum regions in the plugin.
 * Handles creation, deletion, loading, and saving of regions.
 */
public class RegionManager {
    private final QuantumCraft plugin;
    private final StateStorage storage;
    private final Map<String, QuantumRegion> regions;
    private final Map<String, QuantumRegion> regionsByName;
    private final Timer autoSaveTimer;

    /**
     * Creates a new RegionManager.
     *
     * @param plugin The plugin instance
     */
    public RegionManager(QuantumCraft plugin) {
        this.plugin = plugin;
        this.regions = new ConcurrentHashMap<>();
        this.regionsByName = new ConcurrentHashMap<>();
        
        // Initialize storage
        FileConfiguration config = plugin.getConfig();
        File dataDir = new File(plugin.getDataFolder(), config.getString("storage.data-directory", "regions"));
        boolean compression = config.getBoolean("storage.enable-compression", true);
        this.storage = new StateStorage(dataDir, compression, plugin.getLogger());
        
        // Setup auto-save timer
        int autoSaveInterval = config.getInt("storage.auto-save-interval", 5);
        if (autoSaveInterval > 0) {
            this.autoSaveTimer = new Timer("QuantumCraft-AutoSave", true);
            this.autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    saveAllRegions();
                }
            }, autoSaveInterval * 60000L, autoSaveInterval * 60000L);
        } else {
            this.autoSaveTimer = null;
        }
    }

    /**
     * Creates a new quantum region.
     *
     * @param name  The name of the region
     * @param world The world the region is in
     * @param min   The minimum coordinates
     * @param max   The maximum coordinates
     * @return The created QuantumRegion
     * @throws IllegalArgumentException If a region with this name already exists
     */
    public QuantumRegion createRegion(String name, World world, Location min, Location max) {
        return createRegion(name, world, min.toVector(), max.toVector());
    }

    /**
     * Creates a new quantum region.
     *
     * @param name  The name of the region
     * @param world The world the region is in
     * @param min   The minimum coordinates
     * @param max   The maximum coordinates
     * @return The created QuantumRegion
     * @throws IllegalArgumentException If a region with this name already exists
     */
    public QuantumRegion createRegion(String name, World world, Vector min, Vector max) {
        if (regionsByName.containsKey(name.toLowerCase())) {
            throw new IllegalArgumentException("A region with the name '" + name + "' already exists");
        }

        String id = generateRegionId();
        RegionBounds bounds = new RegionBounds(world, min, max);
        QuantumRegion region = new QuantumRegion(id, name, bounds);

        // Create default state
        RegionState defaultState = region.createState("default");
        
        // Auto-capture if enabled
        if (plugin.getConfig().getBoolean("defaults.auto-capture-on-create", true)) {
            defaultState.captureCurrentState();
        }

        regions.put(id, region);
        regionsByName.put(name.toLowerCase(), region);

        plugin.getLogger().info("Created quantum region: " + name + " (" + id + ")");
        return region;
    }

    /**
     * Gets a region by its ID.
     *
     * @param regionId The region ID
     * @return The QuantumRegion, or null if not found
     */
    public QuantumRegion getRegion(String regionId) {
        return regions.get(regionId);
    }

    /**
     * Gets a region by its name.
     *
     * @param name The region name
     * @return The QuantumRegion, or null if not found
     */
    public QuantumRegion getRegionByName(String name) {
        return regionsByName.get(name.toLowerCase());
    }

    /**
     * Gets all regions that contain the specified location.
     *
     * @param location The location to check
     * @return A list of regions containing the location
     */
    public List<QuantumRegion> getRegionsAt(Location location) {
        return regions.values().stream()
            .filter(region -> region.contains(location))
            .collect(Collectors.toList());
    }

    /**
     * Gets all regions.
     *
     * @return A collection of all regions
     */
    public Collection<QuantumRegion> getAllRegions() {
        return new ArrayList<>(regions.values());
    }

    /**
     * Deletes a region.
     *
     * @param regionId The ID of the region to delete
     * @return true if the region was deleted successfully
     */
    public boolean deleteRegion(String regionId) {
        QuantumRegion region = regions.get(regionId);
        if (region == null) {
            return false;
        }

        // Remove from memory
        regions.remove(regionId);
        regionsByName.remove(region.getName().toLowerCase());

        // Delete from storage
        boolean deleted = storage.deleteRegion(regionId);
        
        if (deleted) {
            plugin.getLogger().info("Deleted quantum region: " + region.getName() + " (" + regionId + ")");
        }
        
        return deleted;
    }

    /**
     * Creates a new state for a region.
     *
     * @param regionId  The region ID
     * @param stateName The name of the new state
     * @return The created RegionState, or null if the region doesn't exist
     */
    public RegionState createRegionState(String regionId, String stateName) {
        QuantumRegion region = regions.get(regionId);
        if (region == null) {
            return null;
        }

        try {
            return region.createState(stateName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Failed to create state '" + stateName + "' for region " + regionId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves all regions to disk asynchronously.
     */
    public void saveAllRegions() {
        plugin.getLogger().info("Saving all quantum regions...");

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (QuantumRegion region : regions.values()) {
            futures.add(storage.saveRegion(region));
        }

        // Wait for all saves to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> plugin.getLogger().info("Saved " + regions.size() + " quantum regions"))
            .exceptionally(throwable -> {
                plugin.getLogger().severe("Failed to save some regions: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
    }

    /**
     * Saves all regions to disk synchronously.
     */
    public void saveAllRegionsSync() {
        plugin.getLogger().info("Saving all quantum regions synchronously...");

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (QuantumRegion region : regions.values()) {
                futures.add(storage.saveRegion(region));
            }

            // Wait for all saves to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            plugin.getLogger().info("Saved " + regions.size() + " quantum regions synchronously");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save regions synchronously: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads all regions from disk asynchronously.
     */
    public void loadAllRegions() {
        plugin.getLogger().info("Loading quantum regions...");

        storage.loadAllRegions().thenAccept(loadedRegions -> {
            regions.clear();
            regionsByName.clear();

            for (QuantumRegion region : loadedRegions) {
                regions.put(region.getId(), region);
                regionsByName.put(region.getName().toLowerCase(), region);
            }

            plugin.getLogger().info("Loaded " + loadedRegions.size() + " quantum regions");
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Failed to load regions: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });
    }

    /**
     * Loads all regions from disk synchronously.
     * This blocks the current thread until loading is complete.
     */
    public void loadAllRegionsSync() {
        plugin.getLogger().info("Loading quantum regions synchronously...");

        try {
            List<QuantumRegion> loadedRegions = storage.loadAllRegions().get();
            regions.clear();
            regionsByName.clear();

            for (QuantumRegion region : loadedRegions) {
                regions.put(region.getId(), region);
                regionsByName.put(region.getName().toLowerCase(), region);
            }

            plugin.getLogger().info("Loaded " + loadedRegions.size() + " quantum regions synchronously");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load regions synchronously: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets statistics about all regions.
     *
     * @return A map containing various statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRegions", regions.size());
        stats.put("totalStates", regions.values().stream().mapToInt(r -> r.getStates().size()).sum());
        stats.put("totalBlocks", regions.values().stream().mapToInt(QuantumRegion::getTotalBlockCount).sum());
        stats.put("totalMemoryUsage", regions.values().stream().mapToLong(QuantumRegion::getTotalMemoryUsage).sum());
        
        Map<String, Object> regionStats = new HashMap<>();
        for (QuantumRegion region : regions.values()) {
            regionStats.put(region.getName(), region.getStatistics());
        }
        stats.put("regions", regionStats);
        
        return stats;
    }

    /**
     * Shuts down the region manager.
     */
    public void shutdown() {
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
        }
        
        // Save all regions before shutdown synchronously
        saveAllRegionsSync();
    }

    /**
     * Generates a unique region ID.
     *
     * @return A unique region ID
     */
    private String generateRegionId() {
        String id;
        do {
            id = "qr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (regions.containsKey(id));
        return id;
    }

    /**
     * Checks if regions have been loaded from disk.
     *
     * @return true if regions are loaded and ready
     */
    public boolean areRegionsLoaded() {
        return !regions.isEmpty() || hasCheckedForRegions();
    }

    /**
     * Checks if we've attempted to load regions (even if none exist).
     */
    private boolean hasCheckedForRegions() {
        // Check if the data directory exists and has been scanned
        File dataDir = storage.getDataDirectory();
        return dataDir.exists();
    }

    /**
     * Forces a reload of all regions from disk.
     */
    public void reloadAllRegions() {
        plugin.getLogger().info("Reloading all quantum regions...");
        loadAllRegionsSync();
    }

    // Getters
    public StateStorage getStorage() { return storage; }
    public int getRegionCount() { return regions.size(); }
}
