package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class WebRTCService {

    private final SimpMessagingTemplate messagingTemplate;
    
    // Store active connections: connectionId -> ConnectionInfo
    private final Map<String, WebRTCConnection> activeConnections = new ConcurrentHashMap<>();
    
    @Value("${camcheck.webrtc.stun-servers:stun:stun.l.google.com:19302}")
    private String stunServers;
    
    @Value("${camcheck.webrtc.turn-servers:}")
    private String turnServers;
    
    @Value("${camcheck.webrtc.turn-username:}")
    private String turnUsername;
    
    @Value("${camcheck.webrtc.turn-credential:}")
    private String turnCredential;
    
    @Autowired
    public WebRTCService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Initialize a WebRTC connection between two users
     */
    public Map<String, Object> initializeConnection(String connectionId, String initiatorId, String receiverId, Map<String, Object> options) {
        // Check if receiver exists and is available
        // In a real application, you would check against your user service
        
        // Create connection info
        WebRTCConnection connection = new WebRTCConnection(connectionId, initiatorId, receiverId);
        activeConnections.put(connectionId, connection);
        
        // Get ICE configuration
        Map<String, Object> config = getIceConfiguration();
        
        // Add any custom options
        if (options != null) {
            config.putAll(options);
        }
        
        return config;
    }
    
    /**
     * Get ICE server configuration
     */
    public Map<String, Object> getIceConfiguration() {
        Map<String, Object> config = new HashMap<>();
        
        // Add ICE servers
        Map<String, Object> iceServer = new HashMap<>();
        iceServer.put("urls", stunServers.split(","));
        
        if (!turnServers.isEmpty()) {
            Map<String, Object> turnServer = new HashMap<>();
            turnServer.put("urls", turnServers.split(","));
            if (!turnUsername.isEmpty()) {
                turnServer.put("username", turnUsername);
                turnServer.put("credential", turnCredential);
            }
            config.put("iceServers", new Map[]{iceServer, turnServer});
        } else {
            config.put("iceServers", new Map[]{iceServer});
        }
        
        return config;
    }
    
    /**
     * Send session description to peer
     */
    public boolean sendSessionDescription(String connectionId, String username, Map<String, Object> sdp) {
        WebRTCConnection connection = activeConnections.get(connectionId);
        if (connection == null) {
            return false;
        }
        
        // Get the peer's username
        String peerId = username.equals(connection.initiatorId) ? connection.receiverId : connection.initiatorId;
        
        // Send SDP to peer
        Map<String, Object> message = new HashMap<>();
        message.put("type", "sdp");
        message.put("connectionId", connectionId);
        message.put("sdp", sdp);
        
        messagingTemplate.convertAndSendToUser(peerId, "/queue/webrtc", message);
        return true;
    }
    
    /**
     * Send ICE candidate to peer
     */
    public boolean sendIceCandidate(String connectionId, String username, Map<String, Object> candidate) {
        WebRTCConnection connection = activeConnections.get(connectionId);
        if (connection == null) {
            return false;
        }
        
        // Get the peer's username
        String peerId = username.equals(connection.initiatorId) ? connection.receiverId : connection.initiatorId;
        
        // Send candidate to peer
        Map<String, Object> message = new HashMap<>();
        message.put("type", "candidate");
        message.put("connectionId", connectionId);
        message.put("candidate", candidate);
        
        messagingTemplate.convertAndSendToUser(peerId, "/queue/webrtc", message);
        return true;
    }
    
    /**
     * End WebRTC connection
     */
    public boolean endConnection(String connectionId, String username) {
        WebRTCConnection connection = activeConnections.get(connectionId);
        if (connection == null) {
            return false;
        }
        
        // Verify user is part of the connection
        if (!username.equals(connection.initiatorId) && !username.equals(connection.receiverId)) {
            return false;
        }
        
        // Get the peer's username
        String peerId = username.equals(connection.initiatorId) ? connection.receiverId : connection.initiatorId;
        
        // Send end connection message to peer
        Map<String, Object> message = new HashMap<>();
        message.put("type", "end");
        message.put("connectionId", connectionId);
        
        messagingTemplate.convertAndSendToUser(peerId, "/queue/webrtc", message);
        
        // Remove connection
        activeConnections.remove(connectionId);
        return true;
    }
    
    /**
     * Get WebRTC service statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeConnections", activeConnections.size());
        stats.put("stunServers", stunServers.split(",").length);
        stats.put("turnServersConfigured", !turnServers.isEmpty());
        return stats;
    }
    
    /**
     * WebRTC connection information
     */
    private static class WebRTCConnection {
        final String connectionId;
        final String initiatorId;
        final String receiverId;
        
        WebRTCConnection(String connectionId, String initiatorId, String receiverId) {
            this.connectionId = connectionId;
            this.initiatorId = initiatorId;
            this.receiverId = receiverId;
        }
    }
} 