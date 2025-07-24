package com.camcheck.service;

import com.camcheck.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for handling chat operations (in-memory implementation)
 */
@Service
@Slf4j
public class ChatService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    // In-memory storage for chat messages
    private final Map<String, List<ChatMessage>> chatMessages = new ConcurrentHashMap<>();
    
    // Map of user's conversations
    private final Map<String, Set<String>> userConversations = new ConcurrentHashMap<>();
    
    public ChatService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Save and send a chat message
     */
    public ChatMessage sendMessage(ChatMessage message) {
        log.info("Sending message from {} to {}: {}", message.getSender(), message.getRecipient(), message.getContent());
        
        // Generate conversation key (sorted usernames to ensure uniqueness)
        String conversationKey = getConversationKey(message.getSender(), message.getRecipient());
        
        // Add message to in-memory storage
        chatMessages.computeIfAbsent(conversationKey, k -> new ArrayList<>()).add(message);
        
        // Update user conversations
        userConversations.computeIfAbsent(message.getSender(), k -> new HashSet<>()).add(message.getRecipient());
        userConversations.computeIfAbsent(message.getRecipient(), k -> new HashSet<>()).add(message.getSender());
        
        // Send message to recipient via WebSocket
        messagingTemplate.convertAndSend("/topic/chat/" + message.getRecipient(), message);
        
        return message;
    }
    
    /**
     * Get conversation between two users
     */
    public Page<ChatMessage> getConversation(String user1, String user2, int page, int size) {
        String conversationKey = getConversationKey(user1, user2);
        List<ChatMessage> messages = chatMessages.getOrDefault(conversationKey, new ArrayList<>());
        
        // Sort by timestamp (newest first)
        messages.sort(Comparator.comparing(ChatMessage::getTimestamp).reversed());
        
        // Paginate
        int start = Math.min(page * size, messages.size());
        int end = Math.min(start + size, messages.size());
        List<ChatMessage> pagedMessages = start < end ? messages.subList(start, end) : new ArrayList<>();
        
        return new PageImpl<>(pagedMessages, PageRequest.of(page, size), messages.size());
    }
    
    /**
     * Get unread messages for a user
     */
    public List<ChatMessage> getUnreadMessages(String username) {
        List<ChatMessage> unreadMessages = new ArrayList<>();
        
        // Get all conversations for this user
        Set<String> conversationPartners = userConversations.getOrDefault(username, new HashSet<>());
        
        // For each conversation, get unread messages
        for (String partner : conversationPartners) {
            String conversationKey = getConversationKey(username, partner);
            List<ChatMessage> messages = chatMessages.getOrDefault(conversationKey, new ArrayList<>());
            
            // Filter unread messages sent to this user
            List<ChatMessage> unread = messages.stream()
                    .filter(m -> m.getRecipient().equals(username) && !m.isRead())
                    .collect(Collectors.toList());
            
            unreadMessages.addAll(unread);
        }
        
        // Sort by timestamp (newest first)
        unreadMessages.sort(Comparator.comparing(ChatMessage::getTimestamp).reversed());
        
        return unreadMessages;
    }
    
    /**
     * Mark messages as read
     */
    public void markAsRead(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            String conversationKey = getConversationKey(message.getSender(), message.getRecipient());
            List<ChatMessage> conversationMessages = chatMessages.getOrDefault(conversationKey, new ArrayList<>());
            
            // Find and mark message as read
            conversationMessages.stream()
                    .filter(m -> m.getId().equals(message.getId()))
                    .forEach(m -> m.setRead(true));
        }
    }
    
    /**
     * Get recent conversations for a user
     */
    public Page<String> getRecentConversations(String username, int page, int size) {
        Set<String> conversations = userConversations.getOrDefault(username, new HashSet<>());
        
        // Convert to list for pagination
        List<String> conversationList = new ArrayList<>(conversations);
        
        // Sort conversations by most recent message
        conversationList.sort((user1, user2) -> {
            Instant lastMessage1 = getLastMessageTimestamp(username, user1);
            Instant lastMessage2 = getLastMessageTimestamp(username, user2);
            return lastMessage2.compareTo(lastMessage1); // Newest first
        });
        
        // Paginate
        int start = Math.min(page * size, conversationList.size());
        int end = Math.min(start + size, conversationList.size());
        List<String> pagedConversations = start < end ? conversationList.subList(start, end) : new ArrayList<>();
        
        return new PageImpl<>(pagedConversations, PageRequest.of(page, size), conversationList.size());
    }
    
    /**
     * Send a system notification to a user
     */
    public void sendSystemNotification(String recipient, String content) {
        ChatMessage notification = ChatMessage.createAlertMessage("SYSTEM", recipient, content);
        sendMessage(notification);
    }
    
    /**
     * Get conversation key (sorted usernames to ensure uniqueness)
     */
    private String getConversationKey(String user1, String user2) {
        return user1.compareTo(user2) < 0 ? user1 + "_" + user2 : user2 + "_" + user1;
    }
    
    /**
     * Get timestamp of last message in a conversation
     */
    private Instant getLastMessageTimestamp(String user1, String user2) {
        String conversationKey = getConversationKey(user1, user2);
        List<ChatMessage> messages = chatMessages.getOrDefault(conversationKey, new ArrayList<>());
        
        if (messages.isEmpty()) {
            return Instant.EPOCH;
        }
        
        return messages.stream()
                .map(ChatMessage::getTimestamp)
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);
    }
}