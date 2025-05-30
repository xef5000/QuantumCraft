package ca.xef5000.quantumcraft.player;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.region.QuantumRegion;
import ca.xef5000.quantumcraft.region.RegionState;
import ca.xef5000.quantumcraft.storage.PlayerStateStorage;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Manages which quantum states each player is viewing.
 * Tracks player state assignments and handles state switching.
 */
public class PlayerStateManager {
    private final QuantumCraft plugin;

    // Maps player UUID -> region ID -> state name
    private final Map<UUID, Map<String, String>> playerStates;

    // Maps player UUID -> set of region IDs where player is in "reality" mode
    private final Map<UUID, Set<String>> realityMode;

    // Cache for quick lookups
    private final Map<UUID, Map<String, RegionState>> stateCache;

    // Database storage for player states
    private final PlayerStateStorage stateStorage;

    /**
     * Creates a new PlayerStateManager.
     *
     * @param plugin The plugin instance
     */
    public PlayerStateManager(QuantumCraft plugin) {
        this.plugin = plugin;
        this.playerStates = new ConcurrentHashMap<>();
        this.realityMode = new ConcurrentHashMap<>();
        this.stateCache = new ConcurrentHashMap<>();
        this.stateStorage = new PlayerStateStorage(plugin, plugin.getLogger());
    }

    /**
     * Initializes the player state manager and loads player states from the database.
     */
    public void initialize() {
        // Initialize the database
        if (!stateStorage.initialize()) {
            plugin.getLogger().severe("Failed to initialize player state storage. Player states will not persist!");
            return;
        }

        // Load player states from database
        loadPlayerStates();
    }

    /**
     * Loads all player states from the database.
     */
    private void loadPlayerStates() {
        plugin.getLogger().info("Loading player states from database...");

        // Load player states
        stateStorage.loadAllPlayerStates().thenAccept(states -> {
            playerStates.clear();
            playerStates.putAll(states);
            plugin.getLogger().info("Loaded " + states.size() + " player state records from database");
        }).exceptionally(e -> {
            plugin.getLogger().severe("Failed to load player states: " + e.getMessage());
            e.printStackTrace();
            return null;
        });

        // Load reality modes
        stateStorage.loadAllPlayerRealityModes().thenAccept(modes -> {
            realityMode.clear();
            realityMode.putAll(modes);
            plugin.getLogger().info("Loaded " + modes.size() + " player reality mode records from database");
        }).exceptionally(e -> {
            plugin.getLogger().severe("Failed to load player reality modes: " + e.getMessage());
            e.printStackTrace();
            return null;
        });
    }

    /**
     * Saves all player states to the database.
     */
    public void saveAllPlayerStates() {
        plugin.getLogger().info("Saving player states to database...");

        // Save player states
        for (Map.Entry<UUID, Map<String, String>> entry : playerStates.entrySet()) {
            UUID playerUuid = entry.getKey();
            Map<String, String> states = entry.getValue();

            for (Map.Entry<String, String> stateEntry : states.entrySet()) {
                String regionId = stateEntry.getKey();
                String stateName = stateEntry.getValue();

                stateStorage.savePlayerState(playerUuid, regionId, stateName);
            }
        }

        // Save reality modes
        for (Map.Entry<UUID, Set<String>> entry : realityMode.entrySet()) {
            UUID playerUuid = entry.getKey();
            Set<String> regions = entry.getValue();

            for (String regionId : regions) {
                stateStorage.savePlayerReality(playerUuid, regionId);
            }
        }

        plugin.getLogger().info("Player states saved to database");
    }

    /**
     * Shuts down the player state manager and saves all player states.
     */
    public void shutdown() {
        // Save all player states
        saveAllPlayerStates();

        // Shutdown the storage
        stateStorage.shutdown();
    }

