package com.camcheck.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.HashMap;
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
        log.info("SessionController initialized");
    }
    
    /**
     * Create a new session (admin only)
     */
    @MessageMapping("/session/create")
    public void createSession(SessionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        String username = user != null ? user.getName() : request.getUsername();
        
        log.info("Session creation request: {} by {} (authenticated as: {})", 
                request.getCode(), request.getUsername(), username);
        
        // Store the session
        activeSessions.put(request.getCode(), username);
        
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
        
        // Confirm to the admin that the session was created
        SessionEvent event = new SessionEvent();
        event.setType("created");
        event.setMessage("Session created successfully with code: " + request.getCode());
        messagingTemplate.convertAndSend("/topic/session/" + username, event);
        
        log.info("Session created with code: {} for user: {}", request.getCode(), username);
        log.debug("Available sessions: {}", activeSessions);
    }
    
    /**
     * Join an existing session (user only)
     */
    @MessageMapping("/session/join")
    public void joinSession(SessionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        String username = user != null ? user.getName() : request.getUsername();
        
        log.info("Session join request: {} by {} (authenticated as: {})", 
                request.getCode(), request.getUsername(), username);
        log.debug("Available sessions: {}", activeSessions);
        
        // Check if session exists
        if (!activeSessions.containsKey(request.getCode())) {
            log.warn("Session not found: {}", request.getCode());
            sendError(username, "Invalid session code or session expired");
            return;
        }
        
        String adminUsername = activeSessions.get(request.getCode());
        log.info("Found admin {} for session code {}", adminUsername, request.getCode());
        
        // Check if admin is already connected to someone else
        if (activeConnections.containsKey(adminUsername)) {
            log.warn("Admin {} is already connected to another user", adminUsername);
            sendError(username, "Admin is already connected to another user");
            return;
        }
        
        // Check if user is already connected to someone else
        if (activeConnections.containsKey(username)) {
            log.warn("User {} is already connected to another session", username);
            sendError(username, "You are already connected to another session");
            return;
        }
        
        // Establish connection
        activeConnections.put(adminUsername, username);
        activeConnections.put(username, adminUsername);
        
        // Remove the session code as it's now used
        activeSessions.remove(request.getCode());
        
        // Notify both parties
        notifySessionConnected(adminUsername, username);
        notifySessionConnected(username, adminUsername);
        
        log.info("Session established between {} and {}", adminUsername, username);
        log.debug("Active connections: {}", activeConnections);
    }
    
    /**
     * End an active session
     */
    @MessageMapping("/session/end")
    public void endSession(SessionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        String username = user != null ? user.getName() : request.getUsername();
        
        log.info("Session end request by {} (authenticated as: {})", 
                request.getUsername(), username);
        
        String connectedPeer = activeConnections.get(username);
        
        if (connectedPeer != null) {
            // Remove both connections
            activeConnections.remove(username);
            activeConnections.remove(connectedPeer);
            
            // Notify the peer
            notifySessionDisconnected(connectedPeer, username);
            
            log.info("Session ended between {} and {}", username, connectedPeer);
            log.debug("Active connections after end: {}", activeConnections);
        } else {
            log.warn("No active session found for user: {}", username);
        }
    }
    
    /**
     * Forward camera frames to the connected peer
     */
    @MessageMapping("/camera/{username}")
    public void forwardCameraFrame(String imageData, @DestinationVariable String username, SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        String sender = user != null ? user.getName() : username;
        
        log.debug("Received camera frame from {} to forward to {}", sender, username);
        String connectedPeer = activeConnections.get(sender);
        
        if (connectedPeer != null) {
            log.debug("Forwarding camera frame from {} to {}", sender, connectedPeer);
            messagingTemplate.convertAndSend("/topic/camera/" + connectedPeer, imageData);
        } else {
            log.warn("Cannot forward camera frame: no connected peer found for {}", sender);
        }
    }
    
    /**
     * Debug endpoint to check active sessions
     */
    @GetMapping("/api/debug/sessions")
    @ResponseBody
    public Map<String, Object> debugSessions() {
        Map<String, Object> result = new HashMap<>();
        result.put("activeSessions", new HashMap<>(activeSessions));
        result.put("activeConnections", new HashMap<>(activeConnections));
        log.info("Debug sessions requested - Active sessions: {}, Active connections: {}", 
                activeSessions.size(), activeConnections.size());
        return result;
    }
    
    /**
     * Debug endpoint to check active connections
     */
    @GetMapping("/api/debug/connections")
    @ResponseBody
    public Map<String, Object> debugConnections() {
        Map<String, Object> result = new HashMap<>();
        result.put("activeConnections", new HashMap<>(activeConnections));
        log.info("Debug connections requested - Active connections: {}", activeConnections.size());
        return result;
    }
    
    /**
     * Send error message to user
     */
    private void sendError(String username, String message) {
        SessionEvent event = new SessionEvent();
        event.setType("error");
        event.setMessage(message);
        
        messagingTemplate.convertAndSend("/topic/session/" + username, event);
        log.warn("Session error for {}: {}", username, message);
    }
    
    /**
     * Notify user of successful connection
     */
    private void notifySessionConnected(String username, String peer) {
        SessionEvent event = new SessionEvent();
        event.setType("connected");
        event.setPeer(peer);
        
        messagingTemplate.convertAndSend("/topic/session/" + username, event);
        log.info("Sent connection notification to {} about peer {}", username, peer);
    }
    
    /**
     * Notify user of session disconnection
     */
    private void notifySessionDisconnected(String username, String peer) {
        SessionEvent event = new SessionEvent();
        event.setType("disconnected");
        event.setPeer(peer);
        
        messagingTemplate.convertAndSend("/topic/session/" + username, event);
        log.info("Sent disconnection notification to {} about peer {}", username, peer);
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