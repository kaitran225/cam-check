package com.camcheck.service;

import com.camcheck.model.ChatMessage;
import com.camcheck.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for handling chat operations
 */
@Service
@Slf4j
public class ChatService {
    
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    public ChatService(ChatMessageRepository chatMessageRepository, SimpMessagingTemplate messagingTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Save and send a chat message
     */
    @Transactional
    public ChatMessage sendMessage(ChatMessage message) {
        log.info("Sending message from {} to {}: {}", message.getSender(), message.getRecipient(), message.getContent());
        
        // Save message to database
        ChatMessage savedMessage = chatMessageRepository.save(message);
        
        // Send message to recipient via WebSocket
        messagingTemplate.convertAndSend("/topic/chat/" + message.getRecipient(), savedMessage);
        
        return savedMessage;
    }
    
    /**
     * Get conversation between two users
     */
    @Transactional(readOnly = true)
    public Page<ChatMessage> getConversation(String user1, String user2, int page, int size) {
        return chatMessageRepository.findConversation(
                user1, 
                user2, 
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
    }
    
    /**
     * Get unread messages for a user
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> getUnreadMessages(String username) {
        return chatMessageRepository.findByRecipientAndReadFalseOrderByTimestampDesc(username);
    }
    
    /**
     * Mark messages as read
     */
    @Transactional
    public void markAsRead(List<ChatMessage> messages) {
        List<Long> messageIds = messages.stream()
                .map(ChatMessage::getId)
                .collect(Collectors.toList());
        
        if (!messageIds.isEmpty()) {
            chatMessageRepository.markAsRead(messageIds);
        }
    }
    
    /**
     * Get recent conversations for a user
     */
    @Transactional(readOnly = true)
    public Page<String> getRecentConversations(String username, int page, int size) {
        return chatMessageRepository.findRecentConversations(
                username,
                PageRequest.of(page, size)
        );
    }
    
    /**
     * Send a system notification to a user
     */
    public void sendSystemNotification(String recipient, String content) {
        ChatMessage notification = ChatMessage.createAlertMessage("SYSTEM", recipient, content);
        sendMessage(notification);
    }
}