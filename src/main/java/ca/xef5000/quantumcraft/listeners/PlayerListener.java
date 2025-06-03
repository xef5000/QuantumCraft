package ca.xef5000.quantumcraft.listeners;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.commands.QuantumCraftCommand;
import ca.xef5000.quantumcraft.region.QuantumRegion;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles player-related events for QuantumCraft.
 */
public class PlayerListener implements Listener {
    private final QuantumCraft plugin;

    // Track which regions each player was last in to detect region changes
    private final Map<UUID, Set<String>> playerRegions = new ConcurrentHashMap<>();

    public PlayerListener(QuantumCraft plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles player interactions with the selection tool.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != QuantumCraftCommand.SELECTION_STICK_MATERIAL) {
            return;
        }

        // Check if it's our selection tool
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (!displayName.contains("QuantumCraft Selection Tool")) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedBlock() == null) {
            return;
        }

        Location clickedLocation = event.getClickedBlock().getLocation();
        UUID playerId = player.getUniqueId();

        // Get the command handler to access position maps
        QuantumCraftCommand commandHandler = getCommandHandler();
        if (commandHandler == null) {
            player.sendMessage(ChatColor.RED + "Error: Could not access command handler");
            return;
        }

        Map<UUID, Location> pos1Map = commandHandler.getPos1Map();
        Map<UUID, Location> pos2Map = commandHandler.getPos2Map();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Set position 1
            pos1Map.put(playerId, clickedLocation);
            player.sendMessage(ChatColor.GREEN + "Position 1 set to: " + 
                formatLocation(clickedLocation));
            
            // Show selection info if both positions are set
            if (pos2Map.containsKey(playerId)) {
                showSelectionInfo(player, clickedLocation, pos2Map.get(playerId));
            }
            
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Set position 2
            pos2Map.put(playerId, clickedLocation);
            player.sendMessage(ChatColor.GREEN + "Position 2 set to: " + 
                formatLocation(clickedLocation));
            
            // Show selection info if both positions are set
            if (pos1Map.containsKey(playerId)) {
                showSelectionInfo(player, pos1Map.get(playerId), clickedLocation);
            }
        }
    }

    /**
     * Handles player join events.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Initialize player in the state manager if needed
        // This ensures the player is properly tracked
        plugin.getLogger().fine("Player " + player.getName() + " joined - initializing quantum state tracking");

        // Delay the region refresh to ensure the player is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Force immediate state check for the joining player
            if (plugin.getAutoStateManager() != null) {
                plugin.getAutoStateManager().forceCheckPlayer(player);
            }

            // Also refresh quantum regions for the player (fallback)
            plugin.getPlayerStateManager().refreshAllRegionsForPlayer(player);

            if (plugin.getConfig().getBoolean("debug.log-regions", false)) {
                plugin.getLogger().info("Refreshed quantum regions for joining player: " + player.getName());
            }
        }, 20L); // 1 second delay
    }

    /**
     * Handles player quit events.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up player data
        plugin.getPlayerStateManager().cleanupPlayer(player);

        // Clean up auto state manager data
        if (plugin.getAutoStateManager() != null) {
            plugin.getAutoStateManager().cleanupPlayer(player);
        }

        // Clean up selection data
        QuantumCraftCommand commandHandler = getCommandHandler();
        UUID playerId = player.getUniqueId();
        if (commandHandler != null) {
            commandHandler.getPos1Map().remove(playerId);
            commandHandler.getPos2Map().remove(playerId);
        }

        // Clean up region tracking
        playerRegions.remove(playerId);

        plugin.getLogger().fine("Player " + player.getName() + " quit - cleaned up quantum state data");
    }

    /**
     * Handles player movement to detect when they enter/exit quantum regions.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if the player moved to a different block
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || (from.getBlockX() == to.getBlockX() &&
                          from.getBlockY() == to.getBlockY() &&
                          from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        checkRegionChanges(event.getPlayer(), to);
    }

    /**
     * Handles player teleportation.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to != null) {
            // Delay the check slightly to ensure the teleport is complete
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                checkRegionChanges(event.getPlayer(), to);
            }, 2L);
        }
    }

    /**
     * Checks if a player has entered or exited quantum regions and refreshes as needed.
     */
    private void checkRegionChanges(Player player, Location location) {
        UUID playerId = player.getUniqueId();

        // Get current regions the player is in
        Set<String> currentRegions = new HashSet<>();
        for (QuantumRegion region : plugin.getRegionManager().getAllRegions()) {
            if (region.contains(location)) {
                currentRegions.add(region.getId());
            }
        }

        // Get previous regions
        Set<String> previousRegions = playerRegions.getOrDefault(playerId, new HashSet<>());

        // Find regions the player entered
        Set<String> enteredRegions = new HashSet<>(currentRegions);
        enteredRegions.removeAll(previousRegions);

        // Find regions the player exited
        Set<String> exitedRegions = new HashSet<>(previousRegions);
        exitedRegions.removeAll(currentRegions);

        // Refresh entered regions
        for (String regionId : enteredRegions) {
            QuantumRegion region = plugin.getRegionManager().getRegion(regionId);
            if (region != null) {
                plugin.getPlayerStateManager().updatePlayerView(player, region);

                if (plugin.getConfig().getBoolean("debug.log-regions", false)) {
                    plugin.getLogger().info("Player " + player.getName() + " entered quantum region: " + region.getName());
                }
            }
        }

        // Update tracking
        playerRegions.put(playerId, currentRegions);
    }

    /**
     * Gets the command handler instance.
     * This is a bit of a hack since we need access to the selection positions.
     */
    private QuantumCraftCommand getCommandHandler() {
        try {
            // The command handler should be registered with the plugin
            return (QuantumCraftCommand) plugin.getCommand("quantumcraft").getExecutor();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not access command handler: " + e.getMessage());
            return null;
        }
    }

    /**
     * Formats a location for display.
     */
    private String formatLocation(Location location) {
        return String.format("(%d, %d, %d)", 
            location.getBlockX(), 
            location.getBlockY(), 
            location.getBlockZ());
    }

    /**
     * Shows selection information to the player.
     */
    private void showSelectionInfo(Player player, Location pos1, Location pos2) {
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        long volume = (long) width * height * depth;
        
        player.sendMessage(ChatColor.YELLOW + "Selection: " + width + "x" + height + "x" + depth + 
            " (" + volume + " blocks)");
        player.sendMessage(ChatColor.YELLOW + "Use /qc create <name> to create a quantum region");
    }
}
