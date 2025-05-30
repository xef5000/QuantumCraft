package ca.xef5000.quantumcraft.listeners;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.manager.RegionManager;
import ca.xef5000.quantumcraft.protocol.PacketManager;
import ca.xef5000.quantumcraft.region.Region;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;

/**
 * Handles block-related events for the FakeRegion plugin.
 */
public class BlockListener implements Listener {
    private final QuantumCraft plugin;

    /**
     * Creates a new BlockListener.
     *
     * @param plugin The plugin instance
     */
    public BlockListener(QuantumCraft plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles block breaking.
     * This is used to update fake blocks for players when a real block is broken.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        
        // Check if the block is in any regions
        RegionManager regionManager = plugin.getRegionManager();
        List<Region> regions = regionManager.getRegionsAt(location);
        
        if (regions.isEmpty()) {
            return; // Block is not in any region
        }
        
        // For each player, check if they should see a fake block here
        for (Player player : block.getWorld().getPlayers()) {
            // Skip the player who broke the block
            if (player.equals(event.getPlayer())) {
                continue;
            }
            
            // Check each region the block is in
            for (Region region : regions) {
                String versionName = plugin.getPlayerManager().getPlayerVersion(player, region.getId());
                if (versionName != null) {
                    // Player has a version assigned for this region
                    var version = region.getVersion(versionName);
                    if (version != null) {
                        var blockState = version.getBlockState(location);
                        if (blockState != null) {
                            // Send the fake block to the player
                            PacketManager packetManager = plugin.getPacketManager();
                            packetManager.sendBlockChange(player, location, blockState);
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles block placing.
     * This is used to update fake blocks for players when a real block is placed.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        
        // Check if the block is in any regions
        RegionManager regionManager = plugin.getRegionManager();
        List<Region> regions = regionManager.getRegionsAt(location);
        
        if (regions.isEmpty()) {
            return; // Block is not in any region
        }
        
        // For each player, check if they should see a fake block here
        for (Player player : block.getWorld().getPlayers()) {
            // Skip the player who placed the block
            if (player.equals(event.getPlayer())) {
                continue;
            }
            
            // Check each region the block is in
            for (Region region : regions) {
                String versionName = plugin.getPlayerManager().getPlayerVersion(player, region.getId());
                if (versionName != null) {
                    // Player has a version assigned for this region
                    var version = region.getVersion(versionName);
                    if (version != null) {
                        var blockState = version.getBlockState(location);
                        if (blockState != null) {
                            // Send the fake block to the player
                            PacketManager packetManager = plugin.getPacketManager();
                            packetManager.sendBlockChange(player, location, blockState);
                        }
                    }
                }
            }
        }
    }
}