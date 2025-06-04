# QuantumCraft - Minecraft Quantum Superposition Plugin

QuantumCraft is a Minecraft plugin that implements quantum superposition concepts, allowing different players to see different states of the same world regions using ProtocolLib.

## Features

- **Quantum Regions**: Create regions that can exist in multiple states simultaneously
- **Player-Specific Views**: Different players can see different states of the same region
- **Reality Mode**: "Snap back to reality" to modify the actual server state
- **State Management**: Create, delete, and switch between different quantum states
- **Efficient Storage**: Compressed storage system for large regions
- **Selection Tools**: Easy-to-use selection tools for creating regions

## Commands

### Main Command: `/quantumcraft` (alias: `/qc`)

- `/qc create <name> [x1 y1 z1 x2 y2 z2]` - Create a new quantum region
- `/qc delete <region>` - Delete a quantum region
- `/qc list` - List all quantum regions
- `/qc info <region>` - Show detailed information about a region
- `/qc stick` - Get the selection tool for creating regions

### State Management

- `/qc state create <region> <state>` - Create a new state for a region
- `/qc state delete <region> <state>` - Delete a state from a region
- `/qc state capture <region> <state>` - Capture the current blocks into a state
- `/qc switch <region> <state>` - Switch to viewing a specific state

### Reality Mode

- `/qc reality` - Enter reality mode for all regions (see actual server state)
- `/qc reality <region>` - Enter reality mode for a specific region
- `/qc setregionreality <region> <state>` - Sets the default 'reality' state for a region

### Statistics & Utilities

- `/qc stats` - Show plugin statistics and memory usage
- `/qc refresh [region]` - Manually refresh quantum regions (useful for testing)
- `/qc reload` - Reload all regions from disk (useful after server restart issues)
- `/qc reloadconfig` - Reload regions.yml configuration (useful for testing)
- `/qc update [player]` - Force update a player's quantum state

## How It Works

1. **Create a Region**: Use `/qc stick` to get a selection tool, select two corners, then `/qc create <name>`
2. **Capture States**: Use `/qc state capture <region> default` to capture the current state
3. **Modify Reality**: Use `/qc reality <region>` to enter reality mode and modify blocks
4. **Create New States**: Create a new state with `/qc state create <region> <newstate>`
5. **Capture New State**: Use `/qc capture <region> <newstate>` to save the modified blocks
6. **Switch Views**: Use `/qc switch <region> <state>` to see different versions
7. **Automatic Updates**: Configure `regions.yml` to automatically manage states based on conditions

### Server Restart Handling

The plugin now properly handles server restarts:
- **Synchronous Loading**: Regions are loaded synchronously during server startup to ensure they're available before players join
- **Complete State Restoration**: All block data is properly saved and restored, including compressed states
- **Automatic Player Refresh**: All online players have their quantum states refreshed after a reload
- **Reload Command**: Use `/qc reload` if regions don't appear to load correctly after a restart

## Example Usage

```
# Get selection tool
/qc stick

# Select two corners by left/right clicking blocks

# Create a region called "village"
/qc create village

# Capture the current state as "default"
/qc capture village default

# Enter reality mode to modify blocks
/qc reality village

# Build something different...

# Create a new state called "ruins"
/qc state create village ruins

# Capture the new state
/qc capture village ruins

# Now you can switch between states:
/qc switch village default  # See the original village
/qc switch village ruins    # See the ruined version

# Configure regions.yml
# Add unlock conditions, on-enter commands, etc.
```

## Permissions

- `quantumcraft.admin` - Access to all QuantumCraft commands (default: op)

## Configuration

The plugin creates a `config.yml` file with the following options:

```yaml
# Storage settings
storage:
  data-directory: "regions"
  enable-compression: true
  auto-save-interval: 5

# Performance settings
performance:
  max-chunks-per-tick: 10
  max-blocks-per-packet: 1000
  enable-async-processing: true

# Debug settings
debug:
  enable-debug: false
  log-packets: false
  log-regions: false

# Placeholder settings
placeholders:
  not_in_region_message: "&7Not in a quantum region"
  unknown_state_message: "&7Unknown quantum state"
```

## Dependencies

- **ProtocolLib** - Required for packet manipulation
- **Paper/Spigot 1.20.1** - Minecraft server

## Installation

1. Download ProtocolLib and place it in your plugins folder
2. Download QuantumCraft.jar and place it in your plugins folder
3. Restart your server
4. Configure the plugin as needed

## Technical Details

- Uses ProtocolLib to intercept and modify block change packets
- Implements efficient compression for storing large regions
- Supports overlapping regions with priority handling
- Async processing for better performance with large regions
- Delta compression to minimize storage space

## Limitations

- Large regions may impact performance during state switches
- Players must be in reality mode to modify blocks in quantum regions
- Overlapping regions use the first region found for packet modification

## Support

For issues, suggestions, or contributions, please create an issue on the project repository.
