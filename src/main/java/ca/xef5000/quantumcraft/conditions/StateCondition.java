package ca.xef5000.quantumcraft.conditions;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Represents a condition that must be met for a player to access a quantum state.
 */
public class StateCondition {
    private final ConditionType type;
    private final Map<String, Object> parameters;
    private final List<StateCondition> subConditions;
    private final String logic; // AND, OR for group conditions

    /**
     * Creates a new StateCondition.
     *
     * @param type The type of condition
     * @param parameters The parameters for this condition
     * @param subConditions Sub-conditions for group conditions
     * @param logic The logic operator for group conditions (AND/OR)
     */
    public StateCondition(ConditionType type, Map<String, Object> parameters, 
                         List<StateCondition> subConditions, String logic) {
        this.type = type;
        this.parameters = parameters;
        this.subConditions = subConditions;
        this.logic = logic;
    }

    /**
     * Creates a StateCondition from a configuration section.
     *
     * @param config The configuration section
     * @return The created StateCondition
     */
    public static StateCondition fromConfig(ConfigurationSection config) {
        String typeString = config.getString("type");
        ConditionType type = ConditionType.fromConfigName(typeString);
        
        if (type == null) {
            throw new IllegalArgumentException("Unknown condition type: " + typeString);
        }

        Map<String, Object> parameters = config.getValues(false);
        parameters.remove("type"); // Remove the type from parameters
        
        List<StateCondition> subConditions = null;
        String logic = null;
        
        // Handle group conditions
        if (type == ConditionType.GROUP_CONDITION || type == ConditionType.ALL_CONDITIONS) {
            logic = config.getString("logic", "AND");
            if (config.contains("conditions")) {
                subConditions = config.getMapList("conditions").stream()
                    .map(map -> {
                        ConfigurationSection subConfig = config.createSection("temp", map);
                        return fromConfig(subConfig);
                    })
                    .toList();
            }
        }

        return new StateCondition(type, parameters, subConditions, logic);
    }

    /**
     * Checks if this condition is met by the given player.
     *
     * @param player The player to check
     * @param evaluator The condition evaluator to use
     * @return true if the condition is met
     */
    public boolean isMet(Player player, ConditionEvaluator evaluator) {
        return evaluator.evaluate(this, player);
    }

    // Getters
    public ConditionType getType() { return type; }
    public Map<String, Object> getParameters() { return parameters; }
    public List<StateCondition> getSubConditions() { return subConditions; }
    public String getLogic() { return logic; }

    /**
     * Gets a parameter value as a string.
     */
    public String getString(String key) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets a parameter value as an integer.
     */
    public int getInt(String key, int defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Gets a parameter value as a long.
     */
    public long getLong(String key, long defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Gets a parameter value as a double.
     */
    public double getDouble(String key, double defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Gets a parameter value as a boolean.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        try {
            return Boolean.parseBoolean(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return String.format("StateCondition{type=%s, parameters=%s}", type, parameters);
    }
}
