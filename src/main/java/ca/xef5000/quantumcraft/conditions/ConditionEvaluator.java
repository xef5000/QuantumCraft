package ca.xef5000.quantumcraft.conditions;

import ca.xef5000.quantumcraft.QuantumCraft;
import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Evaluates state conditions to determine if a player meets the requirements.
 */
public class ConditionEvaluator {
    private final QuantumCraft plugin;
    private Object economy; // Use Object to avoid dependency issues

    public ConditionEvaluator(QuantumCraft plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    /**
     * Evaluates a condition for a player.
     *
     * @param condition The condition to evaluate
     * @param player The player to check
     * @return true if the condition is met
     */
    public boolean evaluate(StateCondition condition, Player player) {
        try {
            return switch (condition.getType()) {
                case PERMISSION -> evaluatePermission(condition, player);
                case QUEST_COMPLETED -> evaluateQuestCompleted(condition, player);
                case ITEM_POSSESSED -> evaluateItemPossessed(condition, player);
                case PLAYER_STATISTIC -> evaluatePlayerStatistic(condition, player);
                case ADVANCEMENT_ACHIEVED -> evaluateAdvancementAchieved(condition, player);
                case GAME_TIME -> evaluateGameTime(condition, player);
                case TIME_RANGE, TIME_CONDITION -> evaluateTimeCondition(condition, player);
                case WEATHER_CONDITION -> evaluateWeatherCondition(condition, player);
                case ECONOMY_BALANCE -> evaluateEconomyBalance(condition, player);
                case PLACEHOLDER_API -> evaluatePlaceholderAPI(condition, player);
                case GROUP_CONDITION, ALL_CONDITIONS -> evaluateGroupCondition(condition, player);
                case PLAYER_LEVEL -> evaluatePlayerLevel(condition, player);
                case WORLD_CONDITION -> evaluateWorldCondition(condition, player);
                case BIOME_CONDITION -> evaluateBiomeCondition(condition, player);
                case CUSTOM_CONDITION -> evaluateCustomCondition(condition, player);
                default -> {
                    plugin.getLogger().warning("Unknown condition type: " + condition.getType());
                    yield false;
                }
            };
        } catch (Exception e) {
            plugin.getLogger().warning("Error evaluating condition " + condition.getType() + ": " + e.getMessage());
            if (plugin.getConfig().getBoolean("global_settings.debug_state_changes", false)) {
                e.printStackTrace();
            }
            return false;
        }
    }

    private boolean evaluatePermission(StateCondition condition, Player player) {
        String permission = condition.getString("value");
        return permission != null && player.hasPermission(permission);
    }

    private boolean evaluateQuestCompleted(StateCondition condition, Player player) {
        String questId = condition.getString("value");
        String questPlugin = condition.getString("quest_plugin");
        
        if (questPlugin == null) {
            questPlugin = plugin.getConfig().getString("global_settings.quest_plugin", "Quests");
        }

        // Integration with quest plugins would go here
        // For now, we'll use a placeholder system
        if (Bukkit.getPluginManager().isPluginEnabled(questPlugin)) {
            // This would integrate with the actual quest plugin
            // For demonstration, we'll use a simple permission-based check
            return player.hasPermission("quantumcraft.quest." + questId);
        }
        
        return false;
    }

    private boolean evaluateItemPossessed(StateCondition condition, Player player) {
        String materialName = condition.getString("material");
        int amount = condition.getInt("amount", 1);
        String nameContains = condition.getString("name_contains");
        
        if (materialName == null) return false;
        
        Material material = Material.matchMaterial(materialName);
        if (material == null) return false;

        int totalAmount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                // Check name if specified
                if (nameContains != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    if (!item.getItemMeta().getDisplayName().contains(nameContains)) {
                        continue;
                    }
                }
                
                // Check enchantments if specified
                @SuppressWarnings("unchecked")
                List<String> requiredEnchantments = (List<String>) condition.getParameters().get("enchantments");
                if (requiredEnchantments != null && !requiredEnchantments.isEmpty()) {
                    boolean hasAllEnchantments = true;
                    for (String enchantName : requiredEnchantments) {
                        // This would need proper enchantment checking
                        // For now, simplified check
                        if (!item.getEnchantments().keySet().stream()
                                .anyMatch(ench -> ench.getKey().getKey().equals(enchantName.replace("minecraft:", "")))) {
                            hasAllEnchantments = false;
                            break;
                        }
                    }
                    if (!hasAllEnchantments) continue;
                }
                
                totalAmount += item.getAmount();
            }
        }

