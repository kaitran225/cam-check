package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.model.SessionCreateRequest;
import com.camcheck.model.SessionJoinRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * REST controller for session management
 * Alternative to WebSocket-based session management
 */
@RestController
@RequestMapping("/api/v2/sessions")
@Tag(name = "Session Management", description = "REST API endpoints for session management")
@Slf4j
public class SessionRestController {

    private final SimpMessagingTemplate messagingTemplate;
    
    // Store active sessions: sessionCode -> Session
    private static final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    
    // Store active connections: username -> connectedUsername
    private static final Map<String, String> activeConnections = new ConcurrentHashMap<>();
    
    // Store users with active sessions: username -> SessionInfo
    private static final Map<String, SessionInfo> userSessions = new ConcurrentHashMap<>();
    
    @Autowired
    public SessionRestController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Create a new session
     *
     * @param request Session creation request
     * @param authentication Authentication object
     * @return API response with session code
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERUSER')")
    @Operation(summary = "Create session", description = "Create a new session (admin or superuser only)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session created successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> createSession(
            @RequestBody SessionCreateRequest request,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("REST API: Session creation request from: {}", username);
        
        // Check if user already has active sessions
        if (activeConnections.containsKey(username)) {
            log.warn("User {} already has an active session", username);
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("You already have an active session"));
        }
        
        // Generate a random 6-digit code
        String sessionCode = generateSessionCode();
        
        // Calculate expiration time
        Instant expiresAt = Instant.now().plus(request.getExpirationMinutes(), ChronoUnit.MINUTES);
        
        // Create session
        Session session = new Session();
        session.setSessionCode(sessionCode);
        session.setCreator(username);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(expiresAt);
        session.setAudioEnabled(request.isAudioEnabled());
        session.setVideoEnabled(request.isVideoEnabled());
        session.setQuality(request.getQuality());
        session.setRecordingMode(request.getRecordingMode());
        session.setActive(true);
        
        // Store session
        activeSessions.put(sessionCode, session);
        
        // Create SessionInfo for creator
        SessionInfo creatorInfo = new SessionInfo();
        creatorInfo.setUsername(username);
        creatorInfo.setSessionCode(sessionCode);
        creatorInfo.setRole("creator");
        creatorInfo.setDeviceId(request.getDeviceId());
        creatorInfo.setJoinedAt(Instant.now());
        
        // Store creator info
        userSessions.put(username, creatorInfo);
        
        // Schedule session expiration
        scheduleSessionExpiration(sessionCode, request.getExpirationMinutes());
        
        // Notify via WebSocket for clients that listen to both
        notifySessionCreated(username, sessionCode);
        
        // Prepare response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("sessionCode", sessionCode);
        responseData.put("expiresAt", expiresAt.toString());
        responseData.put("expiresIn", request.getExpirationMinutes() * 60); // seconds
        
        log.info("REST API: Session created with code: {} for user: {}", sessionCode, username);
        
