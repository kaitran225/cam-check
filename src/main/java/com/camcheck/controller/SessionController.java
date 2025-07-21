package com.camcheck.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for managing camera sessions between users
 */
@Controller
@Slf4j
public class SessionController {

    private final SimpMessagingTemplate messagingTemplate;
    
    // Store active sessions: sessionCode -> adminUsername
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();
    
    // Store active connections: username -> connectedPeer
    private final Map<String, String> activeConnections = new ConcurrentHashMap<>();
    
    public SessionController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Create a new session (admin only)
     */
    @MessageMapping("/session/create")
    public void createSession(SessionRequest request) {
        log.info("Session creation request: {} by {}", request.getCode(), request.getUsername());
        
        // Store the session
        activeSessions.put(request.getCode(), request.getUsername());
        
        // Session will expire after 10 minutes if not used
        new Thread(() -> {
            try {
                Thread.sleep(600000); // 10 minutes
                if (activeSessions.containsKey(request.getCode())) {
                    activeSessions.remove(request.getCode());
                    log.info("Session expired: {}", request.getCode());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * Join an existing session (user only)
     */
    @MessageMapping("/session/join")
    public void joinSession(SessionRequest request) {
        log.info("Session join request: {} by {}", request.getCode(), request.getUsername());
        
        // Check if session exists
        if (!activeSessions.containsKey(request.getCode())) {
            sendError(request.getUsername(), "Invalid session code or session expired");
            return;
        }
        
        String adminUsername = activeSessions.get(request.getCode());
        
        // Check if admin is already connected to someone else
        if (activeConnections.containsKey(adminUsername)) {
            sendError(request.getUsername(), "Admin is already connected to another user");
            return;
        }
        
        // Check if user is already connected to someone else
        if (activeConnections.containsKey(request.getUsername())) {
            sendError(request.getUsername(), "You are already connected to another session");
            return;
        }
        
        // Establish connection
        activeConnections.put(adminUsername, request.getUsername());
        activeConnections.put(request.getUsername(), adminUsername);
        
        // Remove the session code as it's now used
        activeSessions.remove(request.getCode());
        
        // Notify both parties
        notifySessionConnected(adminUsername, request.getUsername());
        notifySessionConnected(request.getUsername(), adminUsername);
        
        log.info("Session established between {} and {}", adminUsername, request.getUsername());
    }
    
    /**
     * End an active session
     */
    @MessageMapping("/session/end")
    public void endSession(SessionRequest request) {
        log.info("Session end request by {}", request.getUsername());
        
        String connectedPeer = activeConnections.get(request.getUsername());
        
        if (connectedPeer != null) {
            // Remove both connections
            activeConnections.remove(request.getUsername());
            activeConnections.remove(connectedPeer);
            
            // Notify the peer
            notifySessionDisconnected(connectedPeer, request.getUsername());
            
            log.info("Session ended between {} and {}", request.getUsername(), connectedPeer);
        }
    }
    
    /**
     * Forward camera frames to the connected peer
     */
    @MessageMapping("/camera/{username}")
    public void forwardCameraFrame(String imageData, String username) {
        String connectedPeer = activeConnections.get(username);
        
        if (connectedPeer != null) {
            messagingTemplate.convertAndSend("/topic/camera/" + connectedPeer, imageData);
        }
    }
    
    /**
     * Send error message to user
     */
    private void sendError(String username, String message) {
        SessionEvent event = new SessionEvent();
        event.setType("error");
        event.setMessage(message);
        
        messagingTemplate.convertAndSend("/topic/session/events", event);
        log.warn("Session error for {}: {}", username, message);
    }
    
    /**
     * Notify user of successful connection
     */
    private void notifySessionConnected(String username, String peer) {
        SessionEvent event = new SessionEvent();
        event.setType("connected");
        event.setPeer(peer);
        
        messagingTemplate.convertAndSend("/topic/session/events", event);
    }
    
    /**
     * Notify user of session disconnection
     */
    private void notifySessionDisconnected(String username, String peer) {
        SessionEvent event = new SessionEvent();
        event.setType("disconnected");
        event.setPeer(peer);
        
        messagingTemplate.convertAndSend("/topic/session/events", event);
    }
    
    /**
     * Session request DTO
     */
    public static class SessionRequest {
        private String code;
        private String username;
        private String peer;
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPeer() {
            return peer;
        }
        
        public void setPeer(String peer) {
            this.peer = peer;
        }
    }
    
    /**
     * Session event DTO
     */
    public static class SessionEvent {
        private String type;
        private String peer;
        private String message;
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getPeer() {
            return peer;
        }
        
        public void setPeer(String peer) {
            this.peer = peer;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
} 