package com.camcheck.controller;

import com.camcheck.controller.v1.BaseController;
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
 * Unified WebRTC controller for all clients
 */
@RestController
@RequestMapping("/api/v1/webrtc")
@Tag(name = "WebRTC", description = "WebRTC API endpoints for real-time communication")
@Slf4j
public class WebRTCController extends BaseController {

    private final WebRTCService webRTCService;
    
    @Value("${camcheck.webrtc.enabled:true}")
    private boolean webRtcEnabled;
    
    @Value("${camcheck.media.resolution-scaling.enabled:true}")
    private boolean resolutionScalingEnabled;
    
    @Value("${camcheck.media.delta-encoding.enabled:true}")
    private boolean deltaEncodingEnabled;
    
    @Autowired
    public WebRTCController(WebRTCService webRTCService) {
        this.webRTCService = webRTCService;
    }
    
    /**
     * Initialize a WebRTC connection with another user
     *
     * @param receiverId Receiver user ID
     * @param options WebRTC options
     * @param authentication Authentication object
     * @return Connection configuration
     */
    @PostMapping("/connect/{receiverId}")
    @Operation(summary = "Initialize WebRTC connection", description = "Initialize a WebRTC connection with another user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Connection initialized successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Receiver not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> initializeConnection(
            @PathVariable String receiverId,
            @RequestBody(required = false) Map<String, Object> options,
            Authentication authentication) {
        
        if (!webRtcEnabled) {
            return error("WebRTC is disabled on this server");
        }
        
        String initiatorId = authentication.getName();
        log.info("WebRTC connection request from {} to {}", initiatorId, receiverId);
        
        // Generate a unique connection ID
        String connectionId = UUID.randomUUID().toString();
        
        // Initialize options if not provided
        Map<String, Object> finalOptions = options != null ? new HashMap<>(options) : new HashMap<>();
        
        // Add default options
        finalOptions.putIfAbsent("resolutionScaling", resolutionScalingEnabled);
        finalOptions.putIfAbsent("deltaEncoding", deltaEncodingEnabled);
        
        // Initialize the connection
        Map<String, Object> config = webRTCService.initializeConnection(connectionId, initiatorId, receiverId, finalOptions);
        
        if (config.containsKey("error")) {
            log.warn("Failed to initialize WebRTC connection: {}", config.get("error"));
            return error((String) config.get("error"));
        }
        
        log.info("WebRTC connection initialized: {}", connectionId);
        
        // Return the configuration
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("connectionId", connectionId);
        responseData.put("config", config);
        
        return success("WebRTC connection initialized", responseData);
    }
    
    /**
     * Get WebRTC configuration
     *
     * @param deviceInfo Device information
     * @return WebRTC configuration
     */
    @PostMapping("/config")
    @Operation(summary = "Get WebRTC configuration", description = "Get optimized WebRTC configuration based on client status")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Configuration retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getWebRTCConfig(@RequestBody Map<String, Object> deviceInfo) {
        log.debug("Getting WebRTC configuration for client: {}", deviceInfo);
        
        Map<String, Object> iceConfig = webRTCService.getIceConfiguration();
        
        // Add configuration based on device info
        Map<String, Object> config = new HashMap<>(iceConfig);
        config.put("resolutionScaling", resolutionScalingEnabled);
        config.put("deltaEncoding", deltaEncodingEnabled);
        
        // Add device-specific optimizations
        if (deviceInfo != null) {
            String deviceType = (String) deviceInfo.getOrDefault("type", "desktop");
            String networkType = (String) deviceInfo.getOrDefault("networkType", "wifi");
            Integer batteryLevel = (Integer) deviceInfo.getOrDefault("batteryLevel", 100);
            
            // Adjust configuration based on device type and conditions
            Map<String, Object> mediaConstraints = new HashMap<>();
            mediaConstraints.put("video", getVideoConstraints(deviceType, networkType, batteryLevel));
            mediaConstraints.put("audio", getAudioConstraints(deviceType, networkType));
            
            config.put("mediaConstraints", mediaConstraints);
        }
        
        return success("WebRTC configuration retrieved", config);
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
            return error("Connection not found", 404);
        }
        
        return success("Session description sent successfully");
    }
    
    /**
     * Send ICE candidate
     *
     * @param connectionId Connection ID
     * @param candidate ICE candidate
     * @param authentication Authentication object
     * @return API response
     */
    @PostMapping("/candidate/{connectionId}")
    @Operation(summary = "Send ICE candidate", description = "Send WebRTC ICE candidate")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ICE candidate sent successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Connection not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> sendIceCandidate(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> candidate,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Sending ICE candidate for connection: {}, user: {}", connectionId, username);
        
        boolean success = webRTCService.sendIceCandidate(connectionId, username, candidate);
        
        if (!success) {
            return error("Connection not found", 404);
        }
        
        return success("ICE candidate sent successfully");
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
            return error("Connection not found", 404);
        }
        
        return success("WebRTC connection ended successfully");
    }
    
    /**
     * Get video constraints based on device type and conditions
     */
    private Map<String, Object> getVideoConstraints(String deviceType, String networkType, int batteryLevel) {
        Map<String, Object> constraints = new HashMap<>();
        
        // Base constraints
        constraints.put("enabled", true);
        
        // Resolution and framerate based on device type and network
        if ("mobile".equals(deviceType)) {
            if ("cellular".equals(networkType)) {
                constraints.put("width", 480);
                constraints.put("height", 360);
                constraints.put("frameRate", 15);
            } else {
                constraints.put("width", 640);
                constraints.put("height", 480);
                constraints.put("frameRate", 24);
            }
            
            // Reduce quality if battery is low
            if (batteryLevel < 20) {
                constraints.put("width", 320);
                constraints.put("height", 240);
                constraints.put("frameRate", 10);
            }
        } else {
            // Desktop settings
            if ("wifi".equals(networkType)) {
                constraints.put("width", 1280);
                constraints.put("height", 720);
                constraints.put("frameRate", 30);
            } else {
                constraints.put("width", 854);
                constraints.put("height", 480);
                constraints.put("frameRate", 24);
            }
        }
        
        return constraints;
    }
    
    /**
     * Get audio constraints based on device type and network
     */
    private Map<String, Object> getAudioConstraints(String deviceType, String networkType) {
        Map<String, Object> constraints = new HashMap<>();
        
        constraints.put("enabled", true);
        constraints.put("echoCancellation", true);
        constraints.put("noiseSuppression", true);
        constraints.put("autoGainControl", true);
        
        // Adjust quality based on network type
        if ("cellular".equals(networkType)) {
            constraints.put("sampleRate", 22050);
            constraints.put("channelCount", 1);
        } else {
            constraints.put("sampleRate", 44100);
            constraints.put("channelCount", "mobile".equals(deviceType) ? 1 : 2);
        }
        
        return constraints;
    }
} 