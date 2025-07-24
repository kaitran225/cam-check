package com.camcheck.controller.v1;

import com.camcheck.model.ApiResponse;
import com.camcheck.model.SessionCreateRequest;
import com.camcheck.model.SessionJoinRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified session controller for all clients
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "API endpoints for session management")
@Slf4j
public class SessionController extends BaseController {

    private final SimpMessagingTemplate messagingTemplate;
    
    // Store active sessions: sessionId -> Session
    private static final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    
    // Store user sessions: username -> List<SessionInfo>
    private static final Map<String, List<SessionInfo>> userSessions = new ConcurrentHashMap<>();
    
    @Autowired
    public SessionController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Create a new session
     *
     * @param request Session creation request
     * @param authentication Authentication object
     * @return Session details
     */
    @PostMapping
    @Operation(summary = "Create session", description = "Create a new session")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session created successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> createSession(
            @RequestBody SessionCreateRequest request,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Creating session for user: {}", username);
        
        // Generate session ID (in a real app, use a more robust method)
        String sessionId = generateSessionId();
        
        // Create session
        Session session = new Session();
        session.sessionId = sessionId;
        session.name = request.getName();
        session.createdBy = username;
        session.createdAt = Instant.now();
        session.participants.add(username);
        session.settings.putAll(request.getSettings());
        
        // Store session
        activeSessions.put(sessionId, session);
        
        // Add to user's sessions
        userSessions.computeIfAbsent(username, k -> new ArrayList<>())
                .add(new SessionInfo(sessionId, request.getName(), username));
        
        // Create response data
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("sessionId", sessionId);
        responseData.put("name", request.getName());
        responseData.put("settings", session.settings);
        
        return success("Session created successfully", responseData);
    }
    
    /**
     * Join an existing session
     *
     * @param request Session join request
     * @param authentication Authentication object
     * @return Session details
     */
    @PostMapping("/join")
    @Operation(summary = "Join session", description = "Join an existing session")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Joined session successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> joinSession(
            @RequestBody SessionJoinRequest request,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("User {} joining session: {}", username, request.getSessionId());
        
        // Get session
        Session session = activeSessions.get(request.getSessionId());
        if (session == null) {
            return error("Session not found", 404);
        }
        
        // Check if user is already in session
        if (session.participants.contains(username)) {
            return error("Already in session");
        }
        
        // Add user to session
        session.participants.add(username);
        
        // Add to user's sessions
        userSessions.computeIfAbsent(username, k -> new ArrayList<>())
                .add(new SessionInfo(session.sessionId, session.name, session.createdBy));
        
        // Notify other participants
        Map<String, Object> joinMessage = new HashMap<>();
        joinMessage.put("type", "participant_joined");
        joinMessage.put("username", username);
        joinMessage.put("timestamp", Instant.now());
        
        session.participants.forEach(participant -> {
            if (!participant.equals(username)) {
                messagingTemplate.convertAndSendToUser(
                        participant,
                        "/queue/session/" + session.sessionId,
                        joinMessage
                );
            }
        });
        
        // Create response data
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("sessionId", session.sessionId);
        responseData.put("name", session.name);
        responseData.put("createdBy", session.createdBy);
        responseData.put("participants", session.participants);
        responseData.put("settings", session.settings);
        
        return success("Joined session successfully", responseData);
    }
    
    /**
     * Leave a session
     *
     * @param sessionId Session ID
     * @param authentication Authentication object
     * @return Success response
     */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Leave session", description = "Leave a session")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Left session successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> leaveSession(
            @PathVariable String sessionId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("User {} leaving session: {}", username, sessionId);
        
        // Get session
        Session session = activeSessions.get(sessionId);
        if (session == null) {
            return error("Session not found", 404);
        }
        
        // Remove user from session
        session.participants.remove(username);
        
        // Remove from user's sessions
        List<SessionInfo> userSessionList = userSessions.get(username);
        if (userSessionList != null) {
            userSessionList.removeIf(info -> info.sessionId.equals(sessionId));
        }
        
