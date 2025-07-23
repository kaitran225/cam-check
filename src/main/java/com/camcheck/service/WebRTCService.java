package com.camcheck.service;

import com.camcheck.model.WebRTCMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

/**
 * Service for handling WebRTC connections and signaling
 * Provides functionality for peer-to-peer streaming between clients
 */
@Service
@Slf4j
public class WebRTCService {

    private final SimpMessagingTemplate messagingTemplate;
    
    @Value("${camcheck.webrtc.stun-servers:stun:stun.l.google.com:19302}")
    private String stunServers;
    
    @Value("${camcheck.webrtc.turn-servers:}")
    private String turnServers;
    
    @Value("${camcheck.webrtc.turn-username:}")
    private String turnUsername;
    
    @Value("${camcheck.webrtc.turn-credential:}")
    private String turnCredential;
    
    @Value("${camcheck.webrtc.enabled:true}")
    private boolean webRtcEnabled;
    
    @Value("${camcheck.webrtc.ice-transport-policy:all}")
    private String iceTransportPolicy;
    
    @Value("${camcheck.webrtc.bundle-policy:balanced}")
    private String bundlePolicy;
    
    // Map of active WebRTC connections (connectionId -> connection details)
    private final Map<String, WebRTCConnection> connections = new ConcurrentHashMap<>();
    
    // Map of user to connection ID
    private final Map<String, String> userConnections = new ConcurrentHashMap<>();
    
    public WebRTCService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Initialize a new WebRTC connection between two users
     * @param connectionId Connection identifier
     * @param initiator User who initiated the connection
     * @param receiver User who receives the connection
     * @return Connection configuration
     */
    public Map<String, Object> initializeConnection(String connectionId, String initiator, String receiver) {
        return initializeConnection(connectionId, initiator, receiver, new HashMap<>());
    }
    
    /**
     * Initialize a new WebRTC connection between two users with options
     * @param connectionId Connection identifier
     * @param initiator User who initiated the connection
     * @param receiver User who receives the connection
     * @param options Additional options for the connection
     * @return Connection configuration
     */
    public Map<String, Object> initializeConnection(String connectionId, String initiator, String receiver, Map<String, Object> options) {
        if (!webRtcEnabled) {
            log.warn("WebRTC is disabled, cannot initialize connection");
            return Map.of("error", "WebRTC is disabled");
        }
        
        log.info("Initializing WebRTC connection: {} between {} and {} with options: {}", 
                connectionId, initiator, receiver, options);
        
        // Create connection object
        WebRTCConnection connection = new WebRTCConnection();
        connection.setConnectionId(connectionId);
        connection.setInitiator(initiator);
        connection.setReceiver(receiver);
        connection.setStatus(ConnectionStatus.INITIALIZING);
        connection.setCreatedAt(System.currentTimeMillis());
        
        // Store options in metadata
        connection.getMetadata().putAll(options);
        
        // Store connection
        connections.put(connectionId, connection);
        userConnections.put(initiator, connectionId);
        userConnections.put(receiver, connectionId);
        
        // Get base ICE configuration
        Map<String, Object> config = getIceConfiguration();
        
        // Add any custom ICE transport policy from options
        if (options.containsKey("iceTransportPolicy")) {
            config.put("iceTransportPolicy", options.get("iceTransportPolicy"));
        }
        
        // Add media constraints if provided
        if (options.containsKey("videoConstraints")) {
            config.put("videoConstraints", options.get("videoConstraints"));
        }
        
        if (options.containsKey("audioConstraints")) {
            config.put("audioConstraints", options.get("audioConstraints"));
        }
        
        return config;
    }
    
