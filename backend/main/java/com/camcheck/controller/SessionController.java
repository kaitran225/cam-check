package com.camcheck.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Controller for managing camera sessions between users
 * Enhanced with audio streaming support
 */
@Controller
@Slf4j
public class SessionController {

    private final SimpMessagingTemplate messagingTemplate;
    
    // Store active sessions: sessionCode -> adminUsername
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();
    
    // Store active connections: username -> connectedPeer
    private final Map<String, String> activeConnections = new ConcurrentHashMap<>();
    
    // Store users with active cameras: username -> lastActivityTimestamp
    private final Map<String, Long> activeUserTimestamps = new ConcurrentHashMap<>();
    
    // Store users with active audio: username -> audioEnabled
    private final Map<String, Boolean> activeAudioUsers = new ConcurrentHashMap<>();
    
    // Store session capabilities: username -> capabilities map
    private final Map<String, Map<String, Boolean>> sessionCapabilities = new ConcurrentHashMap<>();
    
    // Scheduler for cleanup tasks
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    // Activity timeout in milliseconds (10 seconds)
    private static final long ACTIVITY_TIMEOUT = 10000;
    
    public SessionController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        log.info("SessionController initialized with audio support");
        
        // Schedule cleanup task to run every 5 seconds
        scheduler.scheduleAtFixedRate(this::cleanupInactiveUsers, 5, 5, TimeUnit.SECONDS);
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
        
        // Initialize session capabilities
        Map<String, Boolean> capabilities = new HashMap<>();
        capabilities.put("video", true); // Video is always enabled
        capabilities.put("audio", request.isAudioEnabled()); // Audio may be optionally enabled
        sessionCapabilities.put(username, capabilities);
        
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
        event.setCapabilities(capabilities);
        messagingTemplate.convertAndSend("/topic/session/" + username, event);
        
        log.info("Session created with code: {} for user: {}, audio enabled: {}", 
                request.getCode(), username, request.isAudioEnabled());
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
        
        // Get admin's session capabilities
        Map<String, Boolean> capabilities = sessionCapabilities.getOrDefault(adminUsername, 
                Map.of("video", true, "audio", false));
        
        // Initialize user's capabilities based on request and admin's capabilities
        Map<String, Boolean> userCapabilities = new HashMap<>();
        userCapabilities.put("video", true); // Video is always enabled
        // Only enable audio if both sides support it
        userCapabilities.put("audio", request.isAudioEnabled() && capabilities.getOrDefault("audio", false));
        sessionCapabilities.put(username, userCapabilities);
        
        // Update admin's capabilities to match the negotiated capabilities
        capabilities.put("audio", userCapabilities.get("audio"));
        sessionCapabilities.put(adminUsername, capabilities);
        
        // Track audio status if enabled
        if (userCapabilities.get("audio")) {
            activeAudioUsers.put(username, true);
            activeAudioUsers.put(adminUsername, true);
        }
        
        // Establish connection
        activeConnections.put(adminUsername, username);
        activeConnections.put(username, adminUsername);
        
        // Remove the session code as it's now used
        activeSessions.remove(request.getCode());
        
        // Notify both parties with capabilities
        notifySessionConnected(adminUsername, username, capabilities);
        notifySessionConnected(username, adminUsername, userCapabilities);
        
