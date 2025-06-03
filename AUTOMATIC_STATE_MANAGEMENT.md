# QuantumCraft Automatic State Management System

## Overview

The QuantumCraft plugin now features a comprehensive automatic state management system that eliminates the need for players to manually switch between quantum states. The server automatically determines which state each player should see based on configurable conditions.

## Key Features

### 🤖 **Fully Automatic**
- Players never need to manually switch states
- Server continuously evaluates conditions and updates player views
- Seamless transitions between states based on player progression

### 🎯 **Condition-Based State Selection**
- Rich condition system supporting multiple criteria types
- Logical operators (AND/OR) for complex condition combinations
- Priority-based resolution when multiple states are available

### ⚡ **Performance Optimized**
- Configurable check intervals to balance responsiveness vs performance
- Async processing for large regions
- Batched updates to prevent client lag

### 🔧 **Highly Configurable**
- Global settings for default behaviors
- Per-region customization options
- Hot-reloadable configuration without server restart

## Configuration Structure

### Global Settings (`regions.yml`)

```yaml
global_settings:
  # How to resolve when multiple states are unlocked
  multi_state_resolution: "highest_priority"  # Options: highest_priority, random, cycle_time
  
  # How often to check conditions (ticks)
  condition_check_interval: 100
  
  # Performance limits
  max_regions_per_check: 5
  
  # Debug options
  debug_state_changes: false
```

### Region Configuration

```yaml
regions:
  example_region:
    name: "Example Region"
    world: "world"
    min_point: {x: 100, y: 64, z: 200}
    max_point: {x: 150, y: 100, z: 250}
    priority: 10
    default_state_id: "peaceful_day"
    
    settings:
      state_refresh_interval: 200  # Region-specific check interval
      announce_state_changes: true
      transition_effect: "fade_black"
      transition_duration: 20
    
    states:
      state_name:
        priority: 5
        unlock_conditions: [...]
        on_enter_commands: [...]
        ambient_effects: [...]
```

## Condition Types

### Basic Conditions

#### Permission Check
```yaml
- type: "permission"
  value: "quantumcraft.festival.access"
```

#### Quest Completion
```yaml
- type: "quest_completed"
  value: "main_story_chapter_3"
  quest_plugin: "Quests"  # Optional, uses global setting if not specified
```

#### Item Possession
```yaml
- type: "item_possessed"
  material: "minecraft:ancient_amulet"
  amount: 1
  name_contains: "Glowing"  # Optional
  enchantments:  # Optional
    - "minecraft:curse_of_vanishing"
```

#### Player Statistics
```yaml
- type: "player_statistic"
  statistic: "minecraft:custom:minecraft:mob_kills"
  value: 100
  comparison: "greater_than"  # greater_than, greater_than_or_equal, less_than, less_than_or_equal, equal
```

#### Advancement Achievement
```yaml
- type: "advancement_achieved"
  value: "minecraft:story/enter_the_nether"
```

### Time-Based Conditions

#### Game Time
```yaml
- type: "game_time"
  min_ticks: 13000  # Night time
  max_ticks: 23000
```

#### Real Time
```yaml
- type: "time_condition"
  real_time: true
  start_time: "23:00"
  end_time: "05:00"
```

### Environmental Conditions

#### Weather
```yaml
- type: "weather_condition"
  weather: "STORM"  # CLEAR, RAIN, STORM/THUNDER
```

#### World Check
```yaml
- type: "world_condition"
  world: "world_nether"
```

#### Biome Check
```yaml
- type: "biome_condition"
  biome: "minecraft:dark_forest"
```

### Integration Conditions

#### Economy Balance (Vault)
```yaml
- type: "economy_balance"
  amount: 10000
  comparison: "greater_than_or_equal"
```

#### PlaceholderAPI
```yaml
- type: "placeholder_api"
  placeholder: "%player_level%"
  value: "20"
  comparison: "greater_than_or_equal"
```

#### Player Level
```yaml
- type: "player_level"
  level: 30
  comparison: "greater_than_or_equal"
```

