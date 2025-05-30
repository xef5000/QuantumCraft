package ca.xef5000.quantumcraft.region;

import ca.xef5000.quantumcraft.util.CompressionUtil;
import ca.xef5000.quantumcraft.util.RegionBounds;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single state of a quantum region.
 * This class stores the block data for a specific version of a region.
 */
public class RegionState {
    private final String name;
    private final RegionBounds bounds;
    private final Map<String, BlockData> blockData;
    private boolean isCompressed;
    private byte[] compressedData;
    private long lastModified;

    /**
     * Creates a new RegionState.
     *
     * @param name   The name of this state
     * @param bounds The bounds of the region
     */
    public RegionState(String name, RegionBounds bounds) {
        this.name = name;
        this.bounds = bounds;
        this.blockData = new ConcurrentHashMap<>();
        this.isCompressed = false;
        this.lastModified = System.currentTimeMillis();
    }

    /**
     * Captures the current state of blocks within the region bounds.
     */
    public void captureCurrentState() {
        blockData.clear();
        
        for (int x = bounds.getMinX(); x <= bounds.getMaxX(); x++) {
            for (int y = bounds.getMinY(); y <= bounds.getMaxY(); y++) {
                for (int z = bounds.getMinZ(); z <= bounds.getMaxZ(); z++) {
                    Block block = bounds.getWorld().getBlockAt(x, y, z);
                    String key = getBlockKey(x, y, z);
                    blockData.put(key, block.getBlockData().clone());
                }
            }
        }
        
        this.lastModified = System.currentTimeMillis();
        this.isCompressed = false;
        this.compressedData = null;
    }

    /**
     * Gets the block data at the specified coordinates.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return The BlockData at those coordinates, or null if not found
     */
    public BlockData getBlockData(int x, int y, int z) {
        ensureDecompressed();
        String key = getBlockKey(x, y, z);
        return blockData.get(key);
    }

    /**
     * Sets the block data at the specified coordinates.
     *
     * @param x         X coordinate
     * @param y         Y coordinate
     * @param z         Z coordinate
     * @param blockData The BlockData to set
     */
    public void setBlockData(int x, int y, int z, BlockData blockData) {
        ensureDecompressed();
        String key = getBlockKey(x, y, z);
        this.blockData.put(key, blockData.clone());
        this.lastModified = System.currentTimeMillis();
    }

    /**
     * Compresses the block data to save memory.
     *
     * @throws IOException If compression fails
     */
    public void compress() throws IOException {
        if (isCompressed) return;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            
            // Convert BlockData to serializable format
            Map<String, String> serializableData = new HashMap<>();
            for (Map.Entry<String, BlockData> entry : blockData.entrySet()) {
                serializableData.put(entry.getKey(), entry.getValue().getAsString());
            }
            
            oos.writeObject(serializableData);
            oos.flush();
            
            this.compressedData = CompressionUtil.compress(baos.toByteArray());
            this.blockData.clear();
            this.isCompressed = true;
        }
    }

    /**
     * Decompresses the block data.
     *
     * @throws IOException            If decompression fails
     * @throws ClassNotFoundException If deserialization fails
     */
    @SuppressWarnings("unchecked")
    public void decompress() throws IOException, ClassNotFoundException {
        if (!isCompressed || compressedData == null) return;

        byte[] decompressedData = CompressionUtil.decompress(compressedData);
        
        try (ByteArrayInputStream bais = new ByteArrayInputStream(decompressedData);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            
            Map<String, String> serializableData = (Map<String, String>) ois.readObject();
            
            blockData.clear();
            for (Map.Entry<String, String> entry : serializableData.entrySet()) {
                try {
                    BlockData data = org.bukkit.Bukkit.createBlockData(entry.getValue());
                    blockData.put(entry.getKey(), data);
                } catch (Exception e) {
                    // If we can't parse the block data, use air as fallback
                    blockData.put(entry.getKey(), Material.AIR.createBlockData());
                }
            }
            
            this.isCompressed = false;
            this.compressedData = null;
        }
    }

    /**
     * Ensures the block data is decompressed and ready for use.
     */
    private void ensureDecompressed() {
        if (isCompressed) {
            try {
                decompress();
            } catch (Exception e) {
                throw new RuntimeException("Failed to decompress region state: " + name, e);
            }
        }
    }

    /**
     * Creates a unique key for a block position.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return A unique string key
     */
    private String getBlockKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * Gets the number of blocks stored in this state.
     *
     * @return The number of blocks
     */
    public int getBlockCount() {
        if (isCompressed) {
            // We can't count without decompressing, so return estimated count
            return (int) bounds.getVolume();
        }
        return blockData.size();
    }

    /**
     * Gets the memory usage of this state.
     *
     * @return Memory usage in bytes (estimated)
     */
    public long getMemoryUsage() {
        if (isCompressed && compressedData != null) {
            return compressedData.length;
        }
        // Estimate: each block data entry is roughly 50-100 bytes
        return blockData.size() * 75L;
    }

    // Getters
    public String getName() { return name; }
    public RegionBounds getBounds() { return bounds; }
    public boolean isCompressed() { return isCompressed; }
    public long getLastModified() { return lastModified; }

    @Override
    public String toString() {
        return String.format("RegionState{name='%s', blocks=%d, compressed=%s, memory=%s}",
            name, getBlockCount(), isCompressed, CompressionUtil.formatBytes(getMemoryUsage()));
    }
}