        log.info("Session established between {} and {}, audio enabled: {}", 
                adminUsername, username, userCapabilities.get("audio"));
        log.debug("Active connections: {}", activeConnections);
    }
    
    /**
     * Superuser direct connection to any user with active camera
     */
    @MessageMapping("/session/superuser/connect")
    public void superuserConnect(SessionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        
        if (principal == null || !isSuperuser(principal)) {
            log.warn("Unauthorized superuser connection attempt from {}", 
                    request.getUsername());
            sendError(request.getUsername(), "Unauthorized: Superuser role required");
            return;
        }
        
        String superuserName = principal.getName();
        String targetUser = request.getPeer();
        
        log.info("Superuser {} attempting to connect to {}", superuserName, targetUser);
        
        // Check if target user exists and has active camera
        if (!activeUserTimestamps.containsKey(targetUser)) {
            log.warn("Target user {} not found or has no active camera", targetUser);
            sendError(superuserName, "Target user not found or has no active camera");
            return;
        }
        
        // Check if superuser is already connected to someone else
        if (activeConnections.containsKey(superuserName)) {
            // Disconnect from current peer first
            String currentPeer = activeConnections.get(superuserName);
            activeConnections.remove(superuserName);
            activeConnections.remove(currentPeer);
            notifySessionDisconnected(currentPeer, superuserName);
            log.info("Superuser {} disconnected from current peer {}", superuserName, currentPeer);
        }
        
        // Check if target user is already connected to someone else
        if (activeConnections.containsKey(targetUser)) {
            // Force disconnect the current peer
            String currentPeer = activeConnections.get(targetUser);
            activeConnections.remove(targetUser);
            activeConnections.remove(currentPeer);
            notifySessionDisconnected(currentPeer, targetUser);
            log.info("Forced disconnect between {} and {} for superuser connection", targetUser, currentPeer);
        }
        
        // Initialize capabilities
        Map<String, Boolean> capabilities = new HashMap<>();
        capabilities.put("video", true);
        capabilities.put("audio", request.isAudioEnabled());
        sessionCapabilities.put(superuserName, capabilities);
        sessionCapabilities.put(targetUser, capabilities);
        
        // Track audio status if enabled
        if (request.isAudioEnabled()) {
            activeAudioUsers.put(superuserName, true);
            activeAudioUsers.put(targetUser, true);
        }
        
        // Establish connection
        activeConnections.put(superuserName, targetUser);
        activeConnections.put(targetUser, superuserName);
        
        // Notify both parties
        notifySessionConnected(superuserName, targetUser, capabilities);
        notifySessionConnected(targetUser, superuserName, capabilities);
        
        log.info("Superuser connection established between {} and {}, audio enabled: {}", 
                superuserName, targetUser, request.isAudioEnabled());
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
            
            // Remove from audio users if present
            activeAudioUsers.remove(username);
            activeAudioUsers.remove(connectedPeer);
            
            // Remove capabilities
            sessionCapabilities.remove(username);
            sessionCapabilities.remove(connectedPeer);
            
            // Notify the peer
            notifySessionDisconnected(connectedPeer, username);
            
            log.info("Session ended between {} and {}", username, connectedPeer);
            log.debug("Active connections after end: {}", activeConnections);
        } else {
            log.warn("No active session found for user: {}", username);
        }
    }
    
    /**
     * Toggle audio in an active session
     */
    @MessageMapping("/session/audio/toggle")
    public void toggleAudio(SessionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        String username = user != null ? user.getName() : request.getUsername();
        
        String connectedPeer = activeConnections.get(username);
        if (connectedPeer == null) {
            log.warn("Cannot toggle audio: No active session for user {}", username);
            sendError(username, "No active session found");
            return;
        }
        @SuppressWarnings("unused")
        // Get current capabilities
        Map<String, Boolean> capabilities = sessionCapabilities.getOrDefault(username, 
                Map.of("video", true, "audio", false));
        
        // Get peer capabilities
        Map<String, Boolean> peerCapabilities = sessionCapabilities.getOrDefault(connectedPeer, 
                Map.of("video", true, "audio", false));
        
        // Check if both users have audio capability
        if (!peerCapabilities.getOrDefault("audio", false)) {
            log.warn("Cannot toggle audio: Peer {} does not have audio capability", connectedPeer);
            sendError(username, "Audio is not available in this session");
            return;
        }
        
        // Toggle audio state
        boolean newAudioState = !activeAudioUsers.getOrDefault(username, false);
        activeAudioUsers.put(username, newAudioState);
        
        // Notify both users about the audio state change
        SessionEvent event = new SessionEvent();
        event.setType("audio_state_changed");
        event.setPeer(username);
        event.setAudioEnabled(newAudioState);
        
        messagingTemplate.convertAndSend("/topic/session/" + connectedPeer, event);
        
        // Also notify the user who toggled
        event.setPeer(username); // Set peer to self for clarity
        messagingTemplate.convertAndSend("/topic/session/" + username, event);
        
        log.info("User {} toggled audio to: {}", username, newAudioState);
    }
    
    /**
     * Forward camera frames to the connected peer
     */
    @MessageMapping("/camera/{username}")
    public void forwardCameraFrame(String imageData, @DestinationVariable String username, SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        String sender = user != null ? user.getName() : username;
        
        // Mark this user as having an active camera with current timestamp
        activeUserTimestamps.put(sender, Instant.now().toEpochMilli());
        
        log.debug("Received camera frame from {} to forward to {}", sender, username);
        String connectedPeer = activeConnections.get(sender);
        
        if (connectedPeer != null) {
            log.debug("Forwarding camera frame from {} to {}", sender, connectedPeer);
            messagingTemplate.convertAndSend("/topic/camera/" + connectedPeer, imageData);
        } else {
            log.debug("Cannot forward camera frame: no connected peer found for {}", sender);
        }
    }
    
    /**
     * Forward audio data to the connected peer
     */
    @MessageMapping("/audio/{username}")
    public void forwardAudioData(String audioData, @DestinationVariable String username, SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        String sender = user != null ? user.getName() : username;
        
        // Check if sender has audio enabled
        if (!activeAudioUsers.getOrDefault(sender, false)) {
            log.debug("Ignoring audio from {} - audio not enabled", sender);
            return;
        }
        
        String connectedPeer = activeConnections.get(sender);
        if (connectedPeer != null) {
            // Check if receiver has audio enabled
            if (activeAudioUsers.getOrDefault(connectedPeer, false)) {
                log.debug("Forwarding audio data from {} to {}", sender, connectedPeer);
                messagingTemplate.convertAndSend("/topic/audio/" + connectedPeer, audioData);
            } else {
                log.debug("Not forwarding audio to {} - receiver has audio disabled", connectedPeer);
            }
        } else {
            log.debug("Cannot forward audio: no connected peer found for {}", sender);
        }
    }
    
    /**
     * Client camera heartbeat to keep active status
     */
    @MessageMapping("/camera/heartbeat")
    public void cameraHeartbeat(SessionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        String username = user != null ? user.getName() : request.getUsername();
        
        // Update user's active timestamp
        activeUserTimestamps.put(username, Instant.now().toEpochMilli());
        log.debug("Received heartbeat from {}", username);
    }
    
    /**
     * Get users with active cameras (superuser only)
     */
    @MessageMapping("/session/superuser/active-users")
    public void getActiveUsers(SessionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        
        if (principal == null || !isSuperuser(principal)) {
            log.warn("Unauthorized attempt to get active users from {}", 
                    request.getUsername());
            sendError(request.getUsername(), "Unauthorized: Superuser role required");
            return;
        }
        
        String superuserName = principal.getName();
        
        // Get active users (excluding the superuser)
        Set<String> users = activeUserTimestamps.keySet().stream()
                .filter(user -> !user.equals(superuserName))
                .collect(Collectors.toSet());
        
        // Send list of active users to the superuser
        SessionEvent event = new SessionEvent();
        event.setType("active-users");
        event.setMessage("Users with active cameras: " + String.join(", ", users));
        event.setActiveUsers(users);
        
        messagingTemplate.convertAndSend("/topic/session/" + superuserName, event);
        log.info("Sent active users list to superuser {}: {}", superuserName, users);
    }
    
    /**
     * Clean up inactive users
     */
    private void cleanupInactiveUsers() {
        long currentTime = Instant.now().toEpochMilli();
        
        // Remove users that haven't sent a frame in ACTIVITY_TIMEOUT milliseconds
        activeUserTimestamps.entrySet().removeIf(entry -> {
            boolean isInactive = (currentTime - entry.getValue()) > ACTIVITY_TIMEOUT;
            if (isInactive) {
                String username = entry.getKey();
                log.debug("Removing inactive user: {}", username);
                
                // Also remove from audio users and session capabilities
                activeAudioUsers.remove(username);
                sessionCapabilities.remove(username);
                
                // If user was in a session, notify the peer
                String connectedPeer = activeConnections.remove(username);
                if (connectedPeer != null) {
                    activeConnections.remove(connectedPeer);
                    notifySessionDisconnected(connectedPeer, username);
                    log.info("Removed inactive session between {} and {}", username, connectedPeer);
                }
            }
            return isInactive;
        });
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
        result.put("activeUsers", activeUserTimestamps.keySet());
        result.put("audioEnabledUsers", activeAudioUsers.keySet());
        result.put("sessionCapabilities", sessionCapabilities);
        
        log.info("Debug sessions requested - Active sessions: {}, Active connections: {}, Active users: {}, Audio users: {}", 
                activeSessions.size(), activeConnections.size(), activeUserTimestamps.size(), activeAudioUsers.size());
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
     * Check if the principal has the SUPERUSER role
     */
    private boolean isSuperuser(Principal principal) {
        if (principal instanceof Authentication) {
            Authentication auth = (Authentication) principal;
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            return authorities.stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERUSER"));
        }
        return false;
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
    private void notifySessionConnected(String username, String peer, Map<String, Boolean> capabilities) {
        SessionEvent event = new SessionEvent();
        event.setType("connected");
        event.setPeer(peer);
        event.setCapabilities(capabilities);
        
        messagingTemplate.convertAndSend("/topic/session/" + username, event);
        log.info("Sent connection notification to {} about peer {} with capabilities: {}", 
                username, peer, capabilities);
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
        private boolean audioEnabled;
        
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
        
        public boolean isAudioEnabled() {
            return audioEnabled;
        }
        
        public void setAudioEnabled(boolean audioEnabled) {
            this.audioEnabled = audioEnabled;
        }
    }
    
    /**
     * Session event DTO
     */
    public static class SessionEvent {
        private String type;
        private String peer;
        private String message;
        private Set<String> activeUsers;
        private Map<String, Boolean> capabilities;
        private boolean audioEnabled;
        
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
        
        public Set<String> getActiveUsers() {
            return activeUsers;
        }
        
        public void setActiveUsers(Set<String> activeUsers) {
            this.activeUsers = activeUsers;
        }
        
        public Map<String, Boolean> getCapabilities() {
            return capabilities;
        }
        
        public void setCapabilities(Map<String, Boolean> capabilities) {
            this.capabilities = capabilities;
        }
        
        public boolean isAudioEnabled() {
            return audioEnabled;
        }
        
        public void setAudioEnabled(boolean audioEnabled) {
            this.audioEnabled = audioEnabled;
        }
    }
} 