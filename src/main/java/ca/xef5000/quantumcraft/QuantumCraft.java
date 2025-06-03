package ca.xef5000.quantumcraft;

import ca.xef5000.quantumcraft.commands.QuantumCraftCommand;
import ca.xef5000.quantumcraft.config.RegionConfig;
import ca.xef5000.quantumcraft.listeners.BlockListener;
import ca.xef5000.quantumcraft.listeners.PlayerListener;
import ca.xef5000.quantumcraft.manager.AutoStateManager;
import ca.xef5000.quantumcraft.player.PlayerStateManager;
import ca.xef5000.quantumcraft.protocol.PacketInterceptor;
import ca.xef5000.quantumcraft.region.RegionManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

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
}
