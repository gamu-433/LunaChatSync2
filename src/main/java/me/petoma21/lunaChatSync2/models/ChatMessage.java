package me.petoma21.lunaChatSync2.models;

import java.util.UUID;

public class ChatMessage {

    private final String messageId;
    private final String serverName;
    private final String playerName;
    private final String playerUuid;
    private final String channelName;
    private final String message;
    private final long timestamp;

    public ChatMessage(String serverName, String playerName, String playerUuid,
                       String channelName, String message) {
        this.messageId = UUID.randomUUID().toString();
        this.serverName = serverName;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.channelName = channelName;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(String messageId, String serverName, String playerName,
                       String playerUuid, String channelName, String message, long timestamp) {
        this.messageId = messageId;
        this.serverName = serverName;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.channelName = channelName;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getServerName() {
        return serverName;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("ChatMessage{id='%s', server='%s', player='%s', channel='%s', message='%s', timestamp=%d}",
                messageId, serverName, playerName, channelName, message, timestamp);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ChatMessage that = (ChatMessage) obj;
        return messageId.equals(that.messageId);
    }

    @Override
    public int hashCode() {
        return messageId.hashCode();
    }
}
