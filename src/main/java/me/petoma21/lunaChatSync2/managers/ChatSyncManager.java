package me.petoma21.lunaChatSync2.managers;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import me.petoma21.lunaChatSync2.LunaChatSync2;
import me.petoma21.lunaChatSync2.config.ConfigManager;
import me.petoma21.lunaChatSync2.models.ChatMessage;
import com.github.ucchyocean.lc3.LunaChat;
import com.github.ucchyocean.lc3.LunaChatAPI;
import com.github.ucchyocean.lc3.member.ChannelMember;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatSyncManager {

    private final LunaChatSync2 plugin;
    private final ConfigManager configManager;
    private final Set<String> processedMessages;
    private BukkitTask syncTask;

    public ChatSyncManager(LunaChatSync2 plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.processedMessages = ConcurrentHashMap.newKeySet();
        startSyncTask();
    }

    private void startSyncTask() {
        long delay = Math.max(configManager.getSyncDelay() / 50, 1); // ticks

        syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            syncMessages();
        }, delay, delay);

        plugin.getLogger().info("Chat sync task started with delay: " + delay + " ticks");
    }

    private void syncMessages() {
        if (!configManager.isChatSyncEnabled()) {
            return;
        }

        String serverName = configManager.getServerName();

        plugin.getDatabaseManager().getUnprocessedMessages(serverName)
                .thenAccept(messages -> {
                    if (messages.isEmpty()) {
                        return;
                    }

                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("Processing " + messages.size() + " unprocessed messages");
                    }

                    // メインスレッドで処理
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (ChatMessage message : messages) {
                            processReceivedMessage(message);
                        }
                    });

                }).exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to sync messages: " + throwable.getMessage());
                    return null;
                });
    }

    private void processReceivedMessage(ChatMessage message) {
        // 重複処理防止
        if (processedMessages.contains(message.getMessageId())) {
            return;
        }

        // チャンネルが同期対象かチェック
        if (!configManager.shouldSyncChannel(message.getChannelName())) {
            markAsProcessed(message.getMessageId());
            return;
        }

        try {
            // LunaChatのAPIを使用してメッセージを送信
            LunaChatAPI api = LunaChat.getAPI();

            if (api != null) {
                // サーバー名フォーマットを適用
                String formattedServerName = configManager.formatServerName(message.getServerName());

                // %server%プレースホルダーを置換したメッセージを構築
                String displayMessage = String.format("%s <%s> %s",
                        formattedServerName,
                        message.getPlayerName(),
                        message.getMessage()
                );

//                // 指定チャンネルにメッセージを送信
//                api.sendMessage(null, message.getChannelName(),
//                        displayMessage, "[VelocityChatSync]", true);

                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("Broadcasted message from " +
                            message.getServerName() + ": " + message.getPlayerName() +
                            " -> " + message.getMessage());
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to process received message: " + e.getMessage());
            e.printStackTrace();
        }

        markAsProcessed(message.getMessageId());
    }

    private void markAsProcessed(String messageId) {
        processedMessages.add(messageId);

        // データベース上でも処理済みとしてマーク
        plugin.getDatabaseManager().markMessageAsProcessed(messageId)
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to mark message as processed: " + throwable.getMessage());
                    return null;
                });

        // メモリ使用量を制限するため、古い処理済みメッセージIDを削除
        if (processedMessages.size() > 10000) {
            processedMessages.clear();
        }
    }

    public void forceSync() {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Force syncing messages...");
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::syncMessages);
    }

    public void reload() {
        shutdown();
        processedMessages.clear();
        startSyncTask();
        plugin.getLogger().info("ChatSyncManager reloaded");
    }

    public void shutdown() {
        if (syncTask != null && !syncTask.isCancelled()) {
            syncTask.cancel();
            plugin.getLogger().info("Chat sync task stopped");
        }

        // 最後の同期ステータス更新
        plugin.getDatabaseManager().updateSyncStatus(configManager.getServerName())
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to update final sync status: " + throwable.getMessage());
                    return null;
                });
    }

    public boolean isRunning() {
        return syncTask != null && !syncTask.isCancelled();
    }

    public int getProcessedMessageCount() {
        return processedMessages.size();
    }
}