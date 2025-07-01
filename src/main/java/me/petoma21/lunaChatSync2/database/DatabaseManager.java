package me.petoma21.lunaChatSync2.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.petoma21.lunaChatSync2.LunaChatSync2;
import me.petoma21.lunaChatSync2.config.ConfigManager;
import me.petoma21.lunaChatSync2.models.ChatMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {

    private final LunaChatSync2 plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;

    public DatabaseManager(LunaChatSync2 plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public boolean initialize() {
        try {
            setupHikariCP();
            createTables();
            cleanupOldMessages();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            return false;
        }
    }

    private void setupHikariCP() throws SQLException {
        HikariConfig config = new HikariConfig();

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                configManager.getDatabaseHost(),
                configManager.getDatabasePort(),
                configManager.getDatabaseName());

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(configManager.getDatabaseUsername());
        config.setPassword(configManager.getDatabasePassword());

        config.setMaximumPoolSize(configManager.getMaximumPoolSize());
        config.setMinimumIdle(configManager.getMinimumIdle());
        config.setConnectionTimeout(configManager.getConnectionTimeout());
        config.setIdleTimeout(configManager.getIdleTimeout());
        config.setMaxLifetime(configManager.getMaxLifetime());

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        dataSource = new HikariDataSource(config);

        // 接続テスト
        try (Connection connection = dataSource.getConnection()) {
            plugin.getLogger().info("Database connection established successfully!");
        }
    }

    private void createTables() throws SQLException {
        String createMessagesTable = """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                message_id VARCHAR(36) NOT NULL UNIQUE,
                server_name VARCHAR(64) NOT NULL,
                player_name VARCHAR(16) NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                channel_name VARCHAR(32) NOT NULL,
                message TEXT NOT NULL,
                timestamp BIGINT NOT NULL,
                processed BOOLEAN DEFAULT FALSE,
                INDEX idx_timestamp (timestamp),
                INDEX idx_server_processed (server_name, processed),
                INDEX idx_message_id (message_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        String createSyncStatusTable = """
            CREATE TABLE IF NOT EXISTS sync_status (
                server_name VARCHAR(64) NOT NULL,
                last_sync_time BIGINT NOT NULL,
                last_message_id BIGINT DEFAULT 0,
                PRIMARY KEY (server_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        try (Connection connection = getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createMessagesTable);
                statement.executeUpdate(createSyncStatusTable);
                plugin.getLogger().info("Database tables created/verified successfully!");
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public CompletableFuture<Void> saveChatMessage(ChatMessage message) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO chat_messages 
                (message_id, server_name, player_name, player_uuid, channel_name, message, timestamp) 
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, message.getMessageId());
                statement.setString(2, message.getServerName());
                statement.setString(3, message.getPlayerName());
                statement.setString(4, message.getPlayerUuid());
                statement.setString(5, message.getChannelName());
                statement.setString(6, message.getMessage());
                statement.setLong(7, message.getTimestamp());

                statement.executeUpdate();

                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("Saved chat message: " + message.getMessageId());
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save chat message", e);
            }
        });
    }

    public CompletableFuture<List<ChatMessage>> getUnprocessedMessages(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            List<ChatMessage> messages = new ArrayList<>();
            String sql = """
                SELECT message_id, server_name, player_name, player_uuid, channel_name, message, timestamp
                FROM chat_messages 
                WHERE server_name != ? AND processed = FALSE
                ORDER BY timestamp ASC
                """;

            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, serverName);

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ChatMessage message = new ChatMessage(
                                resultSet.getString("message_id"),
                                resultSet.getString("server_name"),
                                resultSet.getString("player_name"),
                                resultSet.getString("player_uuid"),
                                resultSet.getString("channel_name"),
                                resultSet.getString("message"),
                                resultSet.getLong("timestamp")
                        );
                        messages.add(message);
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get unprocessed messages", e);
            }

            return messages;
        });
    }

    public CompletableFuture<Void> markMessageAsProcessed(String messageId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE chat_messages SET processed = TRUE WHERE message_id = ?";

            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, messageId);
                statement.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to mark message as processed", e);
            }
        });
    }

    public CompletableFuture<Void> updateSyncStatus(String serverName) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO sync_status (server_name, last_sync_time, last_message_id) 
                VALUES (?, ?, (SELECT COALESCE(MAX(id), 0) FROM chat_messages))
                ON DUPLICATE KEY UPDATE 
                last_sync_time = VALUES(last_sync_time),
                last_message_id = VALUES(last_message_id)
                """;

            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, serverName);
                statement.setLong(2, System.currentTimeMillis());

                statement.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update sync status", e);
            }
        });
    }

    private void cleanupOldMessages() {
        if (!configManager.isSaveToFile()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            long cutoffTime = System.currentTimeMillis() - (configManager.getRetentionDays() * 24 * 60 * 60 * 1000L);
            String sql = "DELETE FROM chat_messages WHERE timestamp < ?";

            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setLong(1, cutoffTime);
                int deleted = statement.executeUpdate();

                if (deleted > 0) {
                    plugin.getLogger().info("Cleaned up " + deleted + " old chat messages");
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to cleanup old messages", e);
            }
        });
    }

    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                return connection.isValid(5);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Database connection test failed", e);
                return false;
            }
        });
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed");
        }
    }
}
