package ca.xef5000.quantumcraft.protocol;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.region.QuantumRegion;
import ca.xef5000.quantumcraft.region.RegionState;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Logger;

/**
 * Intercepts and modifies packets to show different quantum states to different players.
 * Uses ProtocolLib to modify block-related packets before they reach the client.
 */
public class PacketInterceptor {
    private final QuantumCraft plugin;
    private final ProtocolManager protocolManager;
    private final Logger logger;
    private boolean enabled;

    /**
     * Creates a new PacketInterceptor.
     *
     * @param plugin The plugin instance
     */
    public PacketInterceptor(QuantumCraft plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.logger = plugin.getLogger();
        this.enabled = false;
    }

    /**
     * Initializes the packet interceptor and registers listeners.
     */
    public void initialize() {
        if (enabled) return;

        // Register packet listeners
        registerBlockChangeListener();
        registerMultiBlockChangeListener();
        registerChunkDataListener();
        
        enabled = true;
        logger.info("QuantumCraft packet interceptor initialized");
    }

    /**
     * Shuts down the packet interceptor and unregisters listeners.
     */
    public void shutdown() {
        if (!enabled) return;

        protocolManager.removePacketListeners(plugin);
        enabled = false;
        logger.info("QuantumCraft packet interceptor shut down");
    }

