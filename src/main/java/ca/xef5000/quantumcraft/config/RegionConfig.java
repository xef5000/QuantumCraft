package ca.xef5000.quantumcraft.config;

import ca.xef5000.quantumcraft.conditions.StateCondition;
import ca.xef5000.quantumcraft.region.QuantumRegion;
import ca.xef5000.quantumcraft.region.RegionState;
import ca.xef5000.quantumcraft.util.RegionBounds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.logging.Logger;

/**
 * Handles loading and managing region configurations from regions.yml
 */
public class RegionConfig {
    private final Logger logger;
    private final Map<String, RegionConfigData> regionConfigs;
    private GlobalSettings globalSettings;

    public RegionConfig(Logger logger) {
        this.logger = logger;
        this.regionConfigs = new HashMap<>();
    }

    /**
     * Loads region configurations from the given file configuration.
     *
     * @param config The file configuration to load from
     */
    public void loadFromConfig(FileConfiguration config) {
        regionConfigs.clear();
        
        // Load global settings
        loadGlobalSettings(config);
        
        // Load regions
        ConfigurationSection regionsSection = config.getConfigurationSection("regions");
        if (regionsSection == null) {
            logger.warning("No regions section found in regions.yml");
            return;
        }

        for (String regionId : regionsSection.getKeys(false)) {
            try {
                ConfigurationSection regionSection = regionsSection.getConfigurationSection(regionId);
                if (regionSection != null) {
                    RegionConfigData regionData = loadRegionConfig(regionId, regionSection);
                    regionConfigs.put(regionId, regionData);
                    logger.info("Loaded region configuration: " + regionId);
                }
            } catch (Exception e) {
                logger.severe("Failed to load region configuration for " + regionId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        logger.info("Loaded " + regionConfigs.size() + " region configurations");
    }

    private void loadGlobalSettings(FileConfiguration config) {
        ConfigurationSection globalSection = config.getConfigurationSection("global_settings");
        if (globalSection == null) {
            globalSettings = new GlobalSettings();
            return;
        }

        globalSettings = new GlobalSettings(
            globalSection.getString("multi_state_resolution", "highest_priority"),
            globalSection.getInt("condition_check_interval", 100),
            globalSection.getBoolean("debug_state_changes", false),
            globalSection.getString("default_entry_message", "&7You have entered a quantum region..."),
            globalSection.getString("default_exit_message", "&7You have left the quantum region."),
            globalSection.getInt("max_regions_per_check", 5),
            globalSection.getString("quest_plugin", "Quests"),
            globalSection.getString("economy_plugin", "Vault")
        );
    }

    private RegionConfigData loadRegionConfig(String regionId, ConfigurationSection section) {
        String name = section.getString("name", regionId);
        String worldName = section.getString("world");
        int priority = section.getInt("priority", 0);
        String defaultStateId = section.getString("default_state_id", "REALITY");
        String entryMessage = section.getString("entry_message");
        String exitMessage = section.getString("exit_message");

        if (worldName == null) {
            throw new IllegalArgumentException("Region " + regionId + " must specify a world");
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("World " + worldName + " not found for region " + regionId);
        }

        // Load bounds
        ConfigurationSection minSection = section.getConfigurationSection("min_point");
        ConfigurationSection maxSection = section.getConfigurationSection("max_point");
        
        if (minSection == null || maxSection == null) {
            throw new IllegalArgumentException("Region " + regionId + " must specify min_point and max_point");
        }

        Vector min = new Vector(
            minSection.getDouble("x"),
            minSection.getDouble("y"),
            minSection.getDouble("z")
        );
        Vector max = new Vector(
            maxSection.getDouble("x"),
            maxSection.getDouble("y"),
            maxSection.getDouble("z")
        );

        RegionBounds bounds = new RegionBounds(world, min, max);

        // Load region settings
        RegionSettings settings = loadRegionSettings(section.getConfigurationSection("settings"));

        // Load states
        Map<String, StateConfigData> states = new HashMap<>();
        ConfigurationSection statesSection = section.getConfigurationSection("states");
        if (statesSection != null) {
            for (String stateId : statesSection.getKeys(false)) {
                ConfigurationSection stateSection = statesSection.getConfigurationSection(stateId);
                if (stateSection != null) {
                    StateConfigData stateData = loadStateConfig(stateId, stateSection);
                    states.put(stateId, stateData);
                }
            }
        }

        return new RegionConfigData(regionId, name, bounds, priority, defaultStateId, 
                                   entryMessage, exitMessage, settings, states);
    }

    private RegionSettings loadRegionSettings(ConfigurationSection section) {
        if (section == null) {
            return new RegionSettings();
        }

        return new RegionSettings(
            section.getInt("state_refresh_interval", globalSettings.conditionCheckInterval),
            section.getBoolean("announce_state_changes", true),
            section.getString("transition_effect", "none"),
            section.getInt("transition_duration", 20),
            section.getBoolean("require_line_of_sight", false)
        );
    }

    private StateConfigData loadStateConfig(String stateId, ConfigurationSection section) {
        String name = section.getString("name", stateId);
        String description = section.getString("description", "");
        String icon = section.getString("icon", "minecraft:stone");
        int priority = section.getInt("priority", 1);

        // Load conditions
        List<StateCondition> conditions = new ArrayList<>();
        if (section.contains("unlock_conditions")) {
            List<Map<?, ?>> conditionMaps = section.getMapList("unlock_conditions");
            for (Map<?, ?> conditionMap : conditionMaps) {
                try {
                    ConfigurationSection conditionSection = section.createSection("temp", conditionMap);
                    StateCondition condition = StateCondition.fromConfig(conditionSection);
                    conditions.add(condition);
                } catch (Exception e) {
                    logger.warning("Failed to load condition for state " + stateId + ": " + e.getMessage());
                }
            }
        }

        // Load commands
        List<String> onEnterCommands = section.getStringList("on_enter_commands");
        List<String> onExitCommands = section.getStringList("on_exit_commands");

        // Load ambient effects
        AmbientEffects ambientEffects = loadAmbientEffects(section.getConfigurationSection("ambient_effects"));

        return new StateConfigData(stateId, name, description, icon, priority, 
                                  conditions, onEnterCommands, onExitCommands, ambientEffects);
    }

    private AmbientEffects loadAmbientEffects(ConfigurationSection section) {
        if (section == null) {
            return new AmbientEffects();
        }

        List<SoundEffect> sounds = new ArrayList<>();
        List<ParticleEffect> particles = new ArrayList<>();

        if (section.contains("sounds")) {
            List<Map<?, ?>> soundMaps = section.getMapList("sounds");
            for (Map<?, ?> soundMap : soundMaps) {
                Object volumeObj = soundMap.get("volume");
                Object pitchObj = soundMap.get("pitch");

                float volume = volumeObj instanceof Number ? ((Number) volumeObj).floatValue() : 1.0f;
                float pitch = pitchObj instanceof Number ? ((Number) pitchObj).floatValue() : 1.0f;

                sounds.add(new SoundEffect(
                    (String) soundMap.get("sound"),
                    volume,
                    pitch
                ));
            }
        }

        if (section.contains("particles")) {
            List<Map<?, ?>> particleMaps = section.getMapList("particles");
            for (Map<?, ?> particleMap : particleMaps) {
                Object countObj = particleMap.get("count");
                Object intervalObj = particleMap.get("interval");

                int count = countObj instanceof Number ? ((Number) countObj).intValue() : 1;
                int interval = intervalObj instanceof Number ? ((Number) intervalObj).intValue() : 100;

                particles.add(new ParticleEffect(
                    (String) particleMap.get("type"),
                    count,
                    interval
                ));
            }
        }

        return new AmbientEffects(sounds, particles);
    }

    /**
     * Creates QuantumRegion objects from the loaded configurations.
     *
     * @return A map of region ID to QuantumRegion
     */
    public Map<String, QuantumRegion> createQuantumRegions() {
        Map<String, QuantumRegion> regions = new HashMap<>();

        for (RegionConfigData configData : regionConfigs.values()) {
            QuantumRegion region = new QuantumRegion(
                configData.id,
                configData.name,
                configData.bounds
            );

            // Create states for the region
            for (StateConfigData stateData : configData.states.values()) {
                RegionState state = region.createState(stateData.id);
                // Additional state setup would go here
            }

            // Set default state if it exists
            if (configData.states.containsKey(configData.defaultStateId)) {
                region.setDefaultState(configData.defaultStateId);
            }

            regions.put(configData.id, region);
        }

        return regions;
    }

    // Getters
    public Map<String, RegionConfigData> getRegionConfigs() { return regionConfigs; }
    public GlobalSettings getGlobalSettings() { return globalSettings; }
    public RegionConfigData getRegionConfig(String regionId) { return regionConfigs.get(regionId); }

    // Data classes
    public static class GlobalSettings {
        public final String multiStateResolution;
        public final int conditionCheckInterval;
        public final boolean debugStateChanges;
        public final String defaultEntryMessage;
        public final String defaultExitMessage;
        public final int maxRegionsPerCheck;
        public final String questPlugin;
        public final String economyPlugin;

        public GlobalSettings() {
            this("highest_priority", 100, false, "&7You have entered a quantum region...", 
                 "&7You have left the quantum region.", 5, "Quests", "Vault");
        }

        public GlobalSettings(String multiStateResolution, int conditionCheckInterval, boolean debugStateChanges,
                            String defaultEntryMessage, String defaultExitMessage, int maxRegionsPerCheck,
                            String questPlugin, String economyPlugin) {
            this.multiStateResolution = multiStateResolution;
            this.conditionCheckInterval = conditionCheckInterval;
            this.debugStateChanges = debugStateChanges;
            this.defaultEntryMessage = defaultEntryMessage;
            this.defaultExitMessage = defaultExitMessage;
            this.maxRegionsPerCheck = maxRegionsPerCheck;
            this.questPlugin = questPlugin;
            this.economyPlugin = economyPlugin;
        }
    }

    public static class RegionConfigData {
        public final String id;
        public final String name;
        public final RegionBounds bounds;
        public final int priority;
        public final String defaultStateId;
        public final String entryMessage;
        public final String exitMessage;
        public final RegionSettings settings;
        public final Map<String, StateConfigData> states;

        public RegionConfigData(String id, String name, RegionBounds bounds, int priority, String defaultStateId,
                               String entryMessage, String exitMessage, RegionSettings settings,
                               Map<String, StateConfigData> states) {
            this.id = id;
            this.name = name;
            this.bounds = bounds;
            this.priority = priority;
            this.defaultStateId = defaultStateId;
            this.entryMessage = entryMessage;
            this.exitMessage = exitMessage;
            this.settings = settings;
            this.states = states;
        }
    }

    public static class RegionSettings {
        public final int stateRefreshInterval;
        public final boolean announceStateChanges;
        public final String transitionEffect;
        public final int transitionDuration;
        public final boolean requireLineOfSight;

        public RegionSettings() {
            this(100, true, "none", 20, false);
        }

        public RegionSettings(int stateRefreshInterval, boolean announceStateChanges, String transitionEffect,
                             int transitionDuration, boolean requireLineOfSight) {
            this.stateRefreshInterval = stateRefreshInterval;
            this.announceStateChanges = announceStateChanges;
            this.transitionEffect = transitionEffect;
            this.transitionDuration = transitionDuration;
            this.requireLineOfSight = requireLineOfSight;
        }
    }

    public static class StateConfigData {
        public final String id;
        public final String name;
        public final String description;
        public final String icon;
        public final int priority;
        public final List<StateCondition> conditions;
        public final List<String> onEnterCommands;
        public final List<String> onExitCommands;
        public final AmbientEffects ambientEffects;

        public StateConfigData(String id, String name, String description, String icon, int priority,
                              List<StateCondition> conditions, List<String> onEnterCommands,
                              List<String> onExitCommands, AmbientEffects ambientEffects) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.icon = icon;
            this.priority = priority;
            this.conditions = conditions;
            this.onEnterCommands = onEnterCommands;
            this.onExitCommands = onExitCommands;
            this.ambientEffects = ambientEffects;
        }
    }

    public static class AmbientEffects {
        public final List<SoundEffect> sounds;
        public final List<ParticleEffect> particles;

        public AmbientEffects() {
            this(new ArrayList<>(), new ArrayList<>());
        }

        public AmbientEffects(List<SoundEffect> sounds, List<ParticleEffect> particles) {
            this.sounds = sounds;
            this.particles = particles;
        }
    }

    public static class SoundEffect {
        public final String sound;
        public final float volume;
        public final float pitch;

        public SoundEffect(String sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public static class ParticleEffect {
        public final String type;
        public final int count;
        public final int interval;

        public ParticleEffect(String type, int count, int interval) {
            this.type = type;
            this.count = count;
            this.interval = interval;
        }
    }
}