    /**
     * Send ICE candidates to peer
     * @param connectionId Connection identifier
     * @param sender User sending the candidates
     * @param candidates ICE candidates
     * @return True if sent successfully, false otherwise
     */
    public boolean sendIceCandidates(String connectionId, String sender, Map<String, Object> candidates) {
        WebRTCConnection connection = connections.get(connectionId);
        if (connection == null) {
            log.warn("Cannot send ICE candidates for unknown connection: {}", connectionId);
            return false;
        }
        
        // Verify the user is part of this connection
        if (!sender.equals(connection.getInitiator()) && !sender.equals(connection.getReceiver())) {
            log.warn("User {} not authorized for connection {}", sender, connectionId);
            return false;
        }
        
        // Get the recipient
        String recipient = sender.equals(connection.getInitiator()) 
                ? connection.getReceiver() : connection.getInitiator();
        
        // Create WebRTC message
        WebRTCMessage message = new WebRTCMessage();
        message.setConnectionId(connectionId);
        message.setSender(sender);
        message.setType("ice-candidate");
        message.setData(candidates);
        
        // Send to recipient
        messagingTemplate.convertAndSendToUser(
                recipient,
                "/queue/webrtc",
                message
        );
        
        log.debug("Sent ICE candidates from {} to {} for connection {}", 
                sender, recipient, connectionId);
        
        // Update connection status
        if (connection.getStatus() == ConnectionStatus.INITIALIZING) {
            connection.setStatus(ConnectionStatus.ICE_GATHERING);
        }
        
        return true;
    }
    
    /**
     * Send session description (offer or answer) to peer
     * @param connectionId Connection identifier
     * @param sender User sending the session description
     * @param sdp Session description
     * @return True if sent successfully, false otherwise
     */
    public boolean sendSessionDescription(String connectionId, String sender, Map<String, Object> sdp) {
        WebRTCConnection connection = connections.get(connectionId);
        if (connection == null) {
            log.warn("Cannot send session description for unknown connection: {}", connectionId);
            return false;
        }
        
        // Verify the user is part of this connection
        if (!sender.equals(connection.getInitiator()) && !sender.equals(connection.getReceiver())) {
            log.warn("User {} not authorized for connection {}", sender, connectionId);
            return false;
        }
        
        // Get the recipient
        String recipient = sender.equals(connection.getInitiator()) 
                ? connection.getReceiver() : connection.getInitiator();
        
        // Create WebRTC message
        WebRTCMessage message = new WebRTCMessage();
        message.setConnectionId(connectionId);
        message.setSender(sender);
        
        // Determine message type based on who is sending (initiator sends offer, receiver sends answer)
        String type = sender.equals(connection.getInitiator()) ? "offer" : "answer";
        message.setType(type);
        message.setData(sdp);
        
        // Send to recipient
        messagingTemplate.convertAndSendToUser(
                recipient,
                "/queue/webrtc",
                message
        );
        
        log.debug("Sent {} from {} to {} for connection {}", 
                type, sender, recipient, connectionId);
        
        // Update connection status
        if (type.equals("offer")) {
            connection.setStatus(ConnectionStatus.OFFER_CREATED);
        } else if (type.equals("answer")) {
            connection.setStatus(ConnectionStatus.ANSWER_CREATED);
        }
        
        return true;
    }
    
    /**
     * End a WebRTC connection
     * @param connectionId Connection identifier
     * @param username User ending the connection
     * @return True if ended successfully, false otherwise
     */
    public boolean endConnection(String connectionId, String username) {
        WebRTCConnection connection = connections.get(connectionId);
        if (connection == null) {
            log.warn("Cannot end unknown WebRTC connection: {}", connectionId);
            return false;
        }
        
        // Verify the user is part of this connection
        if (!username.equals(connection.getInitiator()) && !username.equals(connection.getReceiver())) {
            log.warn("User {} not authorized to end connection {}", username, connectionId);
            return false;
        }
        
        // Close the connection
        closeConnection(connectionId, username);
        return true;
    }
    