        // If session is empty and not created by this user, remove it
        if (session.participants.isEmpty() && !username.equals(session.createdBy)) {
            activeSessions.remove(sessionId);
        }
        
        // Notify other participants
        Map<String, Object> leaveMessage = new HashMap<>();
        leaveMessage.put("type", "participant_left");
        leaveMessage.put("username", username);
        leaveMessage.put("timestamp", Instant.now());
        
        session.participants.forEach(participant -> 
            messagingTemplate.convertAndSendToUser(
                    participant,
                    "/queue/session/" + sessionId,
                    leaveMessage
            )
        );
        
        return success("Left session successfully");
    }
    
    /**
     * Get user's active sessions
     *
     * @param authentication Authentication object
     * @return List of active sessions
     */
    @GetMapping
    @Operation(summary = "Get sessions", description = "Get user's active sessions")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sessions retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getSessions(Authentication authentication) {
        String username = authentication.getName();
        log.debug("Getting sessions for user: {}", username);
        
        List<SessionInfo> sessions = userSessions.getOrDefault(username, new ArrayList<>());
        
        // Filter out any sessions that no longer exist
        sessions.removeIf(info -> !activeSessions.containsKey(info.sessionId));
        
        return success("Sessions retrieved successfully", Map.of("sessions", sessions));
    }
    
    /**
     * Get session details
     *
     * @param sessionId Session ID
     * @param authentication Authentication object
     * @return Session details
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "Get session details", description = "Get details of a specific session")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session details retrieved successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getSessionDetails(
            @PathVariable String sessionId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Getting details for session: {}", sessionId);
        
        // Get session
        Session session = activeSessions.get(sessionId);
        if (session == null) {
            return error("Session not found", 404);
        }
        
        // Check if user is in session
        if (!session.participants.contains(username)) {
            return error("Not a participant in this session", 403);
        }
        
        // Create response data
        Map<String, Object> sessionDetails = new HashMap<>();
        sessionDetails.put("sessionId", session.sessionId);
        sessionDetails.put("name", session.name);
        sessionDetails.put("createdBy", session.createdBy);
        sessionDetails.put("createdAt", session.createdAt);
        sessionDetails.put("participants", session.participants);
        sessionDetails.put("settings", session.settings);
        
        return success("Session details retrieved", sessionDetails);
    }
    
    /**
     * Update session settings
     *
     * @param sessionId Session ID
     * @param settings New settings
     * @param authentication Authentication object
     * @return Updated session details
     */
    @PutMapping("/{sessionId}/settings")
    @Operation(summary = "Update session settings", description = "Update settings of a specific session")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings updated successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> updateSessionSettings(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> settings,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Updating settings for session: {}", sessionId);
        
        // Get session
        Session session = activeSessions.get(sessionId);
        if (session == null) {
            return error("Session not found", 404);
        }
        
        // Check if user is session creator
        if (!username.equals(session.createdBy)) {
            return error("Only session creator can update settings", 403);
        }
        
        // Update settings
        session.settings.putAll(settings);
        
        // Notify participants
        Map<String, Object> updateMessage = new HashMap<>();
        updateMessage.put("type", "settings_updated");
        updateMessage.put("settings", session.settings);
        updateMessage.put("timestamp", Instant.now());
        
        session.participants.forEach(participant ->
            messagingTemplate.convertAndSendToUser(
                    participant,
                    "/queue/session/" + sessionId,
                    updateMessage
            )
        );
        
        return success("Settings updated successfully", Map.of("settings", session.settings));
    }
    
    /**
     * Generate a unique session ID
     */
    private String generateSessionId() {
        return Long.toHexString(System.currentTimeMillis());
    }
    
    /**
     * Session information
     */
    private static class Session {
        String sessionId;
        String name;
        String createdBy;
        Instant createdAt;
        List<String> participants = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
    }
    
    /**
     * Session information for user
     */
    private static class SessionInfo {
        String sessionId;
        String name;
        String createdBy;
        
        SessionInfo(String sessionId, String name, String createdBy) {
            this.sessionId = sessionId;
            this.name = name;
            this.createdBy = createdBy;
        }
    }
} 