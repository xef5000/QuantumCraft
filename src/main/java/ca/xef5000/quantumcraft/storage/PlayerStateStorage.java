package ca.xef5000.quantumcraft.storage;

import ca.xef5000.quantumcraft.QuantumCraft;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Handles the storage and retrieval of player state data using SQLite.
 * Persists which quantum state each player is viewing for each region.
 */
public class PlayerStateStorage {
    private final QuantumCraft plugin;
    private final File databaseFile;
    private final Logger logger;
    private Connection connection;
    private boolean useMySQL;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;

    /**
     * Creates a new PlayerStateStorage instance.
     *
     * @param plugin The plugin instance
     * @param logger Logger for debugging
     */
    public PlayerStateStorage(QuantumCraft plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.databaseFile = new File(plugin.getDataFolder(), "player_states.db");

        // Get database configuration from config
        this.useMySQL = plugin.getConfig().getBoolean("storage.database.use-mysql", false);

        if (useMySQL) {
            this.mysqlHost = plugin.getConfig().getString("storage.database.mysql.host", "localhost");
            this.mysqlPort = plugin.getConfig().getInt("storage.database.mysql.port", 3306);
            this.mysqlDatabase = plugin.getConfig().getString("storage.database.mysql.database", "quantumcraft");
            this.mysqlUsername = plugin.getConfig().getString("storage.database.mysql.username", "root");
            this.mysqlPassword = plugin.getConfig().getString("storage.database.mysql.password", "");
        }
    }

    /**
     * Initializes the database connection and creates tables if they don't exist.
     *
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            // Create database connection
            if (useMySQL) {
                // MySQL connection
                Class.forName("com.mysql.jdbc.Driver");
                String url = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase;
                connection = DriverManager.getConnection(url, mysqlUsername, mysqlPassword);
            } else {
                // SQLite connection
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            }

            // Create tables if they don't exist
            createTables();

            logger.info("Player state database initialized successfully");
            return true;
        } catch (Exception e) {
            logger.severe("Failed to initialize player state database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Creates the necessary database tables if they don't exist.
     */
    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Table for player region states
            statement.execute(
                "CREATE TABLE IF NOT EXISTS player_region_states (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "region_id VARCHAR(36) NOT NULL, " +
                "state_name VARCHAR(64) NOT NULL, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (player_uuid, region_id)" +
                ")"
            );

