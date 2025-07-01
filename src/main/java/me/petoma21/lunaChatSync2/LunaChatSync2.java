package me.petoma21.lunaChatSync2;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import me.petoma21.lunaChatSync2.config.ConfigManager;
import me.petoma21.lunaChatSync2.database.DatabaseManager;
import me.petoma21.lunaChatSync2.listeners.ChatListener;
import me.petoma21.lunaChatSync2.managers.ChatSyncManager;
import me.petoma21.lunaChatSync2.commands.VChatSyncCommand;
public final class LunaChatSync2 extends JavaPlugin {

        private static LunaChatSync2 instance;
        private ConfigManager configManager;
        private DatabaseManager databaseManager;
        private ChatSyncManager chatSyncManager;
        private VChatSyncCommand commandHandler;

        @Override
        public void onEnable() {
            instance = this;

            // 設定ファイルの初期化
            saveDefaultConfig();
            configManager = new ConfigManager(this);

            // データベース接続の初期化
            databaseManager = new DatabaseManager(this);
            if (!databaseManager.initialize()) {
                getLogger().severe("Failed to initialize database connection! Disabling plugin...");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }

            // LunaChatプラグインの確認
            if (!Bukkit.getPluginManager().isPluginEnabled("LunaChat")) {
                getLogger().severe("LunaChat plugin not found! This plugin requires LunaChat to function.");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }

            // チャット同期マネージャーの初期化
            chatSyncManager = new ChatSyncManager(this);

            // イベントリスナーの登録
            Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);

            // コマンドハンドラーの初期化
            commandHandler = new VChatSyncCommand(this);

            getLogger().info("VelocityChatSync has been enabled successfully!");
        }

        @Override
        public void onDisable() {
            if (chatSyncManager != null) {
                chatSyncManager.shutdown();
            }
            if (databaseManager != null) {
                databaseManager.close();
            }
            getLogger().info("VelocityChatSync has been disabled.");
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (command.getName().equalsIgnoreCase("vchatsync")) {
                return commandHandler.onCommand(sender, command, label, args);
            }
            return false;
        }

        public static LunaChatSync2 getInstance() {
            return instance;
        }

        public ConfigManager getConfigManager() {
            return configManager;
        }

        public DatabaseManager getDatabaseManager() {
            return databaseManager;
        }

        public ChatSyncManager getChatSyncManager() {
            return chatSyncManager;
        }
    }