        plugin.debug("Checking item:" + materialName +  ". evaluateItemPossessed: " + totalAmount + " >= " + amount);
        
        return totalAmount >= amount;
    }

    private boolean evaluatePlayerStatistic(StateCondition condition, Player player) {
        String statisticName = condition.getString("statistic");
        long value = condition.getLong("value", 0);
        String comparison = condition.getString("comparison");
        
        if (statisticName == null) return false;
        
        try {
            Statistic statistic = Statistic.valueOf(statisticName.replace("minecraft:custom:minecraft:", "").toUpperCase());
            int playerValue = player.getStatistic(statistic);
            
            return switch (comparison) {
                case "greater_than" -> playerValue > value;
                case "greater_than_or_equal" -> playerValue >= value;
                case "less_than" -> playerValue < value;
                case "less_than_or_equal" -> playerValue <= value;
                case "equal" -> playerValue == value;
                default -> playerValue >= value; // Default to greater_than_or_equal
            };
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid statistic: " + statisticName);
            return false;
        }
    }

    private boolean evaluateAdvancementAchieved(StateCondition condition, Player player) {
        String advancementName = condition.getString("value");
        if (advancementName == null) return false;
        
        Advancement advancement = Bukkit.getAdvancement(org.bukkit.NamespacedKey.fromString(advancementName));
        if (advancement == null) return false;
        
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        return progress.isDone();
    }

    private boolean evaluateGameTime(StateCondition condition, Player player) {
        long minTicks = condition.getLong("min_ticks", 0);
        long maxTicks = condition.getLong("max_ticks", 24000);
        
        long currentTime = player.getWorld().getTime();
        return currentTime >= minTicks && currentTime <= maxTicks;
    }

    private boolean evaluateTimeCondition(StateCondition condition, Player player) {
        boolean realTime = condition.getBoolean("real_time", false);
        String startTimeStr = condition.getString("start_time");
        String endTimeStr = condition.getString("end_time");
        
        if (startTimeStr == null || endTimeStr == null) return false;
        
        if (realTime) {
            LocalTime now = LocalTime.now();
            LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime endTime = LocalTime.parse(endTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            
            if (startTime.isAfter(endTime)) {
                // Crosses midnight
                return now.isAfter(startTime) || now.isBefore(endTime);
            } else {
                return now.isAfter(startTime) && now.isBefore(endTime);
            }
        } else {
            // Game time
            long currentTime = player.getWorld().getTime();
            long startTicks = timeStringToTicks(startTimeStr);
            long endTicks = timeStringToTicks(endTimeStr);
            
            if (startTicks > endTicks) {
                // Crosses midnight
                return currentTime >= startTicks || currentTime <= endTicks;
            } else {
                return currentTime >= startTicks && currentTime <= endTicks;
            }
        }
    }

    private boolean evaluateWeatherCondition(StateCondition condition, Player player) {
        String weather = condition.getString("weather");
        if (weather == null) return false;
        
        return switch (weather.toUpperCase()) {
            case "CLEAR" -> !player.getWorld().hasStorm() && !player.getWorld().isThundering();
            case "RAIN" -> player.getWorld().hasStorm() && !player.getWorld().isThundering();
            case "STORM", "THUNDER" -> player.getWorld().isThundering();
            default -> false;
        };
    }

    private boolean evaluateEconomyBalance(StateCondition condition, Player player) {
        if (economy == null) return false;

        try {
            // Use reflection to call economy methods to avoid dependency issues
            double amount = condition.getDouble("amount", 0);
            String comparison = condition.getString("comparison");

            // Get balance using reflection
            double balance = (Double) economy.getClass().getMethod("getBalance", Player.class).invoke(economy, player);

            return switch (comparison) {
                case "greater_than" -> balance > amount;
                case "greater_than_or_equal" -> balance >= amount;
                case "less_than" -> balance < amount;
                case "less_than_or_equal" -> balance <= amount;
                case "equal" -> balance == amount;
                default -> balance >= amount;
            };
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check economy balance: " + e.getMessage());
            return false;
        }
    }

    private boolean evaluatePlaceholderAPI(StateCondition condition, Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return false;
        }

        String placeholder = condition.getString("placeholder");
        String expectedValue = condition.getString("value");
        String comparison = condition.getString("comparison");

        if (placeholder == null || expectedValue == null) return false;

        try {
            // Use reflection to call PlaceholderAPI to avoid dependency issues
//            Class<?> placeholderAPIClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
//            String actualValue = (String) placeholderAPIClass.getMethod("setPlaceholders", Player.class, String.class)
//                    .invoke(null, player, placeholder);
            @NotNull String actualValue = PlaceholderAPI.setPlaceholders(player, placeholder);

        
            if (comparison != null) {
                try {
                    double actual = Double.parseDouble(actualValue);
                    double expected = Double.parseDouble(expectedValue);

                    return switch (comparison) {
                        case "greater_than" -> actual > expected;
                        case "greater_than_or_equal" -> actual >= expected;
                        case "less_than" -> actual < expected;
                        case "less_than_or_equal" -> actual <= expected;
                        case "equal" -> actual == expected;
                        default -> actualValue.equals(expectedValue);
                    };
                } catch (NumberFormatException e) {
                    return actualValue.equals(expectedValue);
                }
            }

            return actualValue.equals(expectedValue);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to evaluate PlaceholderAPI condition: " + e.getMessage());
            return false;
        }
    }

    private boolean evaluateGroupCondition(StateCondition condition, Player player) {
        List<StateCondition> subConditions = condition.getSubConditions();
        if (subConditions == null || subConditions.isEmpty()) return true;
        
        String logic = condition.getLogic();
        boolean isAnd = "AND".equalsIgnoreCase(logic) || condition.getType() == ConditionType.ALL_CONDITIONS;
        
        for (StateCondition subCondition : subConditions) {
            boolean result = evaluate(subCondition, player);
            
            if (isAnd && !result) {
                return false; // AND: all must be true
            } else if (!isAnd && result) {
                return true; // OR: at least one must be true
            }
        }
        
        return isAnd; // AND: all were true, OR: none were true
    }

    private boolean evaluatePlayerLevel(StateCondition condition, Player player) {
        int requiredLevel = condition.getInt("level", 1);
        String comparison = condition.getString("comparison");
        int playerLevel = player.getLevel();
        
        return switch (comparison) {
            case "greater_than" -> playerLevel > requiredLevel;
            case "greater_than_or_equal" -> playerLevel >= requiredLevel;
            case "less_than" -> playerLevel < requiredLevel;
            case "less_than_or_equal" -> playerLevel <= requiredLevel;
            case "equal" -> playerLevel == requiredLevel;
            default -> playerLevel >= requiredLevel;
        };
    }

    private boolean evaluateWorldCondition(StateCondition condition, Player player) {
        String worldName = condition.getString("world");
        return worldName != null && player.getWorld().getName().equals(worldName);
    }

    private boolean evaluateBiomeCondition(StateCondition condition, Player player) {
        String biomeName = condition.getString("biome");
        if (biomeName == null) return false;
        
        return player.getLocation().getBlock().getBiome().name().equalsIgnoreCase(biomeName);
    }

    private boolean evaluateCustomCondition(StateCondition condition, Player player) {
        // Hook for custom condition implementations
        String conditionName = condition.getString("name");
        // This would call registered custom condition handlers
        return false;
    }

    private long timeStringToTicks(String timeString) {
        String[] parts = timeString.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        
        // Convert to Minecraft ticks (0 = 6:00 AM, 6000 = 12:00 PM, 18000 = 6:00 PM)
        long totalMinutes = (hours - 6) * 60 + minutes;
        if (totalMinutes < 0) totalMinutes += 24 * 60; // Handle times before 6 AM
        
        return (totalMinutes * 1000) / 60; // Convert to ticks
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            try {
                RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
                // Use reflection to avoid dependency issues
                if (rsp != null) {
                    economy = rsp.getProvider();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to setup economy: " + e.getMessage());
            }
        }
    }
}
