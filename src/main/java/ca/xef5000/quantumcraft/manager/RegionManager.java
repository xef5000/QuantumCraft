package ca.xef5000.quantumcraft.manager;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.region.Region;
import ca.xef5000.quantumcraft.region.RegionVersion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages all regions and their versions, including persistence.
 */
public class RegionManager {
    private final QuantumCraft plugin;
    private final Map<String, Region> regions;
    private final File regionsFile;

    /**
     * Creates a new RegionManager.
     *
     * @param plugin The plugin instance
     */
    public RegionManager(QuantumCraft plugin) {
        this.plugin = plugin;
        this.regions = new HashMap<>();
        this.regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        
        // Create the data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        // Load regions from file
        loadRegions();
    }

    /**
     * Creates a new region.
     *
     * @param id    The unique ID of the region
     * @param world The world the region is in
     * @param min   The minimum corner of the region
     * @param max   The maximum corner of the region
     * @return True if the region was created, false if a region with that ID already exists
     */
    public boolean createRegion(String id, World world, Vector min, Vector max) {
        if (regions.containsKey(id)) {
            return false;
        }
        
        Region region = new Region(id, world, min, max);
        regions.put(id, region);
        saveRegions();
        return true;
    }

    /**
     * Deletes a region.
     *
     * @param id The ID of the region to delete
     * @return True if the region was deleted, false if it didn't exist
     */
    public boolean deleteRegion(String id) {
        if (!regions.containsKey(id)) {
            return false;
        }
        
        regions.remove(id);
        saveRegions();
        return true;
    }

    /**
     * Gets a region by ID.
     *
     * @param id The ID of the region
     * @return The region, or null if it doesn't exist
     */
    public Region getRegion(String id) {
        return regions.get(id);
    }

    /**
     * Gets all regions.
     *
     * @return A map of region IDs to regions
     */
    public Map<String, Region> getRegions() {
        return new HashMap<>(regions);
    }

    /**
     * Adds a version to a region by taking a snapshot of the current blocks.
     *
     * @param regionId    The ID of the region
     * @param versionName The name of the version
     * @return True if the version was added, false if the region doesn't exist
     */
    public boolean addVersion(String regionId, String versionName) {
        Region region = regions.get(regionId);
        if (region == null) {
            return false;
        }
        
        RegionVersion version = new RegionVersion();
        World world = region.getWorld();
        Vector min = region.getMin();
        Vector max = region.getMax();
        
        // Iterate through all blocks in the region and add them to the version
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();
                    BlockData blockData = block.getBlockData();
                    
                    Location location = new Location(world, x, y, z);
                    version.setBlockState(location, material, blockData);
                }
            }
        }
        
        region.addVersion(versionName, version);
        saveRegions();
        return true;
    }

    /**
     * Removes a version from a region.
     *
     * @param regionId    The ID of the region
     * @param versionName The name of the version
     * @return True if the version was removed, false if the region or version doesn't exist
     */
    public boolean removeVersion(String regionId, String versionName) {
        Region region = regions.get(regionId);
        if (region == null) {
            return false;
        }
        
        boolean result = region.removeVersion(versionName);
        if (result) {
            saveRegions();
        }
        return result;
    }

    /**
     * Gets a region at the specified location.
     *
     * @param location The location to check
     * @return The region at the location, or null if there is none
     */
    public Region getRegionAt(Location location) {
        for (Region region : regions.values()) {
            if (region.contains(location)) {
                return region;
            }
        }
        return null;
    }

    /**
     * Gets all regions that contain the specified location.
     *
     * @param location The location to check
     * @return A list of regions that contain the location
     */
    public List<Region> getRegionsAt(Location location) {
        List<Region> result = new ArrayList<>();
        for (Region region : regions.values()) {
            if (region.contains(location)) {
                result.add(region);
            }
        }
        return result;
    }

    /**
     * Loads regions from the regions.yml file.
     */
    @SuppressWarnings("unchecked")
    private void loadRegions() {
        regions.clear();
        
        if (!regionsFile.exists()) {
            return;
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(regionsFile);
        ConfigurationSection regionsSection = config.getConfigurationSection("regions");
        
        if (regionsSection == null) {
            return;
        }
        
        for (String regionId : regionsSection.getKeys(false)) {
            try {
                Map<String, Object> regionMap = (Map<String, Object>) regionsSection.get(regionId);
                Region region = new Region(regionMap);
                regions.put(regionId, region);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load region: " + regionId, e);
            }
        }
        
        plugin.getLogger().info("Loaded " + regions.size() + " regions");
    }

    /**
     * Saves regions to the regions.yml file.
     */
    public void saveRegions() {
        YamlConfiguration config = new YamlConfiguration();
        
        Map<String, Object> serializedRegions = new HashMap<>();
        for (Region region : regions.values()) {
            serializedRegions.put(region.getId(), region.serialize());
        }
        
        config.set("regions", serializedRegions);
        
        try {
            config.save(regionsFile);
            plugin.getLogger().info("Saved " + regions.size() + " regions");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save regions", e);
        }
    }
}