### Logical Conditions

#### Group Condition (OR Logic)
```yaml
- type: "group_condition"
  logic: "OR"
  conditions:
    - type: "permission"
      value: "quantumcraft.temple.awakener"
    - type: "economy_balance"
      amount: 10000
      comparison: "greater_than_or_equal"
```

#### All Conditions (AND Logic)
```yaml
- type: "all_conditions"
  conditions:
    - type: "quest_completed"
      value: "temple_master_quest"
    - type: "advancement_achieved"
      value: "minecraft:end/kill_dragon"
    - type: "item_possessed"
      material: "minecraft:nether_star"
      amount: 1
```

## State Resolution

When multiple states are unlocked for a player, the system uses the configured resolution method:

### Highest Priority (Default)
```yaml
multi_state_resolution: "highest_priority"
```
Selects the state with the highest priority value.

### Random Selection
```yaml
multi_state_resolution: "random"
```
Randomly selects from all unlocked states.

### Time-Based Cycling
```yaml
multi_state_resolution: "cycle_time"
```
Cycles through unlocked states based on time (changes every minute).

## Commands

### Administrative Commands

#### Reload Configuration
```
/qc reloadconfig
```
Reloads the `regions.yml` file and restarts the automatic state management system.

#### Force State Check
```
/qc refresh [region]
```
Forces an immediate state check for all players (or specific region).

#### Manual State Override
```
/qc switch <region> <state>
```
Manually override automatic state selection (admin only).

#### Reality Mode
```
/qc reality [region]
```
Enter reality mode to see actual server blocks (for building/editing).

## Performance Tuning

### Global Performance Settings

```yaml
global_settings:
  condition_check_interval: 100    # Base check interval (ticks)
  max_regions_per_check: 5         # Limit regions checked per tick
```

### Region-Specific Settings

```yaml
settings:
  state_refresh_interval: 200      # Override global interval for this region
  require_line_of_sight: true      # Only update if player can see the region
```

### Optimization Tips

1. **Increase check intervals** for regions with complex conditions
2. **Use priority-based resolution** for better performance than random
3. **Limit the number of conditions** per state when possible
4. **Use async processing** for large regions (enabled by default)

## Troubleshooting

### Debug Mode
Enable debug logging to see state changes:
```yaml
global_settings:
  debug_state_changes: true
```

### Common Issues

#### States Not Switching
- Check condition syntax in `regions.yml`
- Verify required plugins (Vault, PlaceholderAPI, Quest plugins) are installed
- Use `/qc reloadconfig` after making changes

#### Performance Issues
- Increase `condition_check_interval`
- Reduce `max_regions_per_check`
- Simplify complex condition logic

#### Players Stuck in Wrong State
- Use `/qc refresh` to force immediate update
- Check if player meets conditions using debug mode
- Verify region boundaries are correct

## Integration Examples

### Quest Plugin Integration
```yaml
unlock_conditions:
  - type: "quest_completed"
    value: "story_quest_1"
    quest_plugin: "Quests"
```

### Economy Integration
```yaml
unlock_conditions:
  - type: "economy_balance"
    amount: 50000
    comparison: "greater_than_or_equal"
```

### PlaceholderAPI Integration
```yaml
unlock_conditions:
  - type: "placeholder_api"
    placeholder: "%mcmmo_power_level%"
    value: "1000"
    comparison: "greater_than"
```

## Best Practices

1. **Start Simple**: Begin with basic conditions and add complexity gradually
2. **Test Thoroughly**: Use debug mode to verify condition logic
3. **Performance First**: Monitor server performance when adding many regions
4. **Player Experience**: Ensure state transitions feel natural and meaningful
5. **Documentation**: Document your condition logic for other administrators

## Migration from Manual System

If upgrading from manual state switching:

1. **Backup existing regions** using `/qc reload`
2. **Create regions.yml** with your existing regions
3. **Add unlock conditions** for each state
4. **Test with debug mode** enabled
5. **Gradually migrate players** by removing manual overrides

The automatic system will take over seamlessly, providing a much better player experience!
