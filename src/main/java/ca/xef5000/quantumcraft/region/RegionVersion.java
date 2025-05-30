package ca.xef5000.quantumcraft.region;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a version of a region, containing a snapshot of block states.
 */
public class RegionVersion implements ConfigurationSerializable {
    private final Map<Location, BlockState> blockStates;

    /**
     * Creates a new empty region version.
     */
    public RegionVersion() {
        this.blockStates = new HashMap<>();
    }

    /**
     * Creates a region version from a serialized map.
     *
     * @param map The serialized map
     */
    @SuppressWarnings("unchecked")
    public RegionVersion(Map<String, Object> map) {
        this.blockStates = new HashMap<>();
        
        Object blockStatesData = map.get("blockStates");
        if (blockStatesData instanceof org.bukkit.configuration.ConfigurationSection blockStateSection) {
            for (String key : blockStateSection.getKeys(false)) {
                org.bukkit.configuration.ConfigurationSection entrySection = blockStateSection.getConfigurationSection(key);
                if (entrySection != null) {
                    String[] parts = key.split(",");
                    if (parts.length != 4) continue;

                    try {
                        String worldName = parts[0];
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);
                        double z = Double.parseDouble(parts[3]);

                        Location location = new Location(org.bukkit.Bukkit.getWorld(worldName), x, y, z);
                        BlockState blockState = new BlockState(entrySection.getValues(false));

                        blockStates.put(location, blockState);
                    } catch (NumberFormatException | NullPointerException e) {
                        // Skip invalid entries
                    } catch (IllegalArgumentException e) {
                        // Skip invalid block state data
                    }
                }
            }
        } else if (blockStatesData instanceof Map) {
            // Fallback for manual Map structure or if ConfigurationSection instanceof check failed
            Map<String, ?> outerMap = (Map<String, ?>) blockStatesData;

            for (Map.Entry<String, ?> rawEntry : outerMap.entrySet()) {
                String locationKey = rawEntry.getKey();
                Object innerValue = rawEntry.getValue();

                Map<String, Object> blockStateConstructorMap = null;

                if (innerValue instanceof org.bukkit.configuration.ConfigurationSection innerCs) {
                    blockStateConstructorMap = innerCs.getValues(false);
                } else if (innerValue instanceof Map) {
                    try {
                        blockStateConstructorMap = (Map<String, Object>) innerValue;
                    } catch (ClassCastException e) {
                        // Value was a Map but not Map<String, Object>. Skip.
                        continue;
                    }
                } else {
                    // Value is neither ConfigurationSection nor Map. Skip.
                    continue;
                }

                if (blockStateConstructorMap == null) continue;

                String[] parts = locationKey.split(",");
                if (parts.length != 4) continue;
                try {
                    String worldName = parts[0];
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    Location location = new Location(org.bukkit.Bukkit.getWorld(worldName), x, y, z);

                    BlockState blockState = new BlockState(blockStateConstructorMap);
                    blockStates.put(location, blockState);
                } catch (NumberFormatException | NullPointerException e) {
                    // Skip invalid entries (e.g., world not found, bad number format)
                } catch (IllegalArgumentException e) {
                    // Skip invalid block state data (e.g., bad material name)
                }
            }
        }
    }

    /**
     * Sets a block state in this version.
     *
     * @param location   The location of the block
     * @param material   The material of the block
     * @param blockData  The block data
     */
    public void setBlockState(Location location, Material material, BlockData blockData) {
        blockStates.put(location.clone(), new BlockState(material, blockData));
    }

    /**
     * Gets the block state at the specified location.
     *
     * @param location The location to check
     * @return The block state, or null if not set
     */
    public BlockState getBlockState(Location location) {
        return blockStates.get(location);
    }

    /**
     * Gets all block states in this version.
     *
     * @return A map of locations to block states
     */
    public Map<Location, BlockState> getBlockStates() {
        return new HashMap<>(blockStates);
    }

    /**
     * Serializes this region version to a map.
     *
     * @return The serialized map
     */
    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        
        Map<String, Map<String, Object>> blockStateMap = new HashMap<>();
        for (Map.Entry<Location, BlockState> entry : blockStates.entrySet()) {
            Location loc = entry.getKey();
            String key = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
            blockStateMap.put(key, entry.getValue().serialize());
        }
        map.put("blockStates", blockStateMap);
        
        return map;
    }

    /**
     * Represents a block state (material and block data).
     */
    public static class BlockState implements ConfigurationSerializable {
        private final Material material;
        private final BlockData blockData;

        /**
         * Creates a new block state.
         *
         * @param material  The material
         * @param blockData The block data
         */
        public BlockState(Material material, BlockData blockData) {
            this.material = material;
            this.blockData = blockData;
        }

        /**
         * Creates a block state from a serialized map.
         *
         * @param map The serialized map
         */
        public BlockState(Map<String, Object> map) {
            this.material = Material.valueOf((String) map.get("material"));
            this.blockData = org.bukkit.Bukkit.createBlockData((String) map.get("blockData"));
        }

        /**
         * Gets the material of this block state.
         *
         * @return The material
         */
        public Material getMaterial() {
            return material;
        }

        /**
         * Gets the block data of this block state.
         *
         * @return The block data
         */
        public BlockData getBlockData() {
            return blockData;
        }

        /**
         * Serializes this block state to a map.
         *
         * @return The serialized map
         */
        @Override
        public @NotNull Map<String, Object> serialize() {
            Map<String, Object> map = new HashMap<>();
            map.put("material", material.name());
            map.put("blockData", blockData.getAsString());
            return map;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockState that = (BlockState) o;
            return material == that.material && Objects.equals(blockData.getAsString(), that.blockData.getAsString());
        }

        @Override
        public int hashCode() {
            return Objects.hash(material, blockData.getAsString());
        }
    }
}

