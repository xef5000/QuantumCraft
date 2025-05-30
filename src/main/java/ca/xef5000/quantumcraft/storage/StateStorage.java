package ca.xef5000.quantumcraft.storage;

import ca.xef5000.quantumcraft.region.QuantumRegion;
import ca.xef5000.quantumcraft.region.RegionState;
import ca.xef5000.quantumcraft.util.CompressionUtil;
import ca.xef5000.quantumcraft.util.RegionBounds;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles the storage and retrieval of quantum region data.
 * Uses a custom file format optimized for Minecraft block data.
 */
public class StateStorage {
    private final File dataDirectory;
    private final boolean compressionEnabled;
    private final Logger logger;
    private final Map<String, QuantumRegion> loadedRegions;

    /**
     * Creates a new StateStorage instance.
     *
     * @param dataDirectory     The directory to store region files
     * @param compressionEnabled Whether to enable compression
     * @param logger            Logger for debugging
     */
    public StateStorage(File dataDirectory, boolean compressionEnabled, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.compressionEnabled = compressionEnabled;
        this.logger = logger;
        this.loadedRegions = new ConcurrentHashMap<>();
        
        // Create data directory if it doesn't exist
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
    }

    /**
     * Saves a quantum region to disk.
     *
     * @param region The region to save
     * @return CompletableFuture that completes when saving is done
     */
    public CompletableFuture<Void> saveRegion(QuantumRegion region) {
        return CompletableFuture.runAsync(() -> {
            try {
                File regionFile = getRegionFile(region.getId());
                
                // Compress states if enabled
                if (compressionEnabled) {
                    region.compressAllStates();
                }
                
                // Create region data map
                Map<String, Object> regionData = new HashMap<>();
                regionData.put("id", region.getId());
                regionData.put("name", region.getName());
                regionData.put("defaultState", region.getDefaultStateName());
                regionData.put("createdTime", region.getCreatedTime());
                regionData.put("lastModified", region.getLastModified());
                
                // Save bounds
                RegionBounds bounds = region.getBounds();
                Map<String, Object> boundsData = new HashMap<>();
                boundsData.put("world", bounds.getWorld().getName());
                boundsData.put("minX", bounds.getMinX());
                boundsData.put("minY", bounds.getMinY());
                boundsData.put("minZ", bounds.getMinZ());
                boundsData.put("maxX", bounds.getMaxX());
                boundsData.put("maxY", bounds.getMaxY());
                boundsData.put("maxZ", bounds.getMaxZ());
                regionData.put("bounds", boundsData);
                
                // Save states
                Map<String, Object> statesData = new HashMap<>();
                for (RegionState state : region.getStates()) {
                    Map<String, Object> stateData = new HashMap<>();
                    stateData.put("name", state.getName());
                    stateData.put("lastModified", state.getLastModified());
                    stateData.put("compressed", state.isCompressed());
                    
                    // Save state to separate file
                    File stateFile = getStateFile(region.getId(), state.getName());
                    saveStateData(state, stateFile);
                    
                    statesData.put(state.getName(), stateData);
                }
                regionData.put("states", statesData);
                
                // Write region metadata
                try (FileOutputStream fos = new FileOutputStream(regionFile);
                     ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                    oos.writeObject(regionData);
                }
                
                loadedRegions.put(region.getId(), region);
                logger.info("Saved region: " + region.getName() + " (" + region.getId() + ")");
                
            } catch (Exception e) {
                logger.severe("Failed to save region " + region.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Loads a quantum region from disk.
     *
     * @param regionId The ID of the region to load
     * @return CompletableFuture containing the loaded region, or null if not found
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<QuantumRegion> loadRegion(String regionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File regionFile = getRegionFile(regionId);
                if (!regionFile.exists()) {
                    return null;
                }
                
                // Read region metadata
                Map<String, Object> regionData;
                try (FileInputStream fis = new FileInputStream(regionFile);
                     ObjectInputStream ois = new ObjectInputStream(fis)) {
                    regionData = (Map<String, Object>) ois.readObject();
                }
                
                // Reconstruct bounds
                Map<String, Object> boundsData = (Map<String, Object>) regionData.get("bounds");
                String worldName = (String) boundsData.get("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    logger.warning("World " + worldName + " not found for region " + regionId);
                    return null;
                }
                
                Vector min = new Vector(
                    (Integer) boundsData.get("minX"),
                    (Integer) boundsData.get("minY"),
                    (Integer) boundsData.get("minZ")
                );
                Vector max = new Vector(
                    (Integer) boundsData.get("maxX"),
                    (Integer) boundsData.get("maxY"),
                    (Integer) boundsData.get("maxZ")
                );
                RegionBounds bounds = new RegionBounds(world, min, max);
                
                // Create region
                QuantumRegion region = new QuantumRegion(
                    (String) regionData.get("id"),
                    (String) regionData.get("name"),
                    bounds
                );
                
                // Load states
                Map<String, Object> statesData = (Map<String, Object>) regionData.get("states");
                for (Map.Entry<String, Object> entry : statesData.entrySet()) {
                    String stateName = entry.getKey();
                    Map<String, Object> stateData = (Map<String, Object>) entry.getValue();
                    
                    RegionState state = region.createState(stateName);
                    
                    // Load state data from file
                    File stateFile = getStateFile(regionId, stateName);
                    if (stateFile.exists()) {
                        loadStateData(state, stateFile);
                    }
                }
                
                // Set default state
                String defaultState = (String) regionData.get("defaultState");
                if (defaultState != null && region.getState(defaultState) != null) {
                    region.setDefaultState(defaultState);
                }
                
                loadedRegions.put(regionId, region);
                logger.info("Loaded region: " + region.getName() + " (" + regionId + ")");
                
                return region;
                
            } catch (Exception e) {
                logger.severe("Failed to load region " + regionId + ": " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    /**
     * Loads all regions from disk.
     *
     * @return CompletableFuture containing a list of all loaded regions
     */
    public CompletableFuture<List<QuantumRegion>> loadAllRegions() {
        return CompletableFuture.supplyAsync(() -> {
            List<QuantumRegion> regions = new ArrayList<>();
            
            File[] files = dataDirectory.listFiles((dir, name) -> name.endsWith(".qregion"));
            if (files == null) return regions;
            
            List<CompletableFuture<QuantumRegion>> futures = new ArrayList<>();
            for (File file : files) {
                String regionId = file.getName().replace(".qregion", "");
                futures.add(loadRegion(regionId));
            }
            
            // Wait for all regions to load
            for (CompletableFuture<QuantumRegion> future : futures) {
                try {
                    QuantumRegion region = future.get();
                    if (region != null) {
                        regions.add(region);
                    }
                } catch (Exception e) {
                    logger.severe("Failed to load region: " + e.getMessage());
                }
            }
            
            logger.info("Loaded " + regions.size() + " regions from disk");
            return regions;
        });
    }

    /**
     * Deletes a region from disk.
     *
     * @param regionId The ID of the region to delete
     * @return true if the region was deleted successfully
     */
    public boolean deleteRegion(String regionId) {
        try {
            File regionFile = getRegionFile(regionId);
            boolean deleted = true;
            
            if (regionFile.exists()) {
                deleted = regionFile.delete();
            }
            
            // Delete all state files
            File[] stateFiles = dataDirectory.listFiles((dir, name) -> 
                name.startsWith(regionId + "_") && name.endsWith(".qstate"));
            if (stateFiles != null) {
                for (File stateFile : stateFiles) {
                    if (!stateFile.delete()) {
                        deleted = false;
                    }
                }
            }
            
            loadedRegions.remove(regionId);
            
            if (deleted) {
                logger.info("Deleted region: " + regionId);
            } else {
                logger.warning("Failed to completely delete region: " + regionId);
            }
            
            return deleted;
            
        } catch (Exception e) {
            logger.severe("Failed to delete region " + regionId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the file for a region.
     */
    private File getRegionFile(String regionId) {
        return new File(dataDirectory, regionId + ".qregion");
    }

    /**
     * Gets the file for a region state.
     */
    private File getStateFile(String regionId, String stateName) {
        return new File(dataDirectory, regionId + "_" + stateName + ".qstate");
    }

    /**
     * Saves state data to a file.
     */
    private void saveStateData(RegionState state, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {

            // Save state metadata
            Map<String, Object> stateInfo = new HashMap<>();
            stateInfo.put("name", state.getName());
            stateInfo.put("lastModified", state.getLastModified());
            stateInfo.put("compressed", state.isCompressed());

            // Save block data
            Map<String, String> blockDataMap = new HashMap<>();
            RegionBounds bounds = state.getBounds();

            for (int x = bounds.getMinX(); x <= bounds.getMaxX(); x++) {
                for (int y = bounds.getMinY(); y <= bounds.getMaxY(); y++) {
                    for (int z = bounds.getMinZ(); z <= bounds.getMaxZ(); z++) {
                        org.bukkit.block.data.BlockData blockData = state.getBlockData(x, y, z);
                        if (blockData != null) {
                            String key = x + "," + y + "," + z;
                            blockDataMap.put(key, blockData.getAsString());
                        }
                    }
                }
            }

            stateInfo.put("blockData", blockDataMap);
            oos.writeObject(stateInfo);
        }
    }

    /**
     * Loads state data from a file.
     */
    @SuppressWarnings("unchecked")
    private void loadStateData(RegionState state, File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(fis)) {

            Map<String, Object> stateInfo = (Map<String, Object>) ois.readObject();
            Map<String, String> blockDataMap = (Map<String, String>) stateInfo.get("blockData");

            if (blockDataMap != null) {
                for (Map.Entry<String, String> entry : blockDataMap.entrySet()) {
                    String[] coords = entry.getKey().split(",");
                    int x = Integer.parseInt(coords[0]);
                    int y = Integer.parseInt(coords[1]);
                    int z = Integer.parseInt(coords[2]);

                    try {
                        org.bukkit.block.data.BlockData blockData = org.bukkit.Bukkit.createBlockData(entry.getValue());
                        state.setBlockData(x, y, z, blockData);
                    } catch (Exception e) {
                        logger.warning("Failed to load block data at " + entry.getKey() + ": " + e.getMessage());
                    }
                }
            }

        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize state data", e);
        }
    }

    // Getters
    public File getDataDirectory() { return dataDirectory; }
    public boolean isCompressionEnabled() { return compressionEnabled; }
    public Map<String, QuantumRegion> getLoadedRegions() { return new HashMap<>(loadedRegions); }
}