    /**
     * Sets which state a player should see for a specific region.
     *
     * @param player     The player
     * @param region     The quantum region
     * @param stateName  The name of the state to show
     * @throws IllegalArgumentException If the state doesn't exist
     */
    public void setPlayerRegionState(Player player, QuantumRegion region, String stateName) {
        RegionState state = region.getState(stateName);
        if (state == null) {
            throw new IllegalArgumentException("State '" + stateName + "' does not exist in region '" + region.getName() + "'");
        }

        UUID playerId = player.getUniqueId();
        String regionId = region.getId();

        // Update player state mapping
        playerStates.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(regionId, stateName);

        // Update cache
        stateCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(regionId, state);

        // Remove from reality mode if they were in it
        Set<String> realityRegions = realityMode.get(playerId);
        if (realityRegions != null) {
            realityRegions.remove(regionId);
        }

        // Update the player's view
        updatePlayerView(player, region);

        // Save to database
        stateStorage.savePlayerState(playerId, regionId, stateName);

        plugin.getLogger().info("Player " + player.getName() + " switched to state '" + stateName + "' in region '" + region.getName() + "'");
    }

    /**
     * Gets the state a player is viewing for a specific region.
     *
     * @param player The player
     * @param region The quantum region
     * @return The RegionState the player is viewing, or null if using default/reality
     */
    public RegionState getPlayerRegionState(Player player, QuantumRegion region) {
        UUID playerId = player.getUniqueId();
        String regionId = region.getId();

        // Check if player is in reality mode for this region
        Set<String> realityRegions = realityMode.get(playerId);
        if (realityRegions != null && realityRegions.contains(regionId)) {
            return null; // Reality mode - no custom state
        }

        // Check cache first
        Map<String, RegionState> playerCache = stateCache.get(playerId);
        if (playerCache != null && playerCache.containsKey(regionId)) {
            return playerCache.get(regionId);
        }

        // Get from player states
        Map<String, String> states = playerStates.get(playerId);
        if (states != null && states.containsKey(regionId)) {
            String stateName = states.get(regionId);
            RegionState state = region.getState(stateName);

            // Update cache
            if (state != null) {
                stateCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(regionId, state);
            }

            return state;
        }

        // Default to the region's default state
        RegionState defaultState = region.getDefaultState();
        if (defaultState != null) {
            stateCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(regionId, defaultState);
        }

        return defaultState;
    }

    /**
     * Puts a player in "reality" mode for a specific region.
     * In reality mode, the player sees the actual server state, not any quantum state.
     *
     * @param player The player
     * @param region The quantum region
     */
    public void setPlayerReality(Player player, QuantumRegion region) {
        UUID playerId = player.getUniqueId();
        String regionId = region.getId();

        // Add to reality mode
        realityMode.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(regionId);

        // Remove from state cache
        Map<String, RegionState> playerCache = stateCache.get(playerId);
        if (playerCache != null) {
            playerCache.remove(regionId);
        }

        // Update the player's view to show reality
        updatePlayerView(player, region);

        // Save to database
        stateStorage.savePlayerReality(playerId, regionId);

        plugin.getLogger().info("Player " + player.getName() + " entered reality mode for region '" + region.getName() + "'");
    }

    /**
     * Removes a player from reality mode for all regions.
     *
     * @param player The player
     */
    public void clearPlayerReality(Player player) {
        UUID playerId = player.getUniqueId();
        Set<String> realityRegions = realityMode.get(playerId);

        if (realityRegions != null && !realityRegions.isEmpty()) {
            // Update view for all regions they were in reality mode for
            for (String regionId : realityRegions) {
                QuantumRegion region = plugin.getRegionManager().getRegion(regionId);
                if (region != null) {
                    updatePlayerView(player, region);
                }
            }

            realityRegions.clear();
            plugin.getLogger().info("Player " + player.getName() + " exited reality mode for all regions");
        }
    }

    /**
     * Removes a player from reality mode for a specific region.
     *
     * @param player The player
     * @param region The quantum region
     */
    public void clearPlayerReality(Player player, QuantumRegion region) {
        UUID playerId = player.getUniqueId();
        String regionId = region.getId();

        Set<String> realityRegions = realityMode.get(playerId);
        if (realityRegions != null && realityRegions.remove(regionId)) {
            updatePlayerView(player, region);
            plugin.getLogger().info("Player " + player.getName() + " exited reality mode for region '" + region.getName() + "'");
        }
    }

    /**
     * Checks if a player is in reality mode for a specific region.
     *
     * @param player The player
     * @param region The quantum region
     * @return true if the player is in reality mode for this region
     */
    public boolean isPlayerInReality(Player player, QuantumRegion region) {
        UUID playerId = player.getUniqueId();
        String regionId = region.getId();

        Set<String> realityRegions = realityMode.get(playerId);
        return realityRegions != null && realityRegions.contains(regionId);
    }