    /**
     * Registers listener for single block change packets.
     */
    private void registerBlockChangeListener() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.BLOCK_CHANGE) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!enabled) return;

                try {
                    Player player = event.getPlayer();
                    BlockPosition position = event.getPacket().getBlockPositionModifier().read(0);
                    
                    Location blockLocation = new Location(
                        player.getWorld(),
                        position.getX(),
                        position.getY(),
                        position.getZ()
                    );

                    // Check if this block is in any quantum region
                    QuantumCraft quantumPlugin = (QuantumCraft) plugin;
                    List<QuantumRegion> regions = quantumPlugin.getRegionManager().getRegionsAt(blockLocation);
                    if (regions.isEmpty()) return;

                    // Use the first region (in case of overlapping regions)
                    QuantumRegion region = regions.get(0);

                    // Check if player is in reality mode
                    if (quantumPlugin.getPlayerStateManager().isPlayerInReality(player, region)) {
                        return; // Let the original packet through
                    }

                    // Get the player's state for this region
                    RegionState state = quantumPlugin.getPlayerStateManager().getPlayerRegionState(player, region);
                    if (state == null) return;

                    // Get the block data from the state
                    BlockData stateBlockData = state.getBlockData(
                        position.getX(),
                        position.getY(),
                        position.getZ()
                    );

                    if (stateBlockData != null) {
                        // Modify the packet to show the state's block data
                        WrappedBlockData wrappedData = WrappedBlockData.createData(stateBlockData);
                        event.getPacket().getBlockData().write(0, wrappedData);
                        
                        if (plugin.getConfig().getBoolean("debug.log-packets", false)) {
                            logger.fine("Modified block change packet for " + player.getName() + 
                                       " at " + position + " to show state: " + state.getName());
                        }
                    }

                } catch (Exception e) {
                    logger.warning("Error processing block change packet: " + e.getMessage());
                    if (plugin.getConfig().getBoolean("debug.enable-debug", false)) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * Registers listener for multi-block change packets.
     */
    private void registerMultiBlockChangeListener() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!enabled) return;

                try {
                    Player player = event.getPlayer();

                    // Get chunk section position from packet
                    // The MULTI_BLOCK_CHANGE packet contains a section position (chunk coordinates)
                    // and a list of block changes relative to that section.
                    if (event.getPacket().getSectionPositions().size() == 0) { // Ensure using .size() == 0
                        if (plugin.getConfig().getBoolean("debug.enable-debug", false)) {
                            logger.warning("MultiBlockChange packet for " + player.getName() + " is missing section position data. Packet details: " + event.getPacket().toString());
                        }
                        return; // Cannot process if section position is missing
                    }
                    BlockPosition sectionPos = event.getPacket().getSectionPositions().read(0);
                    int chunkX = sectionPos.getX(); // This is the X coordinate of the chunk.
                    int chunkZ = sectionPos.getZ(); // This is the Z coordinate of the chunk.

                    // Check if this chunk intersects with any quantum regions
                    boolean hasQuantumRegions = false;
                    QuantumCraft quantumPlugin = (QuantumCraft) plugin;
                    for (QuantumRegion region : quantumPlugin.getRegionManager().getAllRegions()) {
                        if (region.getBounds().getWorld().equals(player.getWorld())) {
                            // Simple check if chunk might intersect with region
                            int chunkMinX = chunkX * 16;
                            int chunkMaxX = chunkMinX + 15;
                            int chunkMinZ = chunkZ * 16;
                            int chunkMaxZ = chunkMinZ + 15;
                            
                            if (chunkMaxX >= region.getBounds().getMinX() && chunkMinX <= region.getBounds().getMaxX() &&
                                chunkMaxZ >= region.getBounds().getMinZ() && chunkMinZ <= region.getBounds().getMaxZ()) {
                                hasQuantumRegions = true;
                                break;
                            }
                        }
                    }

                    if (!hasQuantumRegions) return;

                    // For multi-block changes, we need to process each block individually
                    // This is more complex and would require detailed packet manipulation
                    // For now, we'll log that we detected it
                    if (plugin.getConfig().getBoolean("debug.log-packets", false)) {
                        logger.fine("Detected multi-block change packet in quantum region for " + player.getName());
                    }

                } catch (Exception e) {
                    logger.warning("Error processing multi-block change packet: " + e.getMessage());
                    if (plugin.getConfig().getBoolean("debug.enable-debug", false)) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * Registers listener for chunk data packets.
     */
    private void registerChunkDataListener() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.MAP_CHUNK) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!enabled) return;

                try {
                    Player player = event.getPlayer();
                    
                    // Get chunk coordinates
                    int chunkX = event.getPacket().getIntegers().read(0);
                    int chunkZ = event.getPacket().getIntegers().read(1);
                    
                    // Check if this chunk intersects with any quantum regions
                    boolean hasQuantumRegions = false;
                    QuantumCraft quantumPlugin = (QuantumCraft) plugin;
                    for (QuantumRegion region : quantumPlugin.getRegionManager().getAllRegions()) {
                        if (region.getBounds().getWorld().equals(player.getWorld())) {
                            // Simple check if chunk might intersect with region
                            int chunkMinX = chunkX * 16;
                            int chunkMaxX = chunkMinX + 15;
                            int chunkMinZ = chunkZ * 16;
                            int chunkMaxZ = chunkMinZ + 15;
                            
                            if (chunkMaxX >= region.getBounds().getMinX() && chunkMinX <= region.getBounds().getMaxX() &&
                                chunkMaxZ >= region.getBounds().getMinZ() && chunkMinZ <= region.getBounds().getMaxZ()) {
                                hasQuantumRegions = true;
                                break;
                            }
                        }
                    }

                    if (!hasQuantumRegions) return;

                    // Chunk data modification is very complex and would require
                    // deep understanding of the chunk data format
                    // For now, we'll log that we detected it
                    if (plugin.getConfig().getBoolean("debug.log-packets", false)) {
                        logger.fine("Detected chunk data packet in quantum region for " + player.getName() + 
                                   " at chunk (" + chunkX + ", " + chunkZ + ")");
                    }

                } catch (Exception e) {
                    logger.warning("Error processing chunk data packet: " + e.getMessage());
                    if (plugin.getConfig().getBoolean("debug.enable-debug", false)) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * Sends a custom block change to a specific player.
     *
     * @param player    The player to send to
     * @param location  The block location
     * @param blockData The block data to show
     */
    public void sendBlockChange(Player player, Location location, BlockData blockData) {
        try {
            player.sendBlockChange(location, blockData);
            
            if (plugin.getConfig().getBoolean("debug.log-packets", false)) {
                logger.fine("Sent custom block change to " + player.getName() + 
                           " at " + location + " with data: " + blockData.getAsString());
            }
        } catch (Exception e) {
            logger.warning("Failed to send block change to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Refreshes a region for a specific player by sending all block changes.
     *
     * @param player The player
     * @param region The quantum region
     */
    public void refreshRegionForPlayer(Player player, QuantumRegion region) {
        if (!enabled) return;

        try {
            QuantumCraft quantumPlugin = (QuantumCraft) plugin;
            RegionState state = quantumPlugin.getPlayerStateManager().getPlayerRegionState(player, region);
            boolean inReality = quantumPlugin.getPlayerStateManager().isPlayerInReality(player, region);

            if (inReality || state == null) {
                // Send reality blocks (actual server state)
                refreshRealityForPlayer(player, region);
            } else {
                // Send state blocks
                refreshStateForPlayer(player, region, state);
            }

            if (plugin.getConfig().getBoolean("debug.log-regions", false)) {
                logger.info("Refreshed region '" + region.getName() + "' for player " + player.getName() +
                           " (mode: " + (inReality ? "reality" : "state:" + (state != null ? state.getName() : "default")) + ")");
            }

        } catch (Exception e) {
            logger.warning("Failed to refresh region for player " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug.enable-debug", false)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Refreshes reality view for a player in a region.
     */
    private void refreshRealityForPlayer(Player player, QuantumRegion region) {
        // Use async processing for large regions
        boolean useAsync = plugin.getConfig().getBoolean("performance.enable-async-processing", true);
        long volume = region.getBounds().getVolume();

        if (useAsync && volume > 1000) {
            // Process large regions asynchronously
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                refreshRealityForPlayerSync(player, region);
            });
        } else {
            // Process small regions synchronously
            refreshRealityForPlayerSync(player, region);
        }
    }

    /**
     * Synchronous reality refresh implementation.
     */
    private void refreshRealityForPlayerSync(Player player, QuantumRegion region) {
        int maxBlocksPerTick = plugin.getConfig().getInt("performance.max-blocks-per-packet", 1000);
        int blockCount = 0;

        for (int x = region.getBounds().getMinX(); x <= region.getBounds().getMaxX(); x++) {
            for (int y = region.getBounds().getMinY(); y <= region.getBounds().getMaxY(); y++) {
                for (int z = region.getBounds().getMinZ(); z <= region.getBounds().getMaxZ(); z++) {
                    Location loc = new Location(region.getBounds().getWorld(), x, y, z);
                    BlockData realData = loc.getBlock().getBlockData();

                    // Schedule block change on main thread
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sendBlockChange(player, loc, realData);
                    });

                    blockCount++;

                    // Batch processing to avoid overwhelming the client
                    if (blockCount >= maxBlocksPerTick) {
                        try {
                            Thread.sleep(50); // Small delay between batches
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        blockCount = 0;
                    }
                }
            }
        }
    }

    /**
     * Refreshes state view for a player in a region.
     */
    private void refreshStateForPlayer(Player player, QuantumRegion region, RegionState state) {
        // Use async processing for large regions
        boolean useAsync = plugin.getConfig().getBoolean("performance.enable-async-processing", true);
        long volume = region.getBounds().getVolume();

        if (useAsync && volume > 1000) {
            // Process large regions asynchronously
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                refreshStateForPlayerSync(player, region, state);
            });
        } else {
            // Process small regions synchronously
            refreshStateForPlayerSync(player, region, state);
        }
    }

    /**
     * Synchronous state refresh implementation.
     */
    private void refreshStateForPlayerSync(Player player, QuantumRegion region, RegionState state) {
        int maxBlocksPerTick = plugin.getConfig().getInt("performance.max-blocks-per-packet", 1000);
        int blockCount = 0;

        for (int x = region.getBounds().getMinX(); x <= region.getBounds().getMaxX(); x++) {
            for (int y = region.getBounds().getMinY(); y <= region.getBounds().getMaxY(); y++) {
                for (int z = region.getBounds().getMinZ(); z <= region.getBounds().getMaxZ(); z++) {
                    BlockData stateData = state.getBlockData(x, y, z);
                    if (stateData != null) {
                        Location loc = new Location(region.getBounds().getWorld(), x, y, z);

                        // Schedule block change on main thread
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            sendBlockChange(player, loc, stateData);
                        });

                        blockCount++;

                        // Batch processing to avoid overwhelming the client
                        if (blockCount >= maxBlocksPerTick) {
                            try {
                                Thread.sleep(50); // Small delay between batches
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            blockCount = 0;
                        }
                    }
                }
            }
        }
    }

    // Getters
    public boolean isEnabled() { return enabled; }
}
