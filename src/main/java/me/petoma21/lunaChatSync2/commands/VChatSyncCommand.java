package me.petoma21.lunaChatSync2.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import me.petoma21.lunaChatSync2.LunaChatSync2;
import me.petoma21.lunaChatSync2.config.ConfigManager;

public class VChatSyncCommand {

    private final LunaChatSync2 plugin;
    private final ConfigManager configManager;

    public VChatSyncCommand(LunaChatSync2 plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("velocitychatsync.admin")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "test":
                handleTest(sender);
                break;
            case "sync":
                handleSync(sender);
                break;
            case "debug":
                handleDebug(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "VelocityChatSync Commands:");
        sender.sendMessage(ChatColor.GRAY + "/vchatsync reload " + ChatColor.WHITE + "- Reload configuration");
        sender.sendMessage(ChatColor.GRAY + "/vchatsync status " + ChatColor.WHITE + "- Show plugin status");
        sender.sendMessage(ChatColor.GRAY + "/vchatsync test " + ChatColor.WHITE + "- Test database connection");
        sender.sendMessage(ChatColor.GRAY + "/vchatsync sync " + ChatColor.WHITE + "- Force synchronization");
        sender.sendMessage(ChatColor.GRAY + "/vchatsync debug <on|off> " + ChatColor.WHITE + "- Toggle debug mode");
    }

    private void handleReload(CommandSender sender) {
        try {
            configManager.loadConfig();
            plugin.getChatSyncManager().reload();
            sender.sendMessage(configManager.getMessage("reload-success"));
        } catch (Exception e) {
            sender.sendMessage(configManager.getMessage("reload-error"));
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }
    }

    private void handleStatus(CommandSender sender) {
        String serverName = configManager.getServerName();
        boolean syncEnabled = configManager.isChatSyncEnabled();
        boolean syncRunning = plugin.getChatSyncManager().isRunning();
        int processedCount = plugin.getChatSyncManager().getProcessedMessageCount();

        sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Plugin Status:");
        sender.sendMessage(ChatColor.GRAY + "Server Name: " + ChatColor.WHITE + serverName);
        sender.sendMessage(ChatColor.GRAY + "Sync Enabled: " + (syncEnabled ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        sender.sendMessage(ChatColor.GRAY + "Sync Running: " + (syncRunning ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        sender.sendMessage(ChatColor.GRAY + "Processed Messages: " + ChatColor.WHITE + processedCount);
        sender.sendMessage(ChatColor.GRAY + "Sync Delay: " + ChatColor.WHITE + configManager.getSyncDelay() + "ms");

        // チャンネル情報
        sender.sendMessage(ChatColor.GRAY + "Sync Channels: " + ChatColor.WHITE +
                (configManager.getSyncChannels().isEmpty() ? "ALL" : configManager.getSyncChannels().toString()));
        sender.sendMessage(ChatColor.GRAY + "Exclude Channels: " + ChatColor.WHITE +
                configManager.getExcludeChannels().toString());
    }

    private void handleTest(CommandSender sender) {
        sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Testing database connection...");

        plugin.getDatabaseManager().testConnection().thenAccept(result -> {
            if (result) {
                sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Database connection successful!");
            } else {
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Database connection failed!");
            }
        }).exceptionally(throwable -> {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Database test error: " + throwable.getMessage());
            return null;
        });
    }

    private void handleSync(CommandSender sender) {
        sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Forcing message synchronization...");

        try {
            plugin.getChatSyncManager().forceSync();
            sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Synchronization triggered successfully!");
        } catch (Exception e) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to trigger synchronization: " + e.getMessage());
        }
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            boolean currentDebug = configManager.isDebugEnabled();
            sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Debug mode is currently: " +
                    (currentDebug ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            return;
        }

        String mode = args[1].toLowerCase();
        switch (mode) {
            case "on":
            case "true":
            case "enable":
                plugin.getConfig().set("logging.debug", true);
                plugin.saveConfig();
                configManager.loadConfig();
                sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Debug mode enabled!");
                break;
            case "off":
            case "false":
            case "disable":
                plugin.getConfig().set("logging.debug", false);
                plugin.saveConfig();
                configManager.loadConfig();
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Debug mode disabled!");
                break;
            default:
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Invalid option. Use 'on' or 'off'");
                break;
        }
    }
}
