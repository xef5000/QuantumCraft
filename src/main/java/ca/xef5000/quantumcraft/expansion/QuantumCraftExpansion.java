package ca.xef5000.quantumcraft.expansion;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.player.PlayerStateManager;
import ca.xef5000.quantumcraft.region.QuantumRegion;
import ca.xef5000.quantumcraft.region.RegionManager;
import ca.xef5000.quantumcraft.region.RegionState;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class QuantumCraftExpansion extends PlaceholderExpansion {

    private final QuantumCraft plugin;

    public QuantumCraftExpansion(QuantumCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "quantumcraft";
    }

    @Override
    public @NotNull String getAuthor() {
        List<String> authors = plugin.getDescription().getAuthors();
        return authors != null && !authors.isEmpty() ? String.join(", ", authors) : "Unknown";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String identifier) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) {
            return null;
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return null;
        }

        RegionManager regionManager = plugin.getRegionManager();
        PlayerStateManager playerStateManager = plugin.getPlayerStateManager();

        List<QuantumRegion> regions = regionManager.getRegionsAt(player.getLocation());

        if (regions.isEmpty()) {
            // Player is not in any QuantumCraft region.
            // Return the configured message for any placeholder from this expansion.
            return plugin.getConfig().getString("placeholders.not_in_region_message", "&7Not in a quantum region");
        }

        // Player is in at least one region. Use the first one found for simplicity.
        QuantumRegion currentRegion = regions.get(0);

        if ("region".equalsIgnoreCase(identifier)) {
            return currentRegion.getName();
        }

        if ("region_state".equalsIgnoreCase(identifier)) {
            RegionState playerState = playerStateManager.getPlayerRegionState(player, currentRegion);
            if (playerState != null) {
                return playerState.getName();
            } else {
                // Fallback to the region's default state name
                RegionState defaultState = currentRegion.getState(currentRegion.getDefaultStateName());
                if (defaultState != null) {
                    return defaultState.getName();
                }
            }
            // If state is still somehow not determined (e.g., default state missing or misconfigured)
            // You could make "Unknown State" configurable too, similar to "not_in_region_message"
            return plugin.getConfig().getString("placeholders.unknown_state_message", "Unknown State");
        }

        // Identifier is not "region" or "region_state".
        // Since the player IS in a region, this is an unknown placeholder for this expansion.
        // Returning null allows PlaceholderAPI to handle it (e.g., by showing the raw placeholder).
        return null;
    }
}
