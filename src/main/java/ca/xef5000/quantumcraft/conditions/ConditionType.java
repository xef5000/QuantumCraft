package ca.xef5000.quantumcraft.conditions;

/**
 * Enum representing different types of conditions that can be used
 * to determine if a player should see a particular quantum state.
 */
public enum ConditionType {
    PERMISSION("permission"),
    QUEST_COMPLETED("quest_completed"),
    ITEM_POSSESSED("item_possessed"),
    PLAYER_STATISTIC("player_statistic"),
    ADVANCEMENT_ACHIEVED("advancement_achieved"),
    GAME_TIME("game_time"),
    TIME_RANGE("time_range"),
    TIME_CONDITION("time_condition"),
    WEATHER_CONDITION("weather_condition"),
    ECONOMY_BALANCE("economy_balance"),
    PLACEHOLDER_API("placeholder_api"),
    GROUP_CONDITION("group_condition"),
    ALL_CONDITIONS("all_conditions"),
    PLAYER_LEVEL("player_level"),
    WORLD_CONDITION("world_condition"),
    BIOME_CONDITION("biome_condition"),
    CUSTOM_CONDITION("custom_condition");

    private final String configName;

    ConditionType(String configName) {
        this.configName = configName;
    }

    public String getConfigName() {
        return configName;
    }

    /**
     * Gets a ConditionType from its config name.
     *
     * @param configName The name used in configuration
     * @return The corresponding ConditionType, or null if not found
     */
    public static ConditionType fromConfigName(String configName) {
        for (ConditionType type : values()) {
            if (type.configName.equals(configName)) {
                return type;
            }
        }
        return null;
    }
}
