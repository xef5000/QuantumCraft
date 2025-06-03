package ca.xef5000.quantumcraft.commands;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.region.QuantumRegion;
import ca.xef5000.quantumcraft.region.RegionState;
import ca.xef5000.quantumcraft.util.CompressionUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handles the main /quantumcraft command and its subcommands.
 */
public class QuantumCraftCommand implements CommandExecutor, TabCompleter {
    private final QuantumCraft plugin;
    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();
    
    public static final Material SELECTION_STICK_MATERIAL = Material.BLAZE_ROD;

    /**
     * Creates a new command handler.
     *
     * @param plugin The plugin instance
     */
    public QuantumCraftCommand(QuantumCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                return handleCreate(sender, args);
            case "delete":
                return handleDelete(sender, args);
            case "list":
                return handleList(sender, args);
            case "info":
                return handleInfo(sender, args);
            case "state":
                return handleState(sender, args);
            case "switch":
                return handleSwitch(sender, args);
            case "reality":
                return handleReality(sender, args);
            case "stick":
                return handleStick(sender);
            case "stats":
                return handleStats(sender, args);
            case "refresh":
                return handleRefresh(sender, args);
            case "reload":
                return handleReload(sender, args);
            case "reloadconfig":
                return handleReloadConfig(sender, args);
            case "setregionreality":
                return handleSetRegionReality(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sendHelp(sender);
                return true;
        }
    }

