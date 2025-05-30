package ca.xef5000.quantumcraft.manager;

import ca.xef5000.quantumcraft.QuantumCraft;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player-specific region version assignments.
 */
public class PlayerManager {
    private final QuantumCraft plugin;
    private final File playerDataFile;
    
    // Map of player UUID -> (Map of regionId -> versionName)
    private final Map<UUID, Map<String, String>> playerVersions;

    /**
     * Creates a new PlayerManager.
     *
     * @param plugin The plugin instance
     */
    public PlayerManager(QuantumCraft plugin) {
        this.plugin = plugin;
        this.playerDataFile = new File(plugin.getDataFolder(), "player_data.yml");
        this.playerVersions = new ConcurrentHashMap<>();
        
        // Create the data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        // Load player data from file
        loadPlayerData();
    }

    /**
     * Sets the version of a region that a player should see.
     *
     * @param player     The player
     * @param regionId   The ID of the region
     * @param versionName The name of the version
     */
    public void setPlayerVersion(Player player, String regionId, String versionName) {
        UUID playerId = player.getUniqueId();
        
        // Get or create the player's version map
        Map<String, String> versions = playerVersions.computeIfAbsent(playerId, k -> new HashMap<>());
        
        // Set the version for the region
        if (versionName == null) {
            versions.remove(regionId);
        } else {
            versions.put(regionId, versionName);
        }
        
        // Save the player data
        savePlayerData();
    }

    /**
     * Gets the version of a region that a player should see.
     * This checks both manual selections and permissions.
     *
     * @param player   The player
     * @param regionId The ID of the region
     * @return The version name, or null if the player should see the default world
     */
    public String getPlayerVersion(Player player, String regionId) {
        UUID playerId = player.getUniqueId();
        
        // Check manual selection first (takes precedence)
        Map<String, String> versions = playerVersions.get(playerId);
        if (versions != null && versions.containsKey(regionId)) {
            return versions.get(regionId);
        }
        
        // Check permissions
        return getVersionFromPermissions(player, regionId);
    }

    /**
     * Checks if a player has a permission-based version assignment for a region.
     *
     * @param player   The player
     * @param regionId The ID of the region
     * @return The version name from permissions, or null if none
     */
    private String getVersionFromPermissions(Player player, String regionId) {
        // Get all versions from the RegionManager
        RegionManager regionManager = plugin.getRegionManager();
        if (regionManager == null) {
            return null;
        }
        
        // Get the region
        var region = regionManager.getRegion(regionId);
        if (region == null) {
            return null;
        }
        
        // Check permissions for each version
        for (String versionName : region.getVersions().keySet()) {
            String permissionNode = "fr.view." + regionId + "." + versionName;
            if (player.hasPermission(permissionNode)) {
                return versionName;
            }
        }
        
        return null;
    }

    /**
     * Clears all version assignments for a player.
     *
     * @param player The player
     */
    public void clearPlayerVersions(Player player) {
        UUID playerId = player.getUniqueId();
        playerVersions.remove(playerId);
        savePlayerData();
    }

    /**
     * Loads player data from the player_data.yml file.
     */
    private void loadPlayerData() {
        playerVersions.clear();
        
        if (!playerDataFile.exists()) {
            return;
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerDataFile);
        
        for (String uuidString : config.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(uuidString);
                Map<String, String> versions = new HashMap<>();
                
                // Load the player's version assignments
                org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection(uuidString);
                if (section != null) {
                    Map<String, Object> versionMap = section.getValues(false);
                    // section.getValues(false) returns an empty map if no keys, so no need for null check
                    for (Map.Entry<String, Object> entry : versionMap.entrySet()) {
                        versions.put(entry.getKey(), entry.getValue().toString());
                    }
                }
                
                playerVersions.put(playerId, versions);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid UUID in player data: " + uuidString, e);
            }
        }
        
        plugin.getLogger().info("Loaded data for " + playerVersions.size() + " players");
    }

    /**
     * Saves player data to the player_data.yml file.
     */
    public void savePlayerData() {
        YamlConfiguration config = new YamlConfiguration();
        
        for (Map.Entry<UUID, Map<String, String>> entry : playerVersions.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        
        try {
            config.save(playerDataFile);
            plugin.getLogger().info("Saved data for " + playerVersions.size() + " players");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data", e);
        }
    }
}

