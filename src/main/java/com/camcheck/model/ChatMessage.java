package com.camcheck.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Model class for chat messages (in-memory storage)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    private String id;
    private String sender;
    private String recipient;
    private String content;
    private Instant timestamp;
    private boolean read = false;
    private MessageType type = MessageType.TEXT;
    
    /**
     * Message type enum
     */
    public enum MessageType {
        TEXT,
        IMAGE,
        ALERT
    }
    
    /**
     * Create a new text message
     */
    public static ChatMessage createTextMessage(String sender, String recipient, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID().toString());
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(content);
        message.setTimestamp(Instant.now());
        message.setType(MessageType.TEXT);
        return message;
    }
    
    /**
     * Create a new alert message
     */
    public static ChatMessage createAlertMessage(String sender, String recipient, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID().toString());
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(content);
        message.setTimestamp(Instant.now());
        message.setType(MessageType.ALERT);
        return message;
    }
}
