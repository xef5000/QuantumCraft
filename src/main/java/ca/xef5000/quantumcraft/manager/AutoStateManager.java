package ca.xef5000.quantumcraft.manager;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.conditions.ConditionEvaluator;
import ca.xef5000.quantumcraft.conditions.StateCondition;
import ca.xef5000.quantumcraft.config.RegionConfig;
import ca.xef5000.quantumcraft.region.QuantumRegion;
import ca.xef5000.quantumcraft.region.RegionState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatically manages which quantum state each player should see based on conditions.
 * This system removes the need for players to manually switch states.
 */
public class AutoStateManager {
    private final QuantumCraft plugin;
    private final ConditionEvaluator conditionEvaluator;
    private final RegionConfig regionConfig;
    
    // Track current states for each player in each region
    private final Map<UUID, Map<String, String>> playerCurrentStates = new ConcurrentHashMap<>();
    
    // Track which players are in which regions
    private final Map<UUID, Set<String>> playerRegions = new ConcurrentHashMap<>();
    
    // Background task for checking conditions
    private BukkitTask conditionCheckTask;
    
    // Performance tracking
    private final Map<String, Long> lastRegionCheck = new ConcurrentHashMap<>();

    public AutoStateManager(QuantumCraft plugin, RegionConfig regionConfig) {
        this.plugin = plugin;
        this.regionConfig = regionConfig;
        this.conditionEvaluator = new ConditionEvaluator(plugin);
    }

