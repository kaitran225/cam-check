package com.camcheck.controller;

import com.camcheck.model.ChatMessage;
import com.camcheck.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for chat functionality
 */
@Controller
@Slf4j
public class ChatController {

    private final ChatService chatService;
    
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }
    
    /**
     * WebSocket endpoint for sending chat messages
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        String sender = principal != null ? principal.getName() : chatMessage.getSender();
        
        // Set the authenticated sender
        chatMessage.setSender(sender);
        
        // Set timestamp if not provided
        if (chatMessage.getTimestamp() == null) {
            chatMessage.setTimestamp(Instant.now());
        }
        
        log.info("Received chat message from {}: {}", sender, chatMessage.getContent());
        
        // Save and broadcast the message
        chatService.sendMessage(chatMessage);
    }
    
    /**
     * REST endpoint to get conversation history
     */
    @GetMapping("/api/chat/{otherUser}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getConversation(
            @PathVariable String otherUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        
        String currentUser = authentication.getName();
        Page<ChatMessage> messages = chatService.getConversation(currentUser, otherUser, page, size);
        
        // Mark messages as read
        List<ChatMessage> unreadMessages = messages.getContent().stream()
                .filter(m -> m.getRecipient().equals(currentUser) && !m.isRead())
                .toList();
        
        if (!unreadMessages.isEmpty()) {
            chatService.markAsRead(unreadMessages);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("messages", messages.getContent());
        response.put("currentPage", messages.getNumber());
        response.put("totalItems", messages.getTotalElements());
        response.put("totalPages", messages.getTotalPages());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * REST endpoint to get unread messages
     */
    @GetMapping("/api/chat/unread")
    @ResponseBody
    public ResponseEntity<List<ChatMessage>> getUnreadMessages(Authentication authentication) {
        String currentUser = authentication.getName();
        List<ChatMessage> unreadMessages = chatService.getUnreadMessages(currentUser);
        return ResponseEntity.ok(unreadMessages);
    }
    
    /**
     * REST endpoint to mark messages as read
     */
    @PostMapping("/api/chat/mark-read")
    @ResponseBody
    public ResponseEntity<Void> markAsRead(@RequestBody List<ChatMessage> messages, Authentication authentication) {
        String currentUser = authentication.getName();
        
        // Filter messages to ensure user can only mark their own messages as read
        List<ChatMessage> filteredMessages = messages.stream()
                .filter(m -> m.getRecipient().equals(currentUser))
                .toList();
        
        chatService.markAsRead(filteredMessages);
        return ResponseEntity.ok().build();
    }
    
    /**
     * REST endpoint to get recent conversations
     */
    @GetMapping("/api/chat/conversations")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRecentConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        
        String currentUser = authentication.getName();
        Page<String> conversations = chatService.getRecentConversations(currentUser, page, size);
        
        Map<String, Object> response = new HashMap<>();
        response.put("conversations", conversations.getContent());
        response.put("currentPage", conversations.getNumber());
        response.put("totalItems", conversations.getTotalElements());
        response.put("totalPages", conversations.getTotalPages());
        
        return ResponseEntity.ok(response);
    }
}