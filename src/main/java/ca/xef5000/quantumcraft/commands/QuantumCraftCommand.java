package ca.xef5000.quantumcraft.commands;

import ca.xef5000.quantumcraft.QuantumCraft;
import ca.xef5000.quantumcraft.manager.RegionManager;
import ca.xef5000.quantumcraft.protocol.PacketManager;
import ca.xef5000.quantumcraft.region.Region;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles commands for the FakeRegion plugin.
 */
public class QuantumCraftCommand implements CommandExecutor, TabCompleter {
    private final QuantumCraft plugin;

    /**
     * Creates a new FakeRegionCommand.
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

        switch (args[0].toLowerCase()) {
            case "region":
                return handleRegionCommand(sender, args);
            case "version":
                return handleVersionCommand(sender, args);
            case "select":
                return handleSelectCommand(sender, args);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Type /qc help for help.");
                return true;
        }
    }

    /**
     * Handles the region subcommand.
     */
    private boolean handleRegionCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc region <create|delete> [args]");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "create":
                return handleRegionCreateCommand(sender, args);
            case "delete":
                return handleRegionDeleteCommand(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown region command. Valid options: create, delete");
                return true;
        }
    }

    /**
     * Handles the region create command.
     */
    private boolean handleRegionCreateCommand(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("qc.admin.region.create")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to create regions.");
            return true;
        }

        // Check if sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        // Check arguments
        if (args.length != 9) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc region create <regionID> <x1> <y1> <z1> <x2> <y2> <z2>");
            return true;
        }

        Player player = (Player) sender;
        String regionId = args[2];
        
        // Parse coordinates
        try {
            int x1 = Integer.parseInt(args[3]);
            int y1 = Integer.parseInt(args[4]);
            int z1 = Integer.parseInt(args[5]);
            int x2 = Integer.parseInt(args[6]);
            int y2 = Integer.parseInt(args[7]);
            int z2 = Integer.parseInt(args[8]);
            
            Vector min = new Vector(x1, y1, z1);
            Vector max = new Vector(x2, y2, z2);
            World world = player.getWorld();
            
            // Create the region
            RegionManager regionManager = plugin.getRegionManager();
            boolean success = regionManager.createRegion(regionId, world, min, max);
            
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Region '" + regionId + "' created successfully.");
            } else {
                sender.sendMessage(ChatColor.RED + "A region with that ID already exists.");
            }
            
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates. Please enter valid numbers.");
            return true;
        }
    }

    /**
     * Handles the region delete command.
     */
    private boolean handleRegionDeleteCommand(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("qc.admin.region.delete")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to delete regions.");
            return true;
        }

        // Check arguments
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc region delete <regionID>");
            return true;
        }

        String regionId = args[2];
        
        // Delete the region
        RegionManager regionManager = plugin.getRegionManager();
        boolean success = regionManager.deleteRegion(regionId);
        
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Region '" + regionId + "' deleted successfully.");
        } else {
            sender.sendMessage(ChatColor.RED + "No region with that ID exists.");
        }
        
        return true;
    }

    /**
     * Handles the version subcommand.
     */
    private boolean handleVersionCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc version <add|remove> [args]");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "add":
                return handleVersionAddCommand(sender, args);
            case "remove":
                return handleVersionRemoveCommand(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown version command. Valid options: add, remove");
                return true;
        }
    }

    /**
     * Handles the version add command.
     */
    private boolean handleVersionAddCommand(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("qc.admin.version.add")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to add versions.");
            return true;
        }

        // Check arguments
        if (args.length != 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc version add <regionID> <versionName>");
            return true;
        }

        String regionId = args[2];
        String versionName = args[3];
        
        // Add the version
        RegionManager regionManager = plugin.getRegionManager();
        boolean success = regionManager.addVersion(regionId, versionName);
        
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Version '" + versionName + "' added to region '" + regionId + "' successfully.");
        } else {
            sender.sendMessage(ChatColor.RED + "No region with that ID exists.");
        }
        
        return true;
    }

    /**
     * Handles the version remove command.
     */
    private boolean handleVersionRemoveCommand(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("qc.admin.version.remove")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to remove versions.");
            return true;
        }

        // Check arguments
        if (args.length != 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc version remove <regionID> <versionName>");
            return true;
        }

        String regionId = args[2];
        String versionName = args[3];
        
        // Remove the version
        RegionManager regionManager = plugin.getRegionManager();
        boolean success = regionManager.removeVersion(regionId, versionName);
        
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Version '" + versionName + "' removed from region '" + regionId + "' successfully.");
        } else {
            sender.sendMessage(ChatColor.RED + "No region with that ID exists, or the version doesn't exist.");
        }
        
        return true;
    }

    /**
     * Handles the select command.
     */
    private boolean handleSelectCommand(CommandSender sender, String[] args) {
        // Check if sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        // Check arguments
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /qc select <regionID> <versionName>");
            return true;
        }

        Player player = (Player) sender;
        String regionId = args[1];
        String versionName = args[2];
        
        // Check if the region exists
        RegionManager regionManager = plugin.getRegionManager();
        Region region = regionManager.getRegion(regionId);
        
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "No region with that ID exists.");
            return true;
        }
        
        // Check if the version exists
        if (region.getVersion(versionName) == null) {
            sender.sendMessage(ChatColor.RED + "No version with that name exists for this region.");
            return true;
        }
        
        // Set the player's version
        plugin.getPlayerManager().setPlayerVersion(player, regionId, versionName);
        
        // Send the region version to the player if they're in the region
        if (region.contains(player.getLocation())) {
            PacketManager packetManager = plugin.getPacketManager();
            packetManager.sendRegionVersion(player, region, region.getVersion(versionName));
        }
        
        sender.sendMessage(ChatColor.GREEN + "You are now viewing version '" + versionName + "' of region '" + regionId + "'.");
        return true;
    }

    /**
     * Sends help information to the sender.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== QuantumCraft Help ===");
        sender.sendMessage(ChatColor.YELLOW + "/qc region create <regionID> <x1> <y1> <z1> <x2> <y2> <z2>" + ChatColor.WHITE + " - Create a new region");
        sender.sendMessage(ChatColor.YELLOW + "/qc region delete <regionID>" + ChatColor.WHITE + " - Delete a region");
        sender.sendMessage(ChatColor.YELLOW + "/qc version add <regionID> <versionName>" + ChatColor.WHITE + " - Add a version to a region");
        sender.sendMessage(ChatColor.YELLOW + "/qc version remove <regionID> <versionName>" + ChatColor.WHITE + " - Remove a version from a region");
        sender.sendMessage(ChatColor.YELLOW + "/qc select <regionID> <versionName>" + ChatColor.WHITE + " - Select a version to view");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStartingWith(args[0], Arrays.asList("region", "version", "select", "help"));
        }
        
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "region":
                    return filterStartingWith(args[1], Arrays.asList("create", "delete"));
                case "version":
                    return filterStartingWith(args[1], Arrays.asList("add", "remove"));
                case "select":
                    return getRegionIds();
            }
        }
        
        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "region":
                    if (args[1].equalsIgnoreCase("delete")) {
                        return getRegionIds();
                    }
                    break;
                case "version":
                    return getRegionIds();
                case "select":
                    return getVersionNames(args[2]);
            }
        }
        
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("version")) {
                return getVersionNames(args[2]);
            }
        }
        
        return Collections.emptyList();
    }

    /**
     * Filters a list of strings to only include those starting with the given prefix.
     */
    private List<String> filterStartingWith(String prefix, List<String> options) {
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Gets a list of all region IDs.
     */
    private List<String> getRegionIds() {
        RegionManager regionManager = plugin.getRegionManager();
        return new ArrayList<>(regionManager.getRegions().keySet());
    }

    /**
     * Gets a list of all version names for a region.
     */
    private List<String> getVersionNames(String regionId) {
        RegionManager regionManager = plugin.getRegionManager();
        Region region = regionManager.getRegion(regionId);
        
        if (region == null) {
            return Collections.emptyList();
        }
        
        return new ArrayList<>(region.getVersions().keySet());
    }
}