package ca.xef5000.quantumcraft;

import ca.xef5000.quantumcraft.commands.QuantumCraftCommand;
import ca.xef5000.quantumcraft.listeners.BlockListener;
import ca.xef5000.quantumcraft.listeners.PlayerListener;
import ca.xef5000.quantumcraft.player.PlayerStateManager;
import ca.xef5000.quantumcraft.protocol.PacketInterceptor;
import ca.xef5000.quantumcraft.region.RegionManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuantumCraft extends JavaPlugin {
    private static QuantumCraft instance;
    private RegionManager regionManager;
    private PlayerStateManager playerStateManager;
    private PacketInterceptor packetInterceptor;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize managers
        regionManager = new RegionManager(this);
        playerStateManager = new PlayerStateManager(this);
        packetInterceptor = new PacketInterceptor(this);

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

        // Initialize player state manager and load player states
        playerStateManager.initialize();

        getLogger().info("QuantumCraft has been enabled!");
        getLogger().info("Quantum superposition system initialized - reality is now optional!");
    }

    @Override
    public void onDisable() {
        // Save all regions
        if (regionManager != null) {
            regionManager.shutdown();
        }

        // Save all player states
        if (playerStateManager != null) {
            playerStateManager.shutdown();
        }

        // Clean up packet interceptor
        if (packetInterceptor != null) {
            packetInterceptor.shutdown();
        }

        getLogger().info("QuantumCraft has been disabled!");
        getLogger().info("Reality has been restored... for now.");
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
}
