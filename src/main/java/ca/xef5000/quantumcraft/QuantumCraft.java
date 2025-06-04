package ca.xef5000.quantumcraft;

import ca.xef5000.quantumcraft.expansion.QuantumCraftExpansion;
import ca.xef5000.quantumcraft.commands.QuantumCraftCommand;
import ca.xef5000.quantumcraft.config.RegionConfig;
import ca.xef5000.quantumcraft.listeners.BlockListener;
import ca.xef5000.quantumcraft.listeners.PlayerListener;
import ca.xef5000.quantumcraft.manager.AutoStateManager;
import ca.xef5000.quantumcraft.player.PlayerStateManager;
import ca.xef5000.quantumcraft.protocol.PacketInterceptor;
import ca.xef5000.quantumcraft.region.QuantumRegion;
import ca.xef5000.quantumcraft.region.RegionManager;
import ca.xef5000.quantumcraft.region.RegionState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public final class QuantumCraft extends JavaPlugin {
    private static QuantumCraft instance;
    private RegionManager regionManager;
    private PlayerStateManager playerStateManager;
    private PacketInterceptor packetInterceptor;
    private RegionConfig regionConfig;
    private AutoStateManager autoStateManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Save default regions.yml if it doesn't exist
        saveResource("regions.yml", false);

        // Load region configurations
        regionConfig = new RegionConfig(getLogger());
        loadRegionConfigurations();

        // Initialize managers
        regionManager = new RegionManager(this);
        playerStateManager = new PlayerStateManager(this);
        packetInterceptor = new PacketInterceptor(this);
        autoStateManager = new AutoStateManager(this, regionConfig);

        // Register commands
        QuantumCraftCommand commandHandler = new QuantumCraftCommand(this);
        getCommand("quantumcraft").setExecutor(commandHandler);
        getCommand("quantumcraft").setTabCompleter(commandHandler);

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);

        // Initialize packet interceptor
        packetInterceptor.initialize();

        // Load regions from disk synchronously during startup
        // This ensures regions are available before players can join
        regionManager.loadAllRegionsSync();

        // Start automatic state management
        autoStateManager.start();

        // Register PlaceholderAPI expansion
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new QuantumCraftExpansion(this).register();
            getLogger().info("Successfully registered PlaceholderAPI expansion.");
        } else {
            getLogger().info("PlaceholderAPI not found, QuantumCraft placeholders will not be available.");
        }

        getLogger().info("QuantumCraft has been enabled!");
        getLogger().info("Quantum superposition system initialized - reality is now optional!");
        getLogger().info("Automatic state management active - players will see appropriate states automatically!");
    }

    @Override
    public void onDisable() {
        // Stop automatic state management
        if (autoStateManager != null) {
            autoStateManager.stop();
        }

        // Save all regions
        if (regionManager != null) {
            regionManager.shutdown();
        }

        // Clean up packet interceptor
        if (packetInterceptor != null) {
            packetInterceptor.shutdown();
        }

        getLogger().info("QuantumCraft has been disabled!");
        getLogger().info("Reality has been restored... for now.");
    }

    public void debug(String message) {
        if (getConfig().getBoolean("debug.enable-debug", false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    // Getters for managers
    public static QuantumCraft getInstance() {
        return instance;
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }

    public PacketInterceptor getPacketInterceptor() {
        return packetInterceptor;
    }

    public RegionConfig getRegionConfig() {
        return regionConfig;
    }

    public AutoStateManager getAutoStateManager() {
        return autoStateManager;
    }

    /**
     * Loads region configurations from regions.yml
     */
    private void loadRegionConfigurations() {
        File regionsFile = new File(getDataFolder(), "regions.yml");
        if (!regionsFile.exists()) {
            getLogger().warning("regions.yml not found, creating default file");
            return;
        }

        try {
            FileConfiguration regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);
            regionConfig.loadFromConfig(regionsConfig);
            getLogger().info("Loaded region configurations from regions.yml");
        } catch (Exception e) {
            getLogger().severe("Failed to load regions.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reloads region configurations from regions.yml
     */
    public void reloadRegionConfigurations() {
        loadRegionConfigurations();

        // Restart auto state manager with new config
        if (autoStateManager != null) {
            autoStateManager.stop();
            autoStateManager = new AutoStateManager(this, regionConfig);
            autoStateManager.start();
        }

        getLogger().info("Reloaded region configurations");
    }

    /**
     * Saves region configurations to regions.yml
     */
    public void saveRegionConfigurations() {
        File regionsFile = new File(getDataFolder(), "regions.yml");

        try {
            // Load existing file to preserve comments and structure
            FileConfiguration regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);

            // Update with current region configurations
            // This preserves global settings and only updates the regions section
            ConfigurationSection regionsSection = regionsConfig.getConfigurationSection("regions");
            if (regionsSection == null) {
                regionsSection = regionsConfig.createSection("regions");
            }

            // Clear existing regions to ensure removed regions are not kept
            for (String key : regionsSection.getKeys(false)) {
                regionsSection.set(key, null);
            }

            // Add all current regions from RegionManager
            for (QuantumRegion region : regionManager.getAllRegions()) {
                String regionId = region.getId();
                ConfigurationSection regionSection = regionsSection.createSection(regionId);

                // Set required fields
                regionSection.set("world", region.getBounds().getWorld().getName());

                // Set min_point
                ConfigurationSection minPoint = regionSection.createSection("min_point");
                minPoint.set("x", region.getBounds().getMinX());
                minPoint.set("y", region.getBounds().getMinY());
                minPoint.set("z", region.getBounds().getMinZ());

                // Set max_point
                ConfigurationSection maxPoint = regionSection.createSection("max_point");
                maxPoint.set("x", region.getBounds().getMaxX());
                maxPoint.set("y", region.getBounds().getMaxY());
                maxPoint.set("z", region.getBounds().getMaxZ());

                // Set name if different from ID
                if (!region.getName().equals(regionId)) {
                    regionSection.set("name", region.getName());
                }

                // Set default state
                regionSection.set("default_state_id", region.getDefaultStateName());

                // Create states section
                ConfigurationSection statesSection = regionSection.createSection("states");

                RegionConfig.RegionConfigData regionConfigData = regionConfig.getRegionConfig(regionId);

                // If regionConfigData is null, add it to the cache
                if (regionConfigData == null) {
                    getLogger().warning("Region configuration data for " + regionId + " not found in cache. Adding it now.");
                    regionConfig.addRegionConfig(region);
                    regionConfigData = regionConfig.getRegionConfig(regionId);
                }

                getLogger().info("Saving region " + region.getName() + " to regions.yml. It has " + region.getStates().size() + " states.");
                // Add or update all states
                for (RegionState state : region.getStates()) {
                    ConfigurationSection stateSection = statesSection.createSection(state.getName());

                    // Set properties for the state
                    stateSection.set("name", state.getName());

                    // Check if state exists in regionConfigData
                    if (regionConfigData != null && regionConfigData.states.containsKey(state.getName())) {
                        stateSection.set("description", regionConfigData.states.get(state.getName()).description);
                        stateSection.set("icon", regionConfigData.states.get(state.getName()).icon);
                        stateSection.set("priority", regionConfigData.states.get(state.getName()).priority);
                        stateSection.set("unlock_conditions", regionConfigData.states.get(state.getName()).conditions);
                    } else {
                        // Use default values if state not found in regionConfigData
                        stateSection.set("description", "State for " + region.getName());
                        stateSection.set("icon", "minecraft:stone");
                        stateSection.set("priority", 1);
                        stateSection.set("unlock_conditions", new ArrayList<>());
                    }
                }
            }

            // Save the updated configuration
            regionsConfig.save(regionsFile);
            getLogger().info("Saved region configurations to regions.yml");

        } catch (Exception e) {
            getLogger().severe("Failed to save regions.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