        return ResponseEntity.ok(ApiResponse.success("Session created successfully", responseData));
    }
    
    /**
     * Join an existing session
     *
     * @param sessionCode Session code
     * @param request Session join request
     * @param authentication Authentication object
     * @return API response with session info
     */
    @PostMapping("/{sessionCode}/join")
    @Operation(summary = "Join session", description = "Join an existing session by code")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session joined successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> joinSession(
            @PathVariable String sessionCode,
            @RequestBody SessionJoinRequest request,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("REST API: Session join request from: {} for session: {}", username, sessionCode);
        
        // Check if session exists
        Session session = activeSessions.get(sessionCode);
        if (session == null) {
            log.warn("Session not found: {}", sessionCode);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Session not found or expired"));
        }
        
        // Check if session is already active
        if (!session.isActive()) {
            log.warn("Session is not active: {}", sessionCode);
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Session is no longer active"));
        }
        
        // Check if user already has active sessions
        if (activeConnections.containsKey(username)) {
            log.warn("User {} already has an active connection", username);
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("You already have an active connection"));
        }
        
        // Check if creator is connected to someone else
        String creator = session.getCreator();
        if (activeConnections.containsKey(creator)) {
            log.warn("Creator {} is already connected to someone else", creator);
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Session creator is already connected to someone else"));
        }
        
        // Create connection between user and creator
        activeConnections.put(username, creator);
        activeConnections.put(creator, username);
        
        // Create SessionInfo for joiner
        SessionInfo joinerInfo = new SessionInfo();
        joinerInfo.setUsername(username);
        joinerInfo.setSessionCode(sessionCode);
        joinerInfo.setRole("joiner");
        joinerInfo.setDeviceId(request.getDeviceId());
        joinerInfo.setJoinedAt(Instant.now());
        joinerInfo.setAudioEnabled(request.isAudioEnabled() && session.isAudioEnabled());
        joinerInfo.setVideoEnabled(request.isVideoEnabled() && session.isVideoEnabled());
        joinerInfo.setQuality(request.getQuality());
        
        // Store joiner info
        userSessions.put(username, joinerInfo);
        
        // Mark session as connected
        session.setConnectedUser(username);
        session.setConnectedAt(Instant.now());
        
        // Notify both parties via WebSocket
        notifySessionConnected(creator, username, session);
        notifySessionConnected(username, creator, session);
        
        // Prepare response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("sessionId", sessionCode);
        responseData.put("connectedTo", creator);
        responseData.put("audioEnabled", joinerInfo.isAudioEnabled());
        responseData.put("videoEnabled", joinerInfo.isVideoEnabled());
        responseData.put("quality", joinerInfo.getQuality());
        responseData.put("joinedAt", joinerInfo.getJoinedAt().toString());
        
        log.info("REST API: Session joined successfully: {} by user: {}", sessionCode, username);
        
        return ResponseEntity.ok(ApiResponse.success("Session joined successfully", responseData));
    }
    
    /**
     * End an active session
     *
     * @param sessionCode Session code
     * @param authentication Authentication object
     * @return API response
     */
    @DeleteMapping("/{sessionCode}")
    @Operation(summary = "End session", description = "End an active session")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session ended successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> endSession(
            @PathVariable String sessionCode,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("REST API: Session end request from: {} for session: {}", username, sessionCode);
        
        // Check if session exists
        Session session = activeSessions.get(sessionCode);
        if (session == null) {
            log.warn("Session not found: {}", sessionCode);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Session not found"));
        }
        
        // Check if user is part of the session
        if (!username.equals(session.getCreator()) && !username.equals(session.getConnectedUser())) {
            log.warn("User {} is not part of session {}", username, sessionCode);
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not part of this session"));
        }
        
        // Get other user
        String otherUser = username.equals(session.getCreator()) ? session.getConnectedUser() : session.getCreator();
        
        // End session
        endSessionInternal(sessionCode);
        
        // Notify other user if connected
        if (otherUser != null) {
            notifySessionEnded(otherUser, username);
        }
        
        // Calculate session duration
        String duration = "00:00:00";
        if (session.getConnectedAt() != null) {
            long seconds = Instant.now().getEpochSecond() - session.getConnectedAt().getEpochSecond();
            duration = String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        }
        
        // Prepare response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("sessionCode", sessionCode);
        responseData.put("sessionDuration", duration);
        
        log.info("REST API: Session ended successfully: {}", sessionCode);
        
        return ResponseEntity.ok(ApiResponse.success("Session ended successfully", responseData));
    }
    
    /**
     * Get active sessions (for superuser)
     *
     * @param authentication Authentication object
     * @return API response with active sessions
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPERUSER')")
    @Operation(summary = "Get active sessions", description = "Get active sessions (superuser only)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sessions retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getActiveSessions(Authentication authentication) {
        log.info("REST API: Getting active sessions");
        
        // Filter active sessions
        List<Map<String, Object>> sessionsList = activeSessions.values().stream()
                .filter(Session::isActive)
                .map(this::mapSessionToDto)
                .collect(Collectors.toList());
        
        // Prepare response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("sessions", sessionsList);
        responseData.put("count", sessionsList.size());
        
        return ResponseEntity.ok(ApiResponse.success("Active sessions retrieved", responseData));
    }
    
    /**
     * Get user's active session
     *
     * @param authentication Authentication object
     * @return API response with user's active session
     */
    @GetMapping("/my-session")
    @Operation(summary = "Get my session", description = "Get user's active session")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session retrieved successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active session found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getMySession(Authentication authentication) {
        String username = authentication.getName();
        log.debug("REST API: Getting session for user: {}", username);
        
        // Check if user has active session
        SessionInfo sessionInfo = userSessions.get(username);
        if (sessionInfo == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No active session found"));
        }
        
        // Get session details
        Session session = activeSessions.get(sessionInfo.getSessionCode());
        if (session == null) {
            // Clean up stale reference
            userSessions.remove(username);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No active session found"));
        }
        
        // Prepare response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("sessionCode", session.getSessionCode());
        responseData.put("role", sessionInfo.getRole());
        responseData.put("connectedTo", activeConnections.get(username));
        responseData.put("audioEnabled", sessionInfo.isAudioEnabled());
        responseData.put("videoEnabled", sessionInfo.isVideoEnabled());
        responseData.put("quality", sessionInfo.getQuality());
        responseData.put("joinedAt", sessionInfo.getJoinedAt().toString());
        
        return ResponseEntity.ok(ApiResponse.success("Session retrieved", responseData));
    }
    
    /**
     * Update session settings
     *
     * @param sessionCode Session code
     * @param settings Session settings
     * @param authentication Authentication object
     * @return API response
     */
    @PutMapping("/{sessionCode}/settings")
    @Operation(summary = "Update session settings", description = "Update session settings")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings updated successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> updateSessionSettings(
            @PathVariable String sessionCode,
            @RequestBody Map<String, Object> settings,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("REST API: Updating session settings for session: {} by user: {}", sessionCode, username);
        
        // Check if session exists
        Session session = activeSessions.get(sessionCode);
        if (session == null) {
            log.warn("Session not found: {}", sessionCode);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Session not found"));
        }
        
        // Check if user is part of the session
        if (!username.equals(session.getCreator()) && !username.equals(session.getConnectedUser())) {
            log.warn("User {} is not part of session {}", username, sessionCode);
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not part of this session"));
        }
        
        // Update user's session info
        SessionInfo sessionInfo = userSessions.get(username);
        if (sessionInfo != null) {
            // Update settings
            if (settings.containsKey("audioEnabled")) {
                sessionInfo.setAudioEnabled((Boolean) settings.get("audioEnabled"));
            }
            if (settings.containsKey("videoEnabled")) {
                sessionInfo.setVideoEnabled((Boolean) settings.get("videoEnabled"));
            }
            if (settings.containsKey("quality")) {
                sessionInfo.setQuality((String) settings.get("quality"));
            }
            
            // Save updated info
            userSessions.put(username, sessionInfo);
            
            // Notify other user of settings change
            String otherUser = activeConnections.get(username);
            if (otherUser != null) {
                notifySessionSettingsChanged(otherUser, username, settings);
            }
        }
        
        return ResponseEntity.ok(ApiResponse.success("Settings updated successfully"));
    }
    
    /**
     * Generate a random 6-digit session code
     *
     * @return Session code
     */
    private String generateSessionCode() {
        return String.format("%06d", new Random().nextInt(1000000));
    }
    
    /**
     * Schedule session expiration
     *
     * @param sessionCode Session code
     * @param expirationMinutes Expiration time in minutes
     */
    private void scheduleSessionExpiration(String sessionCode, int expirationMinutes) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Session session = activeSessions.get(sessionCode);
                if (session != null && session.getConnectedUser() == null) {
                    log.info("Session expired: {}", sessionCode);
                    endSessionInternal(sessionCode);
                }
            }
        }, expirationMinutes * 60 * 1000);
    }
    
    /**
     * End a session internally
     *
     * @param sessionCode Session code
     */
    private void endSessionInternal(String sessionCode) {
        Session session = activeSessions.get(sessionCode);
        if (session != null) {
            // Mark session as inactive
            session.setActive(false);
            session.setEndedAt(Instant.now());
            
            // Remove connections
            String creator = session.getCreator();
            String connectedUser = session.getConnectedUser();
            
            if (creator != null) {
                activeConnections.remove(creator);
                userSessions.remove(creator);
            }
            
            if (connectedUser != null) {
                activeConnections.remove(connectedUser);
                userSessions.remove(connectedUser);
            }
            
            // Remove session after some time to keep history
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    activeSessions.remove(sessionCode);
                }
            }, 30 * 60 * 1000); // Remove after 30 minutes
        }
    }
    
    /**
     * Map session to DTO for API response
     *
     * @param session Session
     * @return Session DTO
     */
    private Map<String, Object> mapSessionToDto(Session session) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("sessionCode", session.getSessionCode());
        dto.put("creator", session.getCreator());
        dto.put("connectedUser", session.getConnectedUser());
        dto.put("createdAt", session.getCreatedAt().toString());
        dto.put("expiresAt", session.getExpiresAt().toString());
        dto.put("audioEnabled", session.isAudioEnabled());
        dto.put("videoEnabled", session.isVideoEnabled());
        dto.put("quality", session.getQuality());
        dto.put("recordingMode", session.getRecordingMode());
        dto.put("active", session.isActive());
        
        if (session.getConnectedAt() != null) {
            dto.put("connectedAt", session.getConnectedAt().toString());
        }
        
        if (session.getEndedAt() != null) {
            dto.put("endedAt", session.getEndedAt().toString());
        }
        
        return dto;
    }
    
    /**
     * Notify session created via WebSocket
     *
     * @param username Username
     * @param sessionCode Session code
     */
    private void notifySessionCreated(String username, String sessionCode) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "created");
        message.put("sessionCode", sessionCode);
        message.put("timestamp", Instant.now().toString());
        
        messagingTemplate.convertAndSendToUser(username, "/topic/session", message);
    }
    
    /**
     * Notify session connected via WebSocket
     *
     * @param username Username to notify
     * @param connectedUser Connected user
     * @param session Session
     */
    private void notifySessionConnected(String username, String connectedUser, Session session) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "connected");
        message.put("sessionCode", session.getSessionCode());
        message.put("peer", connectedUser);
        message.put("audioEnabled", session.isAudioEnabled());
        message.put("videoEnabled", session.isVideoEnabled());
        message.put("quality", session.getQuality());
        message.put("timestamp", Instant.now().toString());
        
        messagingTemplate.convertAndSendToUser(username, "/topic/session", message);
    }
    
    /**
     * Notify session ended via WebSocket
     *
     * @param username Username to notify
     * @param initiator User who ended the session
     */
    private void notifySessionEnded(String username, String initiator) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "disconnected");
        message.put("peer", initiator);
        message.put("reason", "Session ended by " + initiator);
        message.put("timestamp", Instant.now().toString());
        
        messagingTemplate.convertAndSendToUser(username, "/topic/session", message);
    }
    
    /**
     * Notify session settings changed via WebSocket
     *
     * @param username Username to notify
     * @param initiator User who changed the settings
     * @param settings Changed settings
     */
    private void notifySessionSettingsChanged(String username, String initiator, Map<String, Object> settings) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "settings_changed");
        message.put("peer", initiator);
        message.put("settings", settings);
        message.put("timestamp", Instant.now().toString());
        
        messagingTemplate.convertAndSendToUser(username, "/topic/session", message);
    }
    
    /**
     * Session model
     */
    private static class Session {
        private String sessionCode;
        private String creator;
        private String connectedUser;
        private Instant createdAt;
        private Instant expiresAt;
        private Instant connectedAt;
        private Instant endedAt;
        private boolean audioEnabled;
        private boolean videoEnabled;
        private String quality;
        private String recordingMode;
        private boolean active;
        
        public String getSessionCode() {
            return sessionCode;
        }
        
        public void setSessionCode(String sessionCode) {
            this.sessionCode = sessionCode;
        }
        
        public String getCreator() {
            return creator;
        }
        
        public void setCreator(String creator) {
            this.creator = creator;
        }
        
        public String getConnectedUser() {
            return connectedUser;
        }
        
        public void setConnectedUser(String connectedUser) {
            this.connectedUser = connectedUser;
        }
        
        public Instant getCreatedAt() {
            return createdAt;
        }
        
        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
        
        public Instant getExpiresAt() {
            return expiresAt;
        }
        
        public void setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
        
        public Instant getConnectedAt() {
            return connectedAt;
        }
        
        public void setConnectedAt(Instant connectedAt) {
            this.connectedAt = connectedAt;
        }
        
        public Instant getEndedAt() {
            return endedAt;
        }
        
        public void setEndedAt(Instant endedAt) {
            this.endedAt = endedAt;
        }
        
        public boolean isAudioEnabled() {
            return audioEnabled;
        }
        
        public void setAudioEnabled(boolean audioEnabled) {
            this.audioEnabled = audioEnabled;
        }
        
        public boolean isVideoEnabled() {
            return videoEnabled;
        }
        
        public void setVideoEnabled(boolean videoEnabled) {
            this.videoEnabled = videoEnabled;
        }
        
        public String getQuality() {
            return quality;
        }
        
        public void setQuality(String quality) {
            this.quality = quality;
        }
        
        public String getRecordingMode() {
            return recordingMode;
        }
        
        public void setRecordingMode(String recordingMode) {
            this.recordingMode = recordingMode;
        }
        
        public boolean isActive() {
            return active;
        }
        
        public void setActive(boolean active) {
            this.active = active;
        }
    }
    
    /**
     * Session info model for users
     */
    private static class SessionInfo {
        private String username;
        private String sessionCode;
        private String role;
        private String deviceId;
        private Instant joinedAt;
        private boolean audioEnabled;
        private boolean videoEnabled;
        private String quality;
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getSessionCode() {
            return sessionCode;
        }
        
        public void setSessionCode(String sessionCode) {
            this.sessionCode = sessionCode;
        }
        
        public String getRole() {
            return role;
        }
        
        public void setRole(String role) {
            this.role = role;
        }
        
        public String getDeviceId() {
            return deviceId;
        }
        
        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }
        
        public Instant getJoinedAt() {
            return joinedAt;
        }
        
        public void setJoinedAt(Instant joinedAt) {
            this.joinedAt = joinedAt;
        }
        
        public boolean isAudioEnabled() {
            return audioEnabled;
        }
        
        public void setAudioEnabled(boolean audioEnabled) {
            this.audioEnabled = audioEnabled;
        }
        
        public boolean isVideoEnabled() {
            return videoEnabled;
        }
        
        public void setVideoEnabled(boolean videoEnabled) {
            this.videoEnabled = videoEnabled;
        }
        
        public String getQuality() {
            return quality;
        }
        
        public void setQuality(String quality) {
            this.quality = quality;
        }
    }
} 