    /**
     * Updates a player's view of a specific region.
     * This triggers the protocol system to send the appropriate blocks.
     *
     * @param player The player
     * @param region The quantum region
     */
    public void updatePlayerView(Player player, QuantumRegion region) {
        RegionState state = getPlayerRegionState(player, region);
        String viewType = isPlayerInReality(player, region) ? "reality" :
                         (state != null ? "state:" + state.getName() : "default");

        plugin.getLogger().fine("Updating view for player " + player.getName() +
                               " in region " + region.getName() + " to " + viewType);

        // Actually refresh the region for the player
        plugin.getPacketInterceptor().refreshRegionForPlayer(player, region);
    }

    /**
     * Updates all players' views of a specific region.
     * Useful when a region state has been modified.
     *
     * @param region The quantum region
     */
    public void updateAllPlayersView(QuantumRegion region) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (region.contains(player.getLocation())) {
                updatePlayerView(player, region);
            }
        }
    }

    /**
     * Updates a player's view for all regions they are currently in.
     * Useful when a player joins the server or moves to a new area.
     *
     * @param player The player
     */
    public void updatePlayerViewForAllRegions(Player player) {
        Location playerLocation = player.getLocation();

        for (QuantumRegion region : plugin.getRegionManager().getAllRegions()) {
            if (region.contains(playerLocation)) {
                updatePlayerView(player, region);
            }
        }
    }

    /**
     * Refreshes all quantum regions for a player.
     * This is more comprehensive than updatePlayerViewForAllRegions as it
     * refreshes all regions the player has states for, not just current location.
     *
     * @param player The player
     */
    public void refreshAllRegionsForPlayer(Player player) {
        UUID playerId = player.getUniqueId();

        // Refresh regions the player has custom states for
        Map<String, String> playerRegionStates = playerStates.get(playerId);
        if (playerRegionStates != null) {
            for (String regionId : playerRegionStates.keySet()) {
                QuantumRegion region = plugin.getRegionManager().getRegion(regionId);
                if (region != null) {
                    updatePlayerView(player, region);
                }
            }
        }

        // Refresh regions the player is in reality mode for
        Set<String> realityRegions = realityMode.get(playerId);
        if (realityRegions != null) {
            for (String regionId : realityRegions) {
                QuantumRegion region = plugin.getRegionManager().getRegion(regionId);
                if (region != null) {
                    updatePlayerView(player, region);
                }
            }
        }

        // Also refresh any regions at the player's current location
        updatePlayerViewForAllRegions(player);
    }

    /**
     * Cleans up data for a player who has left the server.
     *
     * @param player The player who left
     */
    public void cleanupPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        playerStates.remove(playerId);
        realityMode.remove(playerId);
        stateCache.remove(playerId);

        // Note: We don't remove from database as we want to persist player states
        // across sessions. If you want to remove player data from the database,
        // uncomment the following line:
        // stateStorage.removePlayerData(playerId);
    }

    /**
     * Gets all regions a player has custom states for.
     *
     * @param player The player
     * @return A map of region ID to state name
     */
    public Map<String, String> getPlayerStates(Player player) {
        UUID playerId = player.getUniqueId();
        Map<String, String> states = playerStates.get(playerId);
        return states != null ? new HashMap<>(states) : new HashMap<>();
    }

    /**
     * Gets all regions a player is in reality mode for.
     *
     * @param player The player
     * @return A set of region IDs
     */
    public Set<String> getPlayerRealityRegions(Player player) {
        UUID playerId = player.getUniqueId();
        Set<String> regions = realityMode.get(playerId);
        return regions != null ? new HashSet<>(regions) : new HashSet<>();
    }

    /**
     * Gets statistics about player states.
     *
     * @return A map containing various statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("playersWithStates", playerStates.size());
        stats.put("playersInReality", realityMode.size());
        stats.put("totalStateAssignments", playerStates.values().stream().mapToInt(Map::size).sum());
        stats.put("totalRealityAssignments", realityMode.values().stream().mapToInt(Set::size).sum());
        return stats;
    }
}
