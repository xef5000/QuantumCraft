package ca.xef5000.quantumcraft.protocol;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.manager.RegionManager;
import ca.xef5000.quantumcraft.region.Region;
import ca.xef5000.quantumcraft.region.RegionVersion;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages packet sending for fake blocks using ProtocolLib.
 */
public class PacketManager {
    private final QuantumCraft plugin;
    private final ProtocolManager protocolManager;

    // Track which chunks have been sent to players
    private final Map<Player, Set<Long>> sentChunks;

    /**
     * Creates a new PacketManager.
     *
     * @param plugin The plugin instance
     */
    public PacketManager(QuantumCraft plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.sentChunks = new ConcurrentHashMap<>();
    }

    /**
     * Sends fake block changes to a player for all regions they're in.
     *
     * @param player The player to send blocks to
     */
    public void sendFakeBlocks(Player player) {
        RegionManager regionManager = plugin.getRegionManager();
        List<Region> regions = regionManager.getRegionsAt(player.getLocation());

        for (Region region : regions) {
            String versionName = plugin.getPlayerManager().getPlayerVersion(player, region.getId());
            if (versionName != null) {
                RegionVersion version = region.getVersion(versionName);
                if (version != null) {
                    sendRegionVersion(player, region, version);
                }
            }
        }
    }

    /**
     * Sends fake block changes to a player for a specific region and version.
     *
     * @param player  The player to send blocks to
     * @param region  The region
     * @param version The version of the region
     */
    public void sendRegionVersion(Player player, Region region, RegionVersion version) {
        Map<Location, RegionVersion.BlockState> blockStates = version.getBlockStates();

        for (Map.Entry<Location, RegionVersion.BlockState> entry : blockStates.entrySet()) {
            Location location = entry.getKey();
            RegionVersion.BlockState blockState = entry.getValue();

            // Only send blocks in loaded chunks
            if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                sendBlockChange(player, location, blockState);
            }
        }
    }

    /**
     * Sends a fake block change to a player.
     *
     * @param player     The player to send the block change to
     * @param location   The location of the block
     * @param blockState The block state to show
     */
    public void sendBlockChange(Player player, Location location, RegionVersion.BlockState blockState) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);

        // Set the block position
        packet.getBlockPositionModifier().write(0, new BlockPosition(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        ));

        // Set the block data
        packet.getBlockData().write(0, WrappedBlockData.createData(blockState.getMaterial()));

        try {
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send block change packet to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Sends fake blocks to a player for a specific chunk.
     *
     * @param player The player
     * @param chunk  The chunk
     */
    public void sendChunkBlocks(Player player, Chunk chunk) {
        RegionManager regionManager = plugin.getRegionManager();

        // Get all regions that intersect with this chunk
        Set<Region> regions = new HashSet<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Location corner = new Location(
                        chunk.getWorld(),
                        chunk.getX() * 16 + x,
                        0,
                        chunk.getZ() * 16 + z
                );
                regions.addAll(regionManager.getRegionsAt(corner));
            }
        }

        // Send blocks for each region
        for (Region region : regions) {
            String versionName = plugin.getPlayerManager().getPlayerVersion(player, region.getId());
            if (versionName != null) {
                RegionVersion version = region.getVersion(versionName);
                if (version != null) {
                    sendRegionVersionInChunk(player, region, version, chunk);
                }
            }
        }

        // Mark this chunk as sent
        long chunkKey = getChunkKey(chunk);
        sentChunks.computeIfAbsent(player, k -> new HashSet<>()).add(chunkKey);
    }

    /**
     * Sends fake blocks to a player for a specific region and version within a chunk.
     *
     * @param player  The player
     * @param region  The region
     * @param version The version
     * @param chunk   The chunk
     */
    private void sendRegionVersionInChunk(Player player, Region region, RegionVersion version, Chunk chunk) {
        Map<Location, RegionVersion.BlockState> blockStates = version.getBlockStates();

        for (Map.Entry<Location, RegionVersion.BlockState> entry : blockStates.entrySet()) {
            Location location = entry.getKey();

            // Check if the location is in this chunk
            if (location.getBlockX() >> 4 == chunk.getX() && location.getBlockZ() >> 4 == chunk.getZ()) {
                sendBlockChange(player, location, entry.getValue());
            }
        }
    }

    /**
     * Checks if a chunk has been sent to a player.
     *
     * @param player The player
     * @param chunk  The chunk
     * @return True if the chunk has been sent, false otherwise
     */
    public boolean hasChunkBeenSent(Player player, Chunk chunk) {
        Set<Long> playerChunks = sentChunks.get(player);
        if (playerChunks == null) {
            return false;
        }

        return playerChunks.contains(getChunkKey(chunk));
    }

    /**
     * Clears the sent chunks for a player.
     *
     * @param player The player
     */
    public void clearSentChunks(Player player) {
        sentChunks.remove(player);
    }

    /**
     * Gets a unique key for a chunk.
     *
     * @param chunk The chunk
     * @return The chunk key
     */
    private long getChunkKey(Chunk chunk) {
        return ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
    }
}