    /**
     * Process a WebRTC signaling message
     * @param message WebRTC message
     */
    public void processSignalingMessage(WebRTCMessage message) {
        if (!webRtcEnabled) {
            log.warn("WebRTC is disabled, ignoring signaling message");
            return;
        }
        
        String connectionId = message.getConnectionId();
        String sender = message.getSender();
        String type = message.getType();
        
        log.debug("Processing WebRTC signaling message: {} from {} for connection {}", 
                type, sender, connectionId);
        
        WebRTCConnection connection = connections.get(connectionId);
        if (connection == null) {
            log.warn("Received signaling message for unknown connection: {}", connectionId);
            return;
        }
        
        // Update connection status based on message type
        switch (type) {
            case "offer":
                connection.setStatus(ConnectionStatus.OFFER_CREATED);
                break;
            case "answer":
                connection.setStatus(ConnectionStatus.ANSWER_CREATED);
                break;
            case "ice-candidate":
                if (connection.getStatus() == ConnectionStatus.INITIALIZING) {
                    connection.setStatus(ConnectionStatus.ICE_GATHERING);
                }
                break;
            case "connection-established":
                connection.setStatus(ConnectionStatus.CONNECTED);
                log.info("WebRTC connection established: {}", connectionId);
                break;
            case "connection-failed":
                connection.setStatus(ConnectionStatus.FAILED);
                log.warn("WebRTC connection failed: {}", connectionId);
                break;
            case "connection-closed":
                connection.setStatus(ConnectionStatus.CLOSED);
                log.info("WebRTC connection closed: {}", connectionId);
                break;
        }
        
        // Forward the message to the other peer
        String recipient = sender.equals(connection.getInitiator()) 
                ? connection.getReceiver() : connection.getInitiator();
        
        // Send the message to the recipient
        messagingTemplate.convertAndSendToUser(
                recipient,
                "/queue/webrtc",
                message
        );
        
        log.debug("Forwarded WebRTC signaling message to {}", recipient);
    }
    
    /**
     * Close a WebRTC connection
     * @param connectionId Connection identifier
     * @param userId User identifier
     */
    public void closeConnection(String connectionId, String userId) {
        WebRTCConnection connection = connections.get(connectionId);
        if (connection == null) {
            log.warn("Attempt to close unknown WebRTC connection: {}", connectionId);
            return;
        }
        
        // Verify the user is part of this connection
        if (!userId.equals(connection.getInitiator()) && !userId.equals(connection.getReceiver())) {
            log.warn("User {} not authorized to close connection {}", userId, connectionId);
            return;
        }
        
        // Update connection status
        connection.setStatus(ConnectionStatus.CLOSED);
        
        // Notify the other peer
        String otherPeer = userId.equals(connection.getInitiator()) 
                ? connection.getReceiver() : connection.getInitiator();
        
        WebRTCMessage closeMessage = new WebRTCMessage();
        closeMessage.setConnectionId(connectionId);
        closeMessage.setSender(userId);
        closeMessage.setType("connection-closed");
        closeMessage.setData(Map.of("reason", "Peer closed connection"));
        
        messagingTemplate.convertAndSendToUser(
                otherPeer,
                "/queue/webrtc",
                closeMessage
        );
        
        // Remove connection mappings
        userConnections.remove(connection.getInitiator());
        userConnections.remove(connection.getReceiver());
        
        // Keep the connection in the map for history but mark as closed
        log.info("Closed WebRTC connection: {}", connectionId);
    }
    
    /**
     * Get connection status
     * @param connectionId Connection identifier
     * @return Connection status or null if not found
     */
    public ConnectionStatus getConnectionStatus(String connectionId) {
        WebRTCConnection connection = connections.get(connectionId);
        return connection != null ? connection.getStatus() : null;
    }
    
    /**
     * Get active connection for a user
     * @param userId User identifier
     * @return Connection ID or null if not found
     */
    public String getUserConnection(String userId) {
        return userConnections.get(userId);
    }
    
    /**
     * Check if a user has an active WebRTC connection
     * @param userId User identifier
     * @return True if the user has an active connection
     */
    public boolean hasActiveConnection(String userId) {
        String connectionId = userConnections.get(userId);
        if (connectionId == null) {
            return false;
        }
        
        WebRTCConnection connection = connections.get(connectionId);
        return connection != null && connection.getStatus() == ConnectionStatus.CONNECTED;
    }
    
    /**
     * Get ICE server configuration
     * @return Map with ICE configuration
     */
    public Map<String, Object> getIceConfiguration() {
        // Parse STUN servers
        String[] stunServerList = stunServers.split(",");
        
        // Parse TURN servers
        String[] turnServerList = turnServers.isEmpty() ? new String[0] : turnServers.split(",");
        
        // Build ICE server configuration
        return Map.of(
            "iceServers", buildIceServerList(stunServerList, turnServerList),
            "iceTransportPolicy", iceTransportPolicy,
            "bundlePolicy", bundlePolicy
        );
    }
    
