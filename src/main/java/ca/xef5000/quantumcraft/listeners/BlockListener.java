package ca.xef5000.quantumcraft.listeners;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.region.QuantumRegion;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;

/**
 * Handles block-related events in quantum regions.
 * Prevents modifications when players are not in reality mode.
 */
public class BlockListener implements Listener {
    private final QuantumCraft plugin;

    public BlockListener(QuantumCraft plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles block place events.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        // Check if the block is being placed in a quantum region
        List<QuantumRegion> regions = plugin.getRegionManager().getRegionsAt(location);
        if (regions.isEmpty()) {
            return; // Not in a quantum region, allow normal placement
        }

        // Check each region the block is in
        for (QuantumRegion region : regions) {
            if (!plugin.getPlayerStateManager().isPlayerInReality(player, region)) {
                // Player is not in reality mode for this region
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot modify blocks in quantum region '" + 
                    region.getName() + "' while viewing a quantum state!");
                player.sendMessage(ChatColor.YELLOW + "Use /qc reality " + region.getName() + 
                    " to enter reality mode and modify blocks.");
                return;
            }
        }

        // If we get here, the player is in reality mode for all relevant regions
        // Log the modification for debugging
        if (plugin.getConfig().getBoolean("debug.log-regions", false)) {
            plugin.getLogger().info("Player " + player.getName() + " placed block in quantum region(s): " +
                regions.stream().map(QuantumRegion::getName).reduce((a, b) -> a + ", " + b).orElse(""));
        }
    }

    /**
     * Handles block break events.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        // Check if the block is being broken in a quantum region
        List<QuantumRegion> regions = plugin.getRegionManager().getRegionsAt(location);
        if (regions.isEmpty()) {
            return; // Not in a quantum region, allow normal breaking
        }

        // Check each region the block is in
        for (QuantumRegion region : regions) {
            if (!plugin.getPlayerStateManager().isPlayerInReality(player, region)) {
                // Player is not in reality mode for this region
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot modify blocks in quantum region '" + 
                    region.getName() + "' while viewing a quantum state!");
                player.sendMessage(ChatColor.YELLOW + "Use /qc reality " + region.getName() + 
                    " to enter reality mode and modify blocks.");
                return;
            }
        }

        // If we get here, the player is in reality mode for all relevant regions
        // Log the modification for debugging
        if (plugin.getConfig().getBoolean("debug.log-regions", false)) {
            plugin.getLogger().info("Player " + player.getName() + " broke block in quantum region(s): " +
                regions.stream().map(QuantumRegion::getName).reduce((a, b) -> a + ", " + b).orElse(""));
        }
    }
}
