package com.camcheck.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity class for storing chat messages
 */
@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String sender;
    
    @Column(nullable = false)
    private String recipient;
    
    @Column(nullable = false, length = 1000)
    private String content;
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @Column(nullable = false)
    private boolean read = false;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
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
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(content);
        message.setTimestamp(Instant.now());
        message.setType(MessageType.ALERT);
        return message;
    }
}
