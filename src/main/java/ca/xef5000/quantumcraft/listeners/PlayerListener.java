package ca.xef5000.quantumcraft.listeners;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.manager.RegionManager;
import ca.xef5000.quantumcraft.protocol.PacketManager;
import ca.xef5000.quantumcraft.region.Region;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles player-related events for the FakeRegion plugin.
 */
public class PlayerListener implements Listener {
    private final QuantumCraft plugin;
    
    // Track the last region a player was in
    private final ConcurrentHashMap<UUID, Set<String>> playerRegions;

    /**
     * Creates a new PlayerListener.
     *
     * @param plugin The plugin instance
     */
    public PlayerListener(QuantumCraft plugin) {
        this.plugin = plugin;
        this.playerRegions = new ConcurrentHashMap<>();
    }

    /**
     * Handles player movement to check for region entry/exit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process if the player has moved to a different block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        Location location = event.getTo();
        
        // Check if the player has entered or exited any regions
        checkRegionChange(player, location);
        
        // Check if the player has moved to a new chunk
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = location.getChunk();
        
        if (fromChunk.getX() != toChunk.getX() || fromChunk.getZ() != toChunk.getZ()) {
            // Player has moved to a new chunk
            PacketManager packetManager = plugin.getPacketManager();
            
            // Send fake blocks for the new chunk if it hasn't been sent already
            if (!packetManager.hasChunkBeenSent(player, toChunk)) {
                packetManager.sendChunkBlocks(player, toChunk);
            }
        }
    }

    /**
     * Handles player teleportation.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location location = event.getTo();
        
        // Check if the player has entered or exited any regions
        checkRegionChange(player, location);
        
        // Send fake blocks for the destination chunk
        Chunk chunk = location.getChunk();
        PacketManager packetManager = plugin.getPacketManager();
        
        if (!packetManager.hasChunkBeenSent(player, chunk)) {
            packetManager.sendChunkBlocks(player, chunk);
        }
    }

    /**
     * Handles player respawning.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location location = event.getRespawnLocation();
        
        // Check if the player has entered or exited any regions
        checkRegionChange(player, location);
        
        // Send fake blocks for the respawn chunk
        Chunk chunk = location.getChunk();
        PacketManager packetManager = plugin.getPacketManager();
        
        if (!packetManager.hasChunkBeenSent(player, chunk)) {
            packetManager.sendChunkBlocks(player, chunk);
        }
    }

    /**
     * Handles player joining the server.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        
        // Initialize the player's region set
        playerRegions.put(player.getUniqueId(), new HashSet<>());
        
        // Check if the player has entered any regions
        checkRegionChange(player, location);
        
        // Send fake blocks for the current chunk
        Chunk chunk = location.getChunk();
        PacketManager packetManager = plugin.getPacketManager();
        
        packetManager.sendChunkBlocks(player, chunk);
    }

    /**
     * Handles player quitting the server.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up the player's region set
        playerRegions.remove(player.getUniqueId());
        
        // Clean up the player's sent chunks
        PacketManager packetManager = plugin.getPacketManager();
        packetManager.clearSentChunks(player);
    }

    /**
     * Handles player changing worlds.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        
        // Reset the player's region set
        playerRegions.put(player.getUniqueId(), new HashSet<>());
        
        // Check if the player has entered any regions in the new world
        checkRegionChange(player, location);
        
        // Send fake blocks for the current chunk in the new world
        Chunk chunk = location.getChunk();
        PacketManager packetManager = plugin.getPacketManager();
        
        packetManager.sendChunkBlocks(player, chunk);
    }

    /**
     * Handles chunk loading.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        
        // Send fake blocks to all players in this chunk
        for (Player player : chunk.getWorld().getPlayers()) {
            if (player.getLocation().getChunk().equals(chunk)) {
                PacketManager packetManager = plugin.getPacketManager();
                
                if (!packetManager.hasChunkBeenSent(player, chunk)) {
                    packetManager.sendChunkBlocks(player, chunk);
                }
            }
        }
    }

    /**
     * Checks if a player has entered or exited any regions.
     *
     * @param player   The player
     * @param location The player's current location
     */
    private void checkRegionChange(Player player, Location location) {
        RegionManager regionManager = plugin.getRegionManager();
        List<Region> regions = regionManager.getRegionsAt(location);
        
        // Get the player's current regions
        Set<String> currentRegions = new HashSet<>();
        for (Region region : regions) {
            currentRegions.add(region.getId());
        }
        
        // Get the player's previous regions
        Set<String> previousRegions = playerRegions.getOrDefault(player.getUniqueId(), new HashSet<>());
        
        // Check for regions the player has entered
        for (String regionId : currentRegions) {
            if (!previousRegions.contains(regionId)) {
                // Player has entered a new region
                handleRegionEnter(player, regionId);
            }
        }
        
        // Check for regions the player has exited
        for (String regionId : previousRegions) {
            if (!currentRegions.contains(regionId)) {
                // Player has exited a region
                handleRegionExit(player, regionId);
            }
        }
        
        // Update the player's regions
        playerRegions.put(player.getUniqueId(), currentRegions);
    }

    /**
     * Handles a player entering a region.
     *
     * @param player   The player
     * @param regionId The ID of the region
     */
    private void handleRegionEnter(Player player, String regionId) {
        // Get the region
        RegionManager regionManager = plugin.getRegionManager();
        Region region = regionManager.getRegion(regionId);
        
        if (region == null) {
            return;
        }
        
        // Get the version the player should see
        String versionName = plugin.getPlayerManager().getPlayerVersion(player, regionId);
        
        if (versionName != null) {
            // Send the region version to the player
            PacketManager packetManager = plugin.getPacketManager();
            packetManager.sendRegionVersion(player, region, region.getVersion(versionName));
        }
    }

    /**
     * Handles a player exiting a region.
     *
     * @param player   The player
     * @param regionId The ID of the region
     */
    private void handleRegionExit(Player player, String regionId) {
        // No special handling needed for now
        // In the future, we might want to restore the original blocks
    }
}