    /**
     * Handles the 'create' subcommand.
     */
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc create <name> [x1 y1 z1 x2 y2 z2]");
            sender.sendMessage(ChatColor.YELLOW + "Use /qc stick to get a selection tool");
            return true;
        }

        String name = args[1];
        Location min, max;

        if (plugin.getRegionManager().getRegionByName(name) != null) {
            sender.sendMessage(ChatColor.RED + "A region with this name already exists.");
            return true;
        }

        if (args.length >= 8) {
            // Use provided coordinates
            try {
                double x1 = Double.parseDouble(args[2]);
                double y1 = Double.parseDouble(args[3]);
                double z1 = Double.parseDouble(args[4]);
                double x2 = Double.parseDouble(args[5]);
                double y2 = Double.parseDouble(args[6]);
                double z2 = Double.parseDouble(args[7]);

                min = new Location(player.getWorld(), Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
                max = new Location(player.getWorld(), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinates. Please use numbers.");
                return true;
            }
        } else {
            // Use selection
            UUID playerId = player.getUniqueId();
            Location pos1 = pos1Map.get(playerId);
            Location pos2 = pos2Map.get(playerId);

            if (pos1 == null || pos2 == null) {
                sender.sendMessage(ChatColor.RED + "Please select two positions first using the selection stick (/qc stick)");
                return true;
            }

            min = new Location(player.getWorld(), 
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));
            max = new Location(player.getWorld(),
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));
        }

        try {
            QuantumRegion region = plugin.getRegionManager().createRegion(name, player.getWorld(), min, max);
            sender.sendMessage(ChatColor.GREEN + "Created quantum region: " + name);
            sender.sendMessage(ChatColor.YELLOW + "Use /qc state capture " + name + " default to capture the current state");
            
            return true;
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to create region: " + e.getMessage());
            return true;
        }
    }

    /**
     * Handles the 'delete' subcommand.
     */
    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc delete <region>");
            return true;
        }

        String regionName = args[1];
        QuantumRegion region = plugin.getRegionManager().getRegionByName(regionName);

        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Region not found: " + regionName);
            return true;
        }

        boolean success = plugin.getRegionManager().deleteRegion(region.getId());

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Deleted region: " + regionName);
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to delete region: " + regionName);
        }

        return true;
    }

    /**
     * Handles the 'list' subcommand.
     */
    private boolean handleList(CommandSender sender, String[] args) {
        Collection<QuantumRegion> regions = plugin.getRegionManager().getAllRegions();

        if (regions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No quantum regions found.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Quantum Regions (" + regions.size() + "):");
        for (QuantumRegion region : regions) {
            sender.sendMessage(ChatColor.GREEN + "- " + region.getName() + 
                " (" + region.getStates().size() + " states, " + 
                CompressionUtil.formatBytes(region.getTotalMemoryUsage()) + ")");
        }

        return true;
    }

    /**
     * Handles the 'info' subcommand.
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc info <region>");
            return true;
        }

        String regionName = args[1];
        QuantumRegion region = plugin.getRegionManager().getRegionByName(regionName);

        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Region not found: " + regionName);
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "=== Region Info: " + region.getName() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "ID: " + region.getId());
        sender.sendMessage(ChatColor.YELLOW + "World: " + region.getBounds().getWorld().getName());
        sender.sendMessage(ChatColor.YELLOW + "Bounds: " + region.getBounds().toString());
        sender.sendMessage(ChatColor.YELLOW + "States: " + region.getStates().size());
        sender.sendMessage(ChatColor.YELLOW + "Default State: " + region.getDefaultStateName());
        sender.sendMessage(ChatColor.YELLOW + "Memory Usage: " + CompressionUtil.formatBytes(region.getTotalMemoryUsage()));
        
        sender.sendMessage(ChatColor.GREEN + "States:");
        for (RegionState state : region.getStates()) {
            sender.sendMessage(ChatColor.GREEN + "  - " + state.getName() + 
                " (" + state.getBlockCount() + " blocks, " + 
                CompressionUtil.formatBytes(state.getMemoryUsage()) + 
                (state.isCompressed() ? ", compressed" : "") + ")");
        }

        return true;
    }

    /**
     * Handles the 'state' subcommand.
     */
    private boolean handleState(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc state <create|delete|capture> <region> <state>");
            return true;
        }

        String action = args[1].toLowerCase();
        String regionName = args[2];
        
        QuantumRegion region = plugin.getRegionManager().getRegionByName(regionName);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Region not found: " + regionName);
            return true;
        }

        if (action.equals("create")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /qc state create <region> <state>");
                return true;
            }
            
            String stateName = args[3];
            try {
                RegionState state = region.createState(stateName);
                sender.sendMessage(ChatColor.GREEN + "Created state: " + stateName);
                sender.sendMessage(ChatColor.YELLOW + "Use /qc state capture " + regionName + " " + stateName + " to capture blocks");
                return true;
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + e.getMessage());
                return true;
            }
        } else if (action.equals("delete")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /qc state delete <region> <state>");
                return true;
            }
            
            String stateName = args[3];
            boolean success = region.removeState(stateName);
            
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Deleted state: " + stateName);
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to delete state: " + stateName);
            }
            return true;
        } else if (action.equals("capture")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /qc state capture <region> <state>");
                return true;
            }

            String stateName = args[3];
            RegionState state = region.getState(stateName);
            if (state == null) {
                sender.sendMessage(ChatColor.RED + "State not found: " + stateName);
                return true;
            }

            try {
                sender.sendMessage(ChatColor.YELLOW + "Capturing current blocks for state: " + stateName);
                state.captureCurrentState();
                sender.sendMessage(ChatColor.GREEN + "Captured " + state.getBlockCount() + " blocks (" +
                    CompressionUtil.formatBytes(state.getMemoryUsage()) + ")");

                // Update all players viewing this region
                plugin.getPlayerStateManager().updateAllPlayersView(region);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to capture state: " + e.getMessage());
            }
            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown action: " + action);
            sender.sendMessage(ChatColor.RED + "Usage: /qc state <create|delete> <region> <state>");
            return true;
        }
    }

    // Getters for selection positions (used by listeners)
    public Map<UUID, Location> getPos1Map() { return pos1Map; }
    public Map<UUID, Location> getPos2Map() { return pos2Map; }

    /**
     * Sends the help message to a sender.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== QuantumCraft Commands ===");
        sender.sendMessage(ChatColor.GREEN + "/qc create <name> [coords] - Create a new quantum region");
        sender.sendMessage(ChatColor.GREEN + "/qc delete <region> - Delete a quantum region");
        sender.sendMessage(ChatColor.GREEN + "/qc list - List all quantum regions");
        sender.sendMessage(ChatColor.GREEN + "/qc info <region> - Show region information");
        sender.sendMessage(ChatColor.GREEN + "/qc state create <region> <state> - Create a new state");
        sender.sendMessage(ChatColor.GREEN + "/qc state delete <region> <state> - Delete a state");
        sender.sendMessage(ChatColor.GREEN + "/qc state capture <region> <state> - Capture current blocks");
        sender.sendMessage(ChatColor.GREEN + "/qc switch <region> <state> - Switch to a state");
        sender.sendMessage(ChatColor.GREEN + "/qc reality [region] - Enter reality mode");
        sender.sendMessage(ChatColor.GREEN + "/qc setregionreality <region> <state> - Sets the default 'reality' state for a region");
        sender.sendMessage(ChatColor.GREEN + "/qc stick - Get the selection tool");
        sender.sendMessage(ChatColor.GREEN + "/qc stats - Show plugin statistics");
        sender.sendMessage(ChatColor.GREEN + "/qc refresh [region] - Refresh quantum regions");
        sender.sendMessage(ChatColor.GREEN + "/qc reload - Reload regions from disk");
        sender.sendMessage(ChatColor.GREEN + "/qc reloadconfig - Reload regions.yml configuration");
    }

    /**
     * Handles the 'switch' subcommand.
     */
    private boolean handleSwitch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc switch <region> <state>");
            return true;
        }

        Player player = (Player) sender;
        String regionName = args[1];
        String stateName = args[2];

        QuantumRegion region = plugin.getRegionManager().getRegionByName(regionName);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Region not found: " + regionName);
            return true;
        }

        RegionState state = region.getState(stateName);
        if (state == null) {
            sender.sendMessage(ChatColor.RED + "State not found: " + stateName);
            return true;
        }

        try {
            plugin.getPlayerStateManager().setPlayerRegionState(player, region, stateName);
            sender.sendMessage(ChatColor.GREEN + "Switched to state: " + stateName + " in region: " + regionName);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to switch state: " + e.getMessage());
        }

        return true;
    }

    /**
     * Handles the 'reality' subcommand.
     */
    private boolean handleReality(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length > 1) {
            // Reality mode for specific region
            String regionName = args[1];
            QuantumRegion region = plugin.getRegionManager().getRegionByName(regionName);

            if (region == null) {
                sender.sendMessage(ChatColor.RED + "Region not found: " + regionName);
                return true;
            }

            plugin.getPlayerStateManager().setPlayerReality(player, region);
            sender.sendMessage(ChatColor.GREEN + "Entered reality mode for region: " + regionName);
            sender.sendMessage(ChatColor.YELLOW + "You can now modify blocks normally. Use /qc switch to return to a state.");
        } else {
            // Reality mode for all regions
            plugin.getPlayerStateManager().clearPlayerReality(player);
            sender.sendMessage(ChatColor.GREEN + "Entered reality mode for all regions.");
            sender.sendMessage(ChatColor.YELLOW + "You can now modify blocks normally. Use /qc switch to return to states.");
        }

        return true;
    }

    /**
     * Handles the 'stick' subcommand.
     */
    private boolean handleStick(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        ItemStack stick = new ItemStack(SELECTION_STICK_MATERIAL);
        ItemMeta meta = stick.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "QuantumCraft Selection Tool");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Left click to set position 1",
            ChatColor.GRAY + "Right click to set position 2",
            ChatColor.GRAY + "Use /qc create <name> to create region"
        ));
        stick.setItemMeta(meta);

        player.getInventory().addItem(stick);
        sender.sendMessage(ChatColor.GREEN + "Given you a QuantumCraft selection tool!");
        sender.sendMessage(ChatColor.YELLOW + "Left click to set position 1, right click to set position 2");

        return true;
    }

    /**
     * Handles the 'stats' subcommand.
     */
    private boolean handleStats(CommandSender sender, String[] args) {
        Map<String, Object> regionStats = plugin.getRegionManager().getStatistics();
        Map<String, Object> playerStats = plugin.getPlayerStateManager().getStatistics();

        sender.sendMessage(ChatColor.GREEN + "=== QuantumCraft Statistics ===");
        sender.sendMessage(ChatColor.YELLOW + "Regions: " + regionStats.get("totalRegions"));
        sender.sendMessage(ChatColor.YELLOW + "States: " + regionStats.get("totalStates"));
        sender.sendMessage(ChatColor.YELLOW + "Total Blocks: " + regionStats.get("totalBlocks"));
        sender.sendMessage(ChatColor.YELLOW + "Memory Usage: " + CompressionUtil.formatBytes((Long) regionStats.get("totalMemoryUsage")));
        sender.sendMessage(ChatColor.YELLOW + "Players with States: " + playerStats.get("playersWithStates"));
        sender.sendMessage(ChatColor.YELLOW + "Players in Reality: " + playerStats.get("playersInReality"));

        return true;
    }

    /**
     * Handles the 'refresh' subcommand.
     */
    private boolean handleRefresh(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length > 1) {
            // Refresh specific region
            String regionName = args[1];
            QuantumRegion region = plugin.getRegionManager().getRegionByName(regionName);

            if (region == null) {
                sender.sendMessage(ChatColor.RED + "Region not found: " + regionName);
                return true;
            }

            plugin.getPlayerStateManager().updatePlayerView(player, region);
            sender.sendMessage(ChatColor.GREEN + "Refreshed region: " + regionName);
        } else {
            // Refresh all regions for the player
            plugin.getPlayerStateManager().refreshAllRegionsForPlayer(player);
            sender.sendMessage(ChatColor.GREEN + "Refreshed all quantum regions for you.");
        }

        return true;
    }

    /**
     * Handles the 'reload' subcommand.
     */
    private boolean handleReload(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading quantum regions from disk...");

        try {
            plugin.getRegionManager().reloadAllRegions();
            sender.sendMessage(ChatColor.GREEN + "Successfully reloaded " +
                plugin.getRegionManager().getRegionCount() + " quantum regions.");

            // Refresh all online players
            for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
                plugin.getPlayerStateManager().refreshAllRegionsForPlayer(player);
            }

            sender.sendMessage(ChatColor.GREEN + "Refreshed quantum states for all online players.");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload regions: " + e.getMessage());
            plugin.getLogger().severe("Failed to reload regions: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Handles the 'reloadconfig' subcommand.
     */
    private boolean handleReloadConfig(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading regions.yml configuration...");

        try {
            plugin.reloadRegionConfigurations();
            sender.sendMessage(ChatColor.GREEN + "Successfully reloaded regions.yml configuration.");
            sender.sendMessage(ChatColor.GREEN + "Automatic state management restarted with new settings.");

            // Force check all online players with new config
            for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
                if (plugin.getAutoStateManager() != null) {
                    plugin.getAutoStateManager().forceCheckPlayer(player);
                }
            }

            sender.sendMessage(ChatColor.GREEN + "Applied new configuration to all online players.");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload configuration: " + e.getMessage());
            plugin.getLogger().severe("Failed to reload regions.yml: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private boolean handleSetRegionReality(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc setregionreality <regionName> <stateName>");
            return true;
        }

        String regionName = args[1];
        String stateName = args[2];

        QuantumRegion region = plugin.getRegionManager().getRegionByName(regionName);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Region '" + regionName + "' not found.");
            return true;
        }

        RegionState state = region.getState(stateName);
        if (state == null) {
            sender.sendMessage(ChatColor.RED + "State '" + stateName + "' not found in region '" + regionName + "'.");
            return true;
        }

        region.setDefaultState(stateName);

        // Final objects for lambda
        final QuantumRegion finalRegion = region;
        final RegionState finalState = state;

        plugin.getRegionManager().getStorage().saveRegion(region)
            .thenAccept(v -> {
                sender.sendMessage(ChatColor.GREEN + "Region '" + finalRegion.getName() + "' default state reference updated to '" + stateName + "'.");
                sender.sendMessage(ChatColor.YELLOW + "Applying state '" + stateName + "' to the physical world... This may take a moment.");
                plugin.getRegionManager().applyStateToPhysicalWorld(finalRegion, finalState);
            })
            .exceptionally(ex -> {
                plugin.getLogger().severe("Failed to save region reality for region " + finalRegion.getName() + ": " + ex.getMessage());
                sender.sendMessage(ChatColor.RED + "Failed to save region reality: " + ex.getMessage());
                return null;
            });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "delete", "list", "info", "state", "switch", "reality", "stick", "stats", "refresh", "reload", "setregionreality", "reloadconfig")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("create") || subCommand.equals("stick") || subCommand.equals("list") || subCommand.equals("stats") || subCommand.equals("reload")) {
                return new ArrayList<>();
            } else if (subCommand.equals("refresh")) {
                return plugin.getRegionManager().getAllRegions()
                        .stream()
                        .map(QuantumRegion::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("state")) {
                return Arrays.asList("create", "delete", "capture")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("setregionreality")) {
                return plugin.getRegionManager().getAllRegions()
                        .stream()
                        .map(QuantumRegion::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else {
                return plugin.getRegionManager().getAllRegions()
                        .stream()
                        .map(QuantumRegion::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String regionName = args[1];
            QuantumRegion region = plugin.getRegionManager().getRegionByName(regionName);

            if (region != null && subCommand.equals("switch")) {
                return region.getStateNames()
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (subCommand.equals("state")) {
                // This case seems to be for /qc state <action> <region_name_for_action>
                // For /qc state create <region_to_create_state_in> <new_state_name>
                // It should suggest region names for args[2] if args[1] is create/delete/capture
                 return plugin.getRegionManager().getAllRegions()
                        .stream()
                        .map(QuantumRegion::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (region != null && subCommand.equals("setregionreality")) {
                return region.getStateNames()
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("state")) {
            // This is for /qc state <action> <region> <state_name_for_action>
            String regionName = args[2]; // region name is args[2] here
            QuantumRegion region = plugin.getRegionManager().getRegionByName(regionName);

            if (region != null) {
                return region.getStateNames()
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
