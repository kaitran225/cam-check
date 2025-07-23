package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.service.WebRTCService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebRTC controller for mobile clients
 * Enhanced with mobile-specific features
 */
@RestController
@RequestMapping("/api/v2/webrtc")
@Tag(name = "WebRTC Mobile", description = "Mobile-specific WebRTC API endpoints")
@Slf4j
public class WebRTCMobileController {

    private final WebRTCService webRTCService;
    
    @Value("${camcheck.webrtc.enabled:true}")
    private boolean webRtcEnabled;
    
    @Value("${camcheck.media.mobile.max-width:480}")
    private int mobileMaxWidth;
    
    @Value("${camcheck.media.mobile.max-height:360}")
    private int mobileMaxHeight;
    
    @Value("${camcheck.media.mobile.max-frame-rate:15}")
    private int mobileMaxFrameRate;
    
    @Autowired
    public WebRTCMobileController(WebRTCService webRTCService) {
        this.webRTCService = webRTCService;
    }
    
    /**
     * Initialize a WebRTC connection with another user
     * Enhanced for mobile with quality options
     *
     * @param receiverId Receiver user ID
     * @param options WebRTC options
     * @param authentication Authentication object
     * @return Connection configuration
     */
    @PostMapping("/connect/{receiverId}")
    @Operation(summary = "Initialize WebRTC connection", description = "Initialize a WebRTC connection with another user with mobile-specific options")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Connection initialized successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Receiver not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> initializeConnection(
            @PathVariable String receiverId,
            @RequestBody Map<String, Object> options,
            Authentication authentication) {
        
        if (!webRtcEnabled) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("WebRTC is disabled on this server"));
        }
        
        String initiatorId = authentication.getName();
        log.info("Mobile WebRTC connection request from {} to {}", initiatorId, receiverId);
        
        // Generate a unique connection ID
        String connectionId = UUID.randomUUID().toString();
        
        // Apply mobile-specific constraints to options
        Map<String, Object> mobileOptions = applyMobileConstraints(options);
        
        // Initialize the connection
        Map<String, Object> config = webRTCService.initializeConnection(connectionId, initiatorId, receiverId, mobileOptions);
        
        if (config.containsKey("error")) {
            log.warn("Failed to initialize WebRTC connection: {}", config.get("error"));
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error((String) config.get("error")));
        }
        
        log.info("WebRTC connection initialized: {}", connectionId);
        
        // Return the configuration
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("connectionId", connectionId);
        responseData.put("config", config);
        
        return ResponseEntity.ok(ApiResponse.success("WebRTC connection initialized", responseData));
    }
    
    /**
     * Get WebRTC configuration for mobile clients
     *
     * @param networkType Network type (wifi, cellular, unknown)
     * @param batteryLevel Battery level percentage
     * @param deviceType Device type (android, ios)
     * @return WebRTC configuration
     */
    @GetMapping("/config")
    @Operation(summary = "Get WebRTC configuration", description = "Get optimized WebRTC configuration based on client status")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Configuration retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getWebRTCConfig(
            @RequestParam(defaultValue = "wifi") String networkType,
            @RequestParam(defaultValue = "100") Integer batteryLevel,
            @RequestParam(defaultValue = "android") String deviceType) {
        
        log.debug("Getting WebRTC configuration for mobile client: network={}, battery={}, device={}",
                networkType, batteryLevel, deviceType);
        
        Map<String, Object> iceConfig = webRTCService.getIceConfiguration();
        
        // Add mobile-specific configuration
        Map<String, Object> config = new HashMap<>(iceConfig);
        
        // Apply network type constraints
        Map<String, Object> mediaConstraints = new HashMap<>();
        
        // Video constraints
        Map<String, Object> videoConstraints = new HashMap<>();
        
        // Base video constraints
        videoConstraints.put("enabled", true);
        
        // Adjust based on network type
        if ("cellular".equalsIgnoreCase(networkType)) {
            // Lower quality for cellular
            videoConstraints.put("maxWidth", Math.min(360, mobileMaxWidth));
            videoConstraints.put("maxHeight", Math.min(240, mobileMaxHeight));
            videoConstraints.put("maxFrameRate", Math.min(10, mobileMaxFrameRate));
        } else {
            // Higher quality for WiFi
            videoConstraints.put("maxWidth", mobileMaxWidth);
            videoConstraints.put("maxHeight", mobileMaxHeight);
            videoConstraints.put("maxFrameRate", mobileMaxFrameRate);
        }
        
        // Adjust based on battery level
        if (batteryLevel < 20) {
            // Lower quality for low battery
            videoConstraints.put("maxFrameRate", Math.min(10, (int) videoConstraints.get("maxFrameRate")));
            videoConstraints.put("powerSavingMode", true);
        } else {
            videoConstraints.put("powerSavingMode", false);
        }
        
        // Audio constraints
        Map<String, Object> audioConstraints = new HashMap<>();
        audioConstraints.put("enabled", true);
        audioConstraints.put("echoCancellation", true);
        audioConstraints.put("noiseSuppression", true);
        audioConstraints.put("autoGainControl", true);
        
        // Add constraints to config
        mediaConstraints.put("video", videoConstraints);
        mediaConstraints.put("audio", audioConstraints);
        
        config.put("mediaConstraints", mediaConstraints);
        config.put("networkType", networkType);
        config.put("batteryLevel", batteryLevel);
        config.put("deviceType", deviceType);
        
        // Add ICE transport policy based on network type
        if ("cellular".equalsIgnoreCase(networkType)) {
            // Use relay servers for cellular to avoid direct connections that might not work through carrier NATs
            config.put("iceTransportPolicy", "relay");
        } else {
            // Use all candidates for WiFi
            config.put("iceTransportPolicy", "all");
        }
        
        return ResponseEntity.ok(ApiResponse.success("WebRTC configuration retrieved", config));
    }
    
    /**
     * Send ICE candidates
     *
     * @param connectionId Connection ID
     * @param candidates ICE candidates
     * @param authentication Authentication object
     * @return API response
     */
    @PostMapping("/candidates/{connectionId}")
    @Operation(summary = "Send ICE candidates", description = "Send ICE candidates for WebRTC connection")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Candidates sent successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Connection not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> sendIceCandidates(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> candidates,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Sending ICE candidates for connection: {}, user: {}", connectionId, username);
        
        boolean success = webRTCService.sendIceCandidates(connectionId, username, candidates);
        
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(ApiResponse.success("ICE candidates sent successfully"));
    }
    
    /**
     * Send session description (offer/answer)
     *
     * @param connectionId Connection ID
     * @param sdp Session description
     * @param authentication Authentication object
     * @return API response
     */
    @PostMapping("/sdp/{connectionId}")
    @Operation(summary = "Send session description", description = "Send WebRTC session description (offer/answer)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session description sent successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Connection not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> sendSessionDescription(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> sdp,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Sending session description for connection: {}, user: {}", connectionId, username);
        
        boolean success = webRTCService.sendSessionDescription(connectionId, username, sdp);
        
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(ApiResponse.success("Session description sent successfully"));
    }
    
    /**
     * Get connection status
     *
     * @param connectionId Connection ID
     * @param authentication Authentication object
     * @return Connection status
     */
    @GetMapping("/status/{connectionId}")
    @Operation(summary = "Get connection status", description = "Get WebRTC connection status")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status retrieved successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Connection not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getConnectionStatus(
            @PathVariable String connectionId,
            Authentication authentication) {
        
        WebRTCService.ConnectionStatus status = webRTCService.getConnectionStatus(connectionId);
        
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> statusData = new HashMap<>();
        statusData.put("connectionId", connectionId);
        statusData.put("status", status.name());
        statusData.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(ApiResponse.success("Connection status retrieved", statusData));
    }
    
    /**
     * End WebRTC connection
     *
     * @param connectionId Connection ID
     * @param authentication Authentication object
     * @return API response
     */
    @DeleteMapping("/{connectionId}")
    @Operation(summary = "End WebRTC connection", description = "End an active WebRTC connection")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Connection ended successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Connection not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> endConnection(
            @PathVariable String connectionId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Ending WebRTC connection: {}, user: {}", connectionId, username);
        
        boolean success = webRTCService.endConnection(connectionId, username);
        
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(ApiResponse.success("WebRTC connection ended successfully"));
    }
    
    /**
     * Apply mobile constraints to WebRTC options
     *
     * @param options WebRTC options
     * @return Mobile-optimized WebRTC options
     */
    private Map<String, Object> applyMobileConstraints(Map<String, Object> options) {
        Map<String, Object> mobileOptions = new HashMap<>(options);
        
        // Get network type from options or default to WiFi
        String networkType = (String) options.getOrDefault("networkType", "wifi");
        
        // Apply video constraints based on network type
        Map<String, Object> videoConstraints = new HashMap<>();
        
        if ("cellular".equalsIgnoreCase(networkType)) {
            // Lower quality for cellular
            videoConstraints.put("maxWidth", Math.min(360, mobileMaxWidth));
            videoConstraints.put("maxHeight", Math.min(240, mobileMaxHeight));
            videoConstraints.put("maxFrameRate", Math.min(10, mobileMaxFrameRate));
        } else {
            // Higher quality for WiFi
            videoConstraints.put("maxWidth", mobileMaxWidth);
            videoConstraints.put("maxHeight", mobileMaxHeight);
            videoConstraints.put("maxFrameRate", mobileMaxFrameRate);
        }
        
        // Get quality preference from options
        String quality = (String) options.getOrDefault("quality", "medium");
        
        // Adjust constraints based on quality preference
        switch (quality.toLowerCase()) {
            case "low":
                videoConstraints.put("maxWidth", Math.min(320, (int) videoConstraints.get("maxWidth")));
                videoConstraints.put("maxHeight", Math.min(240, (int) videoConstraints.get("maxHeight")));
                videoConstraints.put("maxFrameRate", Math.min(15, (int) videoConstraints.get("maxFrameRate")));
                break;
            case "high":
                // Use maximum allowed values, already set above
                break;
            case "medium":
            default:
                // Adjust to medium quality
                videoConstraints.put("maxWidth", Math.min(640, (int) videoConstraints.get("maxWidth")));
                videoConstraints.put("maxHeight", Math.min(480, (int) videoConstraints.get("maxHeight")));
                videoConstraints.put("maxFrameRate", Math.min(20, (int) videoConstraints.get("maxFrameRate")));
                break;
        }
        
        mobileOptions.put("videoConstraints", videoConstraints);
        
        // Audio constraints
        Map<String, Object> audioConstraints = new HashMap<>();
        audioConstraints.put("echoCancellation", options.getOrDefault("echoCancellation", true));
        audioConstraints.put("noiseSuppression", options.getOrDefault("noiseSuppression", true));
        audioConstraints.put("autoGainControl", options.getOrDefault("autoGainControl", true));
        
        mobileOptions.put("audioConstraints", audioConstraints);
        
        // Set ICE transport policy based on network type
        if ("cellular".equalsIgnoreCase(networkType)) {
            mobileOptions.put("iceTransportPolicy", "relay");
        } else {
            mobileOptions.put("iceTransportPolicy", "all");
        }
        
        return mobileOptions;
    }
} 