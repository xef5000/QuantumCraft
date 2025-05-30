package ca.xef5000.quantumcraft;

import ca.xef5000.quantumcraft.commands.FakeRegionCommand;
import ca.xef5000.quantumcraft.listeners.BlockListener;
import ca.xef5000.quantumcraft.listeners.PlayerListener;
import ca.xef5000.quantumcraft.manager.PlayerManager;
import ca.xef5000.quantumcraft.manager.RegionManager;
import ca.xef5000.quantumcraft.protocol.PacketManager;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

import ca.xef5000.quantumcraft.region.Region;
import ca.xef5000.quantumcraft.region.RegionVersion;

public final class QuantumCraft extends JavaPlugin {
    private RegionManager regionManager;
    private PlayerManager playerManager;
    private PacketManager packetManager;

    @Override
    public void onEnable() {
        // Register serializable classes
        ConfigurationSerialization.registerClass(Region.class);
        ConfigurationSerialization.registerClass(RegionVersion.class);
        ConfigurationSerialization.registerClass(RegionVersion.BlockState.class);

        // Initialize managers
        this.regionManager = new RegionManager(this);
        this.playerManager = new PlayerManager(this);
        this.packetManager = new PacketManager(this);

        // Register command
        getCommand("fr").setExecutor(new FakeRegionCommand(this));

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);

        getLogger().info("FakeRegion has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save data
        if (regionManager != null) {
            regionManager.saveRegions();
        }

        if (playerManager != null) {
            playerManager.savePlayerData();
        }

        getLogger().info("FakeRegion has been disabled!");
    }

    /**
     * Gets the region manager.
     *
     * @return The region manager
     */
    public RegionManager getRegionManager() {
        return regionManager;
    }

    /**
     * Gets the player manager.
     *
     * @return The player manager
     */
    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    /**
     * Gets the packet manager.
     *
     * @return The packet manager
     */
    public PacketManager getPacketManager() {
        return packetManager;
    }
}
