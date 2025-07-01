package me.petoma21.lunaChatSync2.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import com.github.ucchyocean.lc3.bukkit.event.LunaChatBukkitChannelChatEvent;
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
    public void onLunaChatPost(LunaChatBukkitChannelChatEvent event) {
        CommandSender sender = (CommandSender) event.getMember();
        if (!(sender instanceof Player)) {
            return; // プレイヤー以外（コンソールなど）は無視
        }

        Player player = (Player) sender;
        String channelName = event.getChannel().getName();
        String originalMessage = event.getPreReplaceMessage();

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("LunaChat event received: " + player.getName() +
                    " in channel " + channelName);
        }

        if (!shouldSyncMessage(player, channelName, originalMessage)) {
            return;
        }

        ChatMessage chatMessage = new ChatMessage(
                configManager.getServerName(),
                player.getName(),
                player.getUniqueId().toString(),
                channelName,
                originalMessage
        );

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
}