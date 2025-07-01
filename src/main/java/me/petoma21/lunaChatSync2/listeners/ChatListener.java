package me.petoma21.lunaChatSync2.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import com.github.ucchyocean.lc3.event.LunaChatPostChatEvent;
import me.petoma21.lunaChatSync2.LunaChatSync2;
import me.petoma21.lunaChatSync2.config.ConfigManager;
import me.petoma21.lunaChatSync2.models.ChatMessage;

public class ChatListener implements Listener {

    private final LunaChatSync2 plugin;
    private final ConfigManager configManager;

    public ChatListener(LunaChatSync2 plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLunaChatPost(LunaChatPostChatEvent event) {
        // デバッグログ
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("LunaChat event received: " + event.getPlayer().getName() +
                    " in channel " + event.getChannel().getName());
        }

        Player player = event.getPlayer();
        String channelName = event.getChannel().getName();
        String originalMessage = event.getOriginalMessage();

        // フィルタリングチェック
        if (!shouldSyncMessage(player, channelName, originalMessage)) {
            return;
        }

        // チャットメッセージオブジェクトの作成
        ChatMessage chatMessage = new ChatMessage(
                configManager.getServerName(),
                player.getName(),
                player.getUniqueId().toString(),
                channelName,
                originalMessage
        );

        // 非同期でデータベースに保存
        plugin.getDatabaseManager().saveChatMessage(chatMessage).thenRun(() -> {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("Chat message saved: " + chatMessage.getMessageId());
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Failed to save chat message: " + throwable.getMessage());
            return null;
        });
    }

    private boolean shouldSyncMessage(Player player, String channelName, String message) {
        // プラグインが無効な場合
        if (!configManager.isChatSyncEnabled()) {
            return false;
        }

        // プレイヤーがバイパス権限を持っている場合
        if (player.hasPermission("velocitychatsync.bypass")) {
            return false;
        }

        // 無視対象のプレイヤーかチェック
        if (configManager.isPlayerIgnored(player.getName())) {
            return false;
        }

        // チャンネルが同期対象かチェック
        if (!configManager.shouldSyncChannel(channelName)) {
            return false;
        }

        // メッセージに無視ワードが含まれているかチェック
        if (configManager.containsIgnoredWords(message)) {
            return false;
        }

        // メッセージ長制限チェック
        if (message.length() > configManager.getMaxMessageLength()) {
            return false;
        }

        return true;
    }