    /**
     * Starts the automatic state management system.
     */
    public void start() {
        if (conditionCheckTask != null) {
            conditionCheckTask.cancel();
        }

        int interval = regionConfig.getGlobalSettings().conditionCheckInterval;
        
        conditionCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkAllPlayersStates();
            }
        }.runTaskTimerAsynchronously(plugin, 20L, interval); // Start after 1 second, then repeat

        plugin.getLogger().info("AutoStateManager started with " + interval + " tick interval");
    }

    /**
     * Stops the automatic state management system.
     */
    public void stop() {
        if (conditionCheckTask != null) {
            conditionCheckTask.cancel();
            conditionCheckTask = null;
        }
        
        playerCurrentStates.clear();
        playerRegions.clear();
        
        plugin.getLogger().info("AutoStateManager stopped");
    }

    /**
     * Checks and updates states for all online players.
     */
    private void checkAllPlayersStates() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) return;

        int maxRegionsPerCheck = regionConfig.getGlobalSettings().maxRegionsPerCheck;
        int checkedRegions = 0;

        for (Player player : onlinePlayers) {
            if (checkedRegions >= maxRegionsPerCheck) break;
            
            try {
                checkedRegions += checkPlayerStates(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking states for player " + player.getName() + ": " + e.getMessage());
                if (regionConfig.getGlobalSettings().debugStateChanges) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Checks and updates states for a specific player.
     *
     * @param player The player to check
     * @return Number of regions checked
     */
    private int checkPlayerStates(Player player) {
        UUID playerId = player.getUniqueId();
        Set<String> currentRegions = new HashSet<>();
        int regionsChecked = 0;

        // Find all regions the player is currently in
        for (QuantumRegion region : plugin.getRegionManager().getAllRegions()) {
            if (region.contains(player.getLocation())) {
                currentRegions.add(region.getId());
                
                // Check if we should update this region's state
                if (shouldCheckRegion(region.getId())) {
                    String newState = determinePlayerState(player, region);
                    updatePlayerState(player, region, newState);
                    regionsChecked++;
                }
            }
        }

        // Handle region entry/exit
        Set<String> previousRegions = playerRegions.getOrDefault(playerId, new HashSet<>());
        handleRegionChanges(player, previousRegions, currentRegions);
        playerRegions.put(playerId, currentRegions);

        return regionsChecked;
    }

    /**
     * Determines which state a player should see for a given region.
     *
     * @param player The player
     * @param region The quantum region
     * @return The state ID the player should see
     */
    private String determinePlayerState(Player player, QuantumRegion region) {
        RegionConfig.RegionConfigData regionData = regionConfig.getRegionConfig(region.getId());
        if (regionData == null) {
            return region.getDefaultStateName();
        }

        // Collect all states the player qualifies for
        List<QualifiedState> qualifiedStates = new ArrayList<>();

        for (RegionConfig.StateConfigData stateData : regionData.states.values()) {
            if (meetsConditions(player, stateData.conditions)) {
                qualifiedStates.add(new QualifiedState(stateData.id, stateData.priority));
            }
        }

        // If no states qualify, use default
        if (qualifiedStates.isEmpty()) {
            return regionData.defaultStateId;
        }

        // Resolve multiple qualified states
        return resolveMultipleStates(qualifiedStates, regionData.defaultStateId);
    }

    /**
     * Checks if a player meets all conditions for a state.
     *
     * @param player The player
     * @param conditions The list of conditions to check
     * @return true if all conditions are met
     */
    private boolean meetsConditions(Player player, List<StateCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true; // No conditions means always accessible
        }

        for (StateCondition condition : conditions) {
            if (!conditionEvaluator.evaluate(condition, player)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Resolves which state to use when multiple states are qualified.
     *
     * @param qualifiedStates List of qualified states with priorities
     * @param defaultStateId The default state ID
     * @return The state ID to use
     */
    private String resolveMultipleStates(List<QualifiedState> qualifiedStates, String defaultStateId) {
        String resolution = regionConfig.getGlobalSettings().multiStateResolution;

        return switch (resolution) {
            case "highest_priority" -> qualifiedStates.stream()
                    .max(Comparator.comparingInt(s -> s.priority))
                    .map(s -> s.stateId)
                    .orElse(defaultStateId);
                    
            case "random" -> {
                Random random = new Random();
                QualifiedState randomState = qualifiedStates.get(random.nextInt(qualifiedStates.size()));
                yield randomState.stateId;
            }
            
            case "cycle_time" -> {
                // Cycle through states based on time
                long time = System.currentTimeMillis() / 1000; // Seconds
                int index = (int) (time / 60) % qualifiedStates.size(); // Change every minute
                yield qualifiedStates.get(index).stateId;
            }
            
            default -> qualifiedStates.stream()
                    .max(Comparator.comparingInt(s -> s.priority))
                    .map(s -> s.stateId)
                    .orElse(defaultStateId);
        };
    }

    /**
     * Updates a player's state for a region if it has changed.
     *
     * @param player The player
     * @param region The quantum region
     * @param newStateId The new state ID
     */
    private void updatePlayerState(Player player, QuantumRegion region, String newStateId) {
        UUID playerId = player.getUniqueId();
        String regionId = region.getId();
        
        Map<String, String> playerStates = playerCurrentStates.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        String currentStateId = playerStates.get(regionId);

        // Check if state actually changed
        if (Objects.equals(currentStateId, newStateId)) {
            return; // No change needed
        }

        // Handle special "REALITY" state
        if ("REALITY".equals(newStateId)) {
            plugin.getPlayerStateManager().setPlayerReality(player, region);
        } else {
            RegionState newState = region.getState(newStateId);
            if (newState != null) {
                plugin.getPlayerStateManager().setPlayerRegionState(player, region, newStateId);
            } else {
                plugin.getLogger().warning("State " + newStateId + " not found in region " + regionId);
                return;
            }
        }

        // Update tracking
        playerStates.put(regionId, newStateId);

        // Execute state change commands
        executeStateChangeCommands(player, region, currentStateId, newStateId);

        // Announce state change if enabled
        announceStateChange(player, region, newStateId);

        if (regionConfig.getGlobalSettings().debugStateChanges) {
            plugin.getLogger().info("Player " + player.getName() + " auto-switched to state " + 
                                   newStateId + " in region " + region.getName());
        }
    }

    /**
     * Executes commands when a player's state changes.
     */
    private void executeStateChangeCommands(Player player, QuantumRegion region, String oldStateId, String newStateId) {
        RegionConfig.RegionConfigData regionData = regionConfig.getRegionConfig(region.getId());
        if (regionData == null) return;

        // Execute exit commands for old state
        if (oldStateId != null && !oldStateId.equals("REALITY")) {
            RegionConfig.StateConfigData oldStateData = regionData.states.get(oldStateId);
            if (oldStateData != null) {
                executeCommands(player, oldStateData.onExitCommands);
            }
        }

        // Execute enter commands for new state
        if (!newStateId.equals("REALITY")) {
            RegionConfig.StateConfigData newStateData = regionData.states.get(newStateId);
            if (newStateData != null) {
                executeCommands(player, newStateData.onEnterCommands);
            }
        }
    }

    /**
     * Executes a list of commands with player placeholders.
     */
    private void executeCommands(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String command : commands) {
                String processedCommand = command.replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
            }
        });
    }

    /**
     * Announces state changes to players if enabled.
     */
    private void announceStateChange(Player player, QuantumRegion region, String newStateId) {
        RegionConfig.RegionConfigData regionData = regionConfig.getRegionConfig(region.getId());
        if (regionData == null || !regionData.settings.announceStateChanges) return;

        String stateName;
        if ("REALITY".equals(newStateId)) {
            stateName = "Reality";
        } else {
            RegionConfig.StateConfigData stateData = regionData.states.get(newStateId);
            stateName = stateData != null ? stateData.name : newStateId;
        }

        player.sendMessage(ChatColor.GRAY + "Quantum state shifted to: " + ChatColor.AQUA + stateName);
    }

    /**
     * Handles when players enter or exit regions.
     */
    private void handleRegionChanges(Player player, Set<String> previousRegions, Set<String> currentRegions) {
        UUID playerId = player.getUniqueId();

        // Handle region exits
        for (String regionId : previousRegions) {
            if (!currentRegions.contains(regionId)) {
                handleRegionExit(player, regionId);
            }
        }

        // Handle region entries
        for (String regionId : currentRegions) {
            if (!previousRegions.contains(regionId)) {
                handleRegionEntry(player, regionId);
            }
        }
    }

    /**
     * Handles when a player enters a region.
     */
    private void handleRegionEntry(Player player, String regionId) {
        RegionConfig.RegionConfigData regionData = regionConfig.getRegionConfig(regionId);
        if (regionData == null) return;

        // Send entry message
        String entryMessage = regionData.entryMessage;
        if (entryMessage == null) {
            entryMessage = regionConfig.getGlobalSettings().defaultEntryMessage;
        }
        
        if (entryMessage != null && !entryMessage.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', entryMessage));
        }

        // Force immediate state check for this region
        QuantumRegion region = plugin.getRegionManager().getRegion(regionId);
        if (region != null) {
            String newState = determinePlayerState(player, region);
            updatePlayerState(player, region, newState);
        }
    }

    /**
     * Handles when a player exits a region.
     */
    private void handleRegionExit(Player player, String regionId) {
        RegionConfig.RegionConfigData regionData = regionConfig.getRegionConfig(regionId);
        if (regionData == null) return;

        // Send exit message
        String exitMessage = regionData.exitMessage;
        if (exitMessage == null) {
            exitMessage = regionConfig.getGlobalSettings().defaultExitMessage;
        }
        
        if (exitMessage != null && !exitMessage.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', exitMessage));
        }

        // Clean up player state tracking
        UUID playerId = player.getUniqueId();
        Map<String, String> playerStates = playerCurrentStates.get(playerId);
        if (playerStates != null) {
            playerStates.remove(regionId);
        }
    }

    /**
     * Checks if a region should be checked for state updates based on its refresh interval.
     */
    private boolean shouldCheckRegion(String regionId) {
        RegionConfig.RegionConfigData regionData = regionConfig.getRegionConfig(regionId);
        if (regionData == null) return true;

        long now = System.currentTimeMillis();
        long lastCheck = lastRegionCheck.getOrDefault(regionId, 0L);
        long interval = regionData.settings.stateRefreshInterval * 50L; // Convert ticks to milliseconds

        if (now - lastCheck >= interval) {
            lastRegionCheck.put(regionId, now);
            return true;
        }

        return false;
    }

    /**
     * Forces an immediate state check for a specific player.
     *
     * @param player The player to check
     */
    public void forceCheckPlayer(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                checkPlayerStates(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Error in forced state check for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Cleans up data for a player who left the server.
     *
     * @param player The player who left
     */
    public void cleanupPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        playerCurrentStates.remove(playerId);
        playerRegions.remove(playerId);
    }

    /**
     * Gets the current state a player is viewing for a region.
     *
     * @param player The player
     * @param regionId The region ID
     * @return The current state ID, or null if not tracked
     */
    public String getPlayerCurrentState(Player player, String regionId) {
        Map<String, String> playerStates = playerCurrentStates.get(player.getUniqueId());
        return playerStates != null ? playerStates.get(regionId) : null;
    }

    // Helper class for qualified states
    private static class QualifiedState {
        final String stateId;
        final int priority;

        QualifiedState(String stateId, int priority) {
            this.stateId = stateId;
            this.priority = priority;
        }
    }
}
