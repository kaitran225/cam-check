package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.model.WebRTCMessage;
import com.camcheck.service.WebRTCService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for WebRTC signaling and connection management
 */
@RestController
@RequestMapping("/api/webrtc")
@Slf4j
public class WebRTCController {

    private final WebRTCService webRTCService;
    
    @Autowired
    public WebRTCController(WebRTCService webRTCService) {
        this.webRTCService = webRTCService;
    }
    
    /**
     * Initialize a WebRTC connection with another user
     * @param receiverId Receiver user ID
     * @param authentication Authentication object
     * @return Connection configuration
     */
    @PostMapping("/connect/{receiverId}")
    public ResponseEntity<ApiResponse> initializeConnection(
            @PathVariable String receiverId,
            Authentication authentication) {
        
        String initiatorId = authentication.getName();
        
        // Generate a unique connection ID
        String connectionId = UUID.randomUUID().toString();
        
        // Initialize the connection
        Map<String, Object> config = webRTCService.initializeConnection(connectionId, initiatorId, receiverId);
        
        if (config.containsKey("error")) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error((String) config.get("error")));
        }
        
        // Return the configuration
        Map<String, Object> responseData = Map.of(
                "connectionId", connectionId,
                "config", config
        );
        
        return ResponseEntity.ok(ApiResponse.success("WebRTC connection initialized", responseData));
    }
    
    /**
     * Handle WebRTC signaling messages via WebSocket
     * @param message WebRTC message
     * @param headerAccessor Message headers
     * @param principal User principal
     */
    @MessageMapping("/webrtc.signal")
    public void handleSignalingMessage(
            @Payload WebRTCMessage message,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal) {
        
        // Verify sender
        String senderId = principal.getName();
        if (!senderId.equals(message.getSender())) {
            log.warn("Sender ID mismatch: {} vs {}", senderId, message.getSender());
            return;
        }
        
        // Process the signaling message
        webRTCService.processSignalingMessage(message);
    }
    
    /**
     * Close a WebRTC connection
     * @param connectionId Connection ID
     * @param authentication Authentication object
     * @return Response
     */
    @DeleteMapping("/connect/{connectionId}")
    public ResponseEntity<ApiResponse> closeConnection(
            @PathVariable String connectionId,
            Authentication authentication) {
        
        String userId = authentication.getName();
        
        // Close the connection
        webRTCService.closeConnection(connectionId, userId);
        
        return ResponseEntity.ok(ApiResponse.success("WebRTC connection closed"));
    }
    
    /**
     * Get connection status
     * @param connectionId Connection ID
     * @param authentication Authentication object
     * @return Connection status
     */
    @GetMapping("/status/{connectionId}")
    public ResponseEntity<ApiResponse> getConnectionStatus(
            @PathVariable String connectionId,
            Authentication authentication) {
        
        WebRTCService.ConnectionStatus status = webRTCService.getConnectionStatus(connectionId);
        
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> statusData = Map.of(
                "connectionId", connectionId,
                "status", status.name()
        );
        
        return ResponseEntity.ok(ApiResponse.success("Connection status retrieved", statusData));
    }
    
    /**
     * Get ICE server configuration
     * @return ICE server configuration
     */
    @GetMapping("/ice-config")
    public ResponseEntity<ApiResponse> getIceConfiguration() {
        Map<String, Object> config = webRTCService.getIceConfiguration();
        
        return ResponseEntity.ok(ApiResponse.success("ICE configuration retrieved", config));
    }
    
    /**
     * Get WebRTC statistics
     * @return WebRTC statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse> getStatistics() {
        Map<String, Object> stats = webRTCService.getStatistics();
        
        return ResponseEntity.ok(ApiResponse.success("WebRTC statistics retrieved", stats));
    }
} 