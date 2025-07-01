package me.petoma21.lunaChatSync2.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.ChatColor;
import me.petoma21.lunaChatSync2.LunaChatSync2;
import java.util.List;

public class ConfigManager {

    private final LunaChatSync2 plugin;
    private FileConfiguration config;

    public ConfigManager(LunaChatSync2 plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    // データベース設定
    public String getDatabaseHost() {
        return config.getString("database.host", "localhost");
    }

    public int getDatabasePort() {
        return config.getInt("database.port", 3306);
    }

    public String getDatabaseName() {
        return config.getString("database.database", "velocitychatsync");
    }

    public String getDatabaseUsername() {
        return config.getString("database.username", "root");
    }

    public String getDatabasePassword() {
        return config.getString("database.password", "password");
    }

    public int getMaximumPoolSize() {
        return config.getInt("database.pool.maximum-pool-size", 10);
    }

    public int getMinimumIdle() {
        return config.getInt("database.pool.minimum-idle", 5);
    }

    public long getConnectionTimeout() {
        return config.getLong("database.pool.connection-timeout", 30000);
    }

    public long getIdleTimeout() {
        return config.getLong("database.pool.idle-timeout", 600000);
    }

    public long getMaxLifetime() {
        return config.getLong("database.pool.max-lifetime", 1800000);
    }

    // サーバー設定
    public String getServerName() {
        return config.getString("server.name", "lobby");
    }

    public String getServerDisplayFormat() {
        return config.getString("server.display-format", "@[%server%]");
    }

    public String getServerColor() {
        return config.getString("server.color", "&a");
    }

    // チャット設定
    public boolean isChatSyncEnabled() {
        return config.getBoolean("chat.enabled", true);
    }

    public List<String> getSyncChannels() {
        return config.getStringList("chat.sync-channels");
    }

    public List<String> getExcludeChannels() {
        return config.getStringList("chat.exclude-channels");
    }

    public int getMaxMessageLength() {
        return config.getInt("chat.max-message-length", 256);
    }

    public long getSyncDelay() {
        return config.getLong("chat.sync-delay", 50);
    }

    // フィルタ設定
    public List<String> getIgnoredPlayers() {
        return config.getStringList("filters.ignored-players");
    }

    public List<String> getIgnoredWords() {
        return config.getStringList("filters.ignored-words");
    }

    public boolean isExcludeAdminChannels() {
        return config.getBoolean("filters.exclude-admin-channels", true);
    }

    // ログ設定
    public boolean isSaveToFile() {
        return config.getBoolean("logging.save-to-file", true);
    }

    public int getRetentionDays() {
        return config.getInt("logging.retention-days", 30);
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("logging.debug", false);
    }

    // メッセージ設定
    public String getMessage(String key) {
        String message = config.getString("messages." + key, "Message not found: " + key);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getPrefix() {
        return getMessage("prefix");
    }

    // サーバー名のフォーマット処理
    public String formatServerName(String serverName) {
        String format = getServerDisplayFormat();
        String color = ChatColor.translateAlternateColorCodes('&', getServerColor());
        return color + format.replace("%server%", serverName) + ChatColor.RESET;
    }

    // チャンネルが同期対象かチェック
    public boolean shouldSyncChannel(String channelName) {
        if (!isChatSyncEnabled()) {
            return false;
        }

        // 除外リストに含まれている場合
        if (getExcludeChannels().contains(channelName)) {
            return false;
        }

        // 管理者チャンネルの自動除外
        if (isExcludeAdminChannels() && isAdminChannel(channelName)) {
            return false;
        }

        // 同期チャンネルリストが空の場合は全て同期
        List<String> syncChannels = getSyncChannels();
        if (syncChannels.isEmpty()) {
            return true;
        }

        return syncChannels.contains(channelName);
    }

    private boolean isAdminChannel(String channelName) {
        String lowerName = channelName.toLowerCase();
        return lowerName.contains("admin") || lowerName.contains("staff") ||
                lowerName.contains("mod") || lowerName.contains("operator");
    }

    // プレイヤーが無視対象かチェック
    public boolean isPlayerIgnored(String playerName) {
        return getIgnoredPlayers().contains(playerName);
    }

    // メッセージに無視ワードが含まれているかチェック
    public boolean containsIgnoredWords(String message) {
        String lowerMessage = message.toLowerCase();
        for (String word : getIgnoredWords()) {
            if (lowerMessage.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}