            // Table for player reality mode
            statement.execute(
                "CREATE TABLE IF NOT EXISTS player_reality_mode (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "region_id VARCHAR(36) NOT NULL, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (player_uuid, region_id)" +
                ")"
            );
        }
    }

    /**
     * Saves a player's state for a specific region.
     *
     * @param playerUuid The UUID of the player
     * @param regionId   The ID of the region
     * @param stateName  The name of the state
     * @return CompletableFuture that completes when saving is done
     */
    public CompletableFuture<Void> savePlayerState(UUID playerUuid, String regionId, String stateName) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = "INSERT OR REPLACE INTO player_region_states (player_uuid, region_id, state_name, last_updated) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
                if (useMySQL) {
                    sql = "INSERT INTO player_region_states (player_uuid, region_id, state_name, last_updated) VALUES (?, ?, ?, NOW()) " +
                          "ON DUPLICATE KEY UPDATE state_name = VALUES(state_name), last_updated = NOW()";
                }

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, playerUuid.toString());
                    statement.setString(2, regionId);
                    statement.setString(3, stateName);
                    statement.executeUpdate();
                }

                // Remove from reality mode if exists
                removePlayerReality(playerUuid, regionId);

            } catch (SQLException e) {
                logger.severe("Failed to save player state: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Saves a player's reality mode for a specific region.
     *
     * @param playerUuid The UUID of the player
     * @param regionId   The ID of the region
     * @return CompletableFuture that completes when saving is done
     */
    public CompletableFuture<Void> savePlayerReality(UUID playerUuid, String regionId) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = "INSERT OR REPLACE INTO player_reality_mode (player_uuid, region_id, last_updated) VALUES (?, ?, CURRENT_TIMESTAMP)";
                if (useMySQL) {
                    sql = "INSERT INTO player_reality_mode (player_uuid, region_id, last_updated) VALUES (?, ?, NOW()) " +
                          "ON DUPLICATE KEY UPDATE last_updated = NOW()";
                }

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, playerUuid.toString());
                    statement.setString(2, regionId);
                    statement.executeUpdate();
                }

                // Remove from player states if exists
                removePlayerState(playerUuid, regionId);

            } catch (SQLException e) {
                logger.severe("Failed to save player reality mode: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Removes a player's state for a specific region.
     *
     * @param playerUuid The UUID of the player
     * @param regionId   The ID of the region
     */
    private void removePlayerState(UUID playerUuid, String regionId) {
        try {
            String sql = "DELETE FROM player_region_states WHERE player_uuid = ? AND region_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, regionId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Failed to remove player state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Removes a player's reality mode for a specific region.
     *
     * @param playerUuid The UUID of the player
     * @param regionId   The ID of the region
     */
    private void removePlayerReality(UUID playerUuid, String regionId) {
        try {
            String sql = "DELETE FROM player_reality_mode WHERE player_uuid = ? AND region_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, regionId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Failed to remove player reality mode: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads all player states from the database.
     *
     * @return CompletableFuture containing a map of player UUID to region ID to state name
     */
    public CompletableFuture<Map<UUID, Map<String, String>>> loadAllPlayerStates() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Map<String, String>> playerStates = new HashMap<>();

            try {
                String sql = "SELECT player_uuid, region_id, state_name FROM player_region_states";
                try (PreparedStatement statement = connection.prepareStatement(sql);
                     ResultSet resultSet = statement.executeQuery()) {

                    while (resultSet.next()) {
                        UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                        String regionId = resultSet.getString("region_id");
                        String stateName = resultSet.getString("state_name");

                        playerStates
                            .computeIfAbsent(playerUuid, k -> new HashMap<>())
                            .put(regionId, stateName);
                    }
                }
            } catch (SQLException e) {
                logger.severe("Failed to load player states: " + e.getMessage());
                e.printStackTrace();
            }

            return playerStates;
        });
    }

    /**
     * Loads all player reality modes from the database.
     *
     * @return CompletableFuture containing a map of player UUID to set of region IDs
     */
    public CompletableFuture<Map<UUID, Set<String>>> loadAllPlayerRealityModes() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Set<String>> realityModes = new HashMap<>();

            try {
                String sql = "SELECT player_uuid, region_id FROM player_reality_mode";
                try (PreparedStatement statement = connection.prepareStatement(sql);
                     ResultSet resultSet = statement.executeQuery()) {

                    while (resultSet.next()) {
                        UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                        String regionId = resultSet.getString("region_id");

                        realityModes
                            .computeIfAbsent(playerUuid, k -> new HashSet<>())
                            .add(regionId);
                    }
                }
            } catch (SQLException e) {
                logger.severe("Failed to load player reality modes: " + e.getMessage());
                e.printStackTrace();
            }

            return realityModes;
        });
    }

    /**
     * Removes all data for a specific player.
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that completes when deletion is done
     */
    public CompletableFuture<Void> removePlayerData(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Delete from player states
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM player_region_states WHERE player_uuid = ?")) {
                    statement.setString(1, playerUuid.toString());
                    statement.executeUpdate();
                }

                // Delete from reality mode
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM player_reality_mode WHERE player_uuid = ?")) {
                    statement.setString(1, playerUuid.toString());
                    statement.executeUpdate();
                }
            } catch (SQLException e) {
                logger.severe("Failed to remove player data: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Closes the database connection.
     */
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.severe("Failed to close database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