    /**
     * Build ICE server list for WebRTC configuration
     * @param stunServers STUN server URLs
     * @param turnServers TURN server URLs
     * @return List of ICE server configurations
     */
    private Object buildIceServerList(String[] stunServers, String[] turnServers) {
        // Start with STUN servers (no auth required)
        Map<String, Object>[] iceServers = new Map[stunServers.length + turnServers.length];
        
        for (int i = 0; i < stunServers.length; i++) {
            iceServers[i] = Map.of(
                "urls", stunServers[i].trim()
            );
        }
        
        // Add TURN servers (with auth if provided)
        for (int i = 0; i < turnServers.length; i++) {
            if (!turnUsername.isEmpty() && !turnCredential.isEmpty()) {
                iceServers[stunServers.length + i] = Map.of(
                    "urls", turnServers[i].trim(),
                    "username", turnUsername,
                    "credential", turnCredential
                );
            } else {
                iceServers[stunServers.length + i] = Map.of(
                    "urls", turnServers[i].trim()
                );
            }
        }
        
        return iceServers;
    }
    
    /**
     * Get WebRTC statistics for analytics
     * @return Map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Connection statistics
        stats.put("active_connections", connections.size());
        stats.put("total_connections", connections.size()); // This seems to be a typo in the original, should be totalConnections.size()
        stats.put("failed_connections", 0); // This needs to be calculated based on connections
        
        // ICE statistics
        Map<String, Integer> iceStats = new HashMap<>();
        int direct = 0, relay = 0, srflx = 0, prflx = 0;
        for (WebRTCConnection connection : connections.values()) {
            if (connection.getStatus() == ConnectionStatus.CONNECTED) {
                // This part of the logic needs to be implemented based on actual ICE types
                // For now, we'll just count all connected connections as direct for simplicity
                direct++;
            }
        }
        iceStats.put("direct", direct);
        iceStats.put("relay", 0); // This needs to be calculated
        iceStats.put("srflx", 0); // This needs to be calculated
        iceStats.put("prflx", 0); // This needs to be calculated
        stats.put("ice_connections", iceStats);
        
        // Signaling statistics
        stats.put("signaling_messages", 0); // This needs to be calculated
        
        // Media statistics
        Map<String, Object> mediaStats = new HashMap<>();
        mediaStats.put("video_enabled", false); // This needs to be calculated
        mediaStats.put("audio_enabled", false); // This needs to be calculated
        mediaStats.put("data_channel_enabled", false); // This needs to be calculated
        stats.put("media", mediaStats);
        
        return stats;
    }
    
    /**
     * Reset all connections (for testing or system reset)
     */
    public void resetAllConnections() {
        connections.clear();
        userConnections.clear();
        log.info("Reset all WebRTC connections");
    }
    
    /**
     * Enum for connection status
     */
    public enum ConnectionStatus {
        INITIALIZING,
        OFFER_CREATED,
        ANSWER_CREATED,
        ICE_GATHERING,
        CONNECTED,
        FAILED,
        CLOSED
    }
    
    /**
     * Class representing a WebRTC connection
     */
    public static class WebRTCConnection {
        private String connectionId;
        private String initiator;
        private String receiver;
        private ConnectionStatus status;
        private long createdAt;
        private Map<String, Object> metadata = new ConcurrentHashMap<>();
        
        public String getConnectionId() {
            return connectionId;
        }
        
        public void setConnectionId(String connectionId) {
            this.connectionId = connectionId;
        }
        
        public String getInitiator() {
            return initiator;
        }
        
        public void setInitiator(String initiator) {
            this.initiator = initiator;
        }
        
        public String getReceiver() {
            return receiver;
        }
        
        public void setReceiver(String receiver) {
            this.receiver = receiver;
        }
        
        public ConnectionStatus getStatus() {
            return status;
        }
        
        public void setStatus(ConnectionStatus status) {
            this.status = status;
        }
        
        public long getCreatedAt() {
            return createdAt;
        }
        
        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
        
        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
} 