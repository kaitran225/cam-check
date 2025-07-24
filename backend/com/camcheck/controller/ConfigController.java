package com.camcheck.controller;

import com.camcheck.controller.v1.BaseController;
import com.camcheck.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified configuration controller for all clients
 */
@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Configuration", description = "API endpoints for client configuration")
@Slf4j
public class ConfigController extends BaseController {

    // User-specific preferences (in a real app, this would be stored in a database)
    private static final Map<String, Map<String, Object>> userPreferences = new ConcurrentHashMap<>();
    
    @Value("${camcheck.default-quality:medium}")
    private String defaultQuality;
    
    @Value("${camcheck.background-mode-enabled:false}")
    private boolean backgroundModeEnabled;
    
    @Value("${camcheck.webrtc.enabled:true}")
    private boolean webRtcEnabled;
    
    @Value("${camcheck.media.resolution-scaling.enabled:true}")
    private boolean resolutionScalingEnabled;
    
    @Value("${camcheck.media.delta-encoding.enabled:true}")
    private boolean deltaEncodingEnabled;
    
    /**
     * Get client configuration
     *
     * @param deviceInfo Device information (optional)
     * @param authentication Authentication object
     * @return Client configuration
     */
    @PostMapping
    @Operation(summary = "Get client configuration", description = "Get client-specific configuration based on device info")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Configuration retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getConfig(
            @RequestBody(required = false) Map<String, Object> deviceInfo,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Getting configuration for user: {} with device info: {}", username, deviceInfo);
        
        // Get user-specific preferences or create default
        Map<String, Object> preferences = userPreferences.computeIfAbsent(username, k -> new HashMap<>());
        
        // Build configuration
        Map<String, Object> config = buildConfiguration(preferences, deviceInfo);
        
        return success("Configuration retrieved", config);
    }

    /**
     * Update client preferences
     *
     * @param preferences User preferences
     * @param authentication Authentication object
     * @return Updated configuration
     */
    @PostMapping("/preferences")
    @Operation(summary = "Update preferences", description = "Update user's client preferences")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Preferences updated successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> updatePreferences(
            @RequestBody Map<String, Object> preferences,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Updating preferences for user: {}", username);
        
        // Get existing preferences or create new
        Map<String, Object> existingPreferences = userPreferences.computeIfAbsent(username, k -> new HashMap<>());
        
        // Update preferences
        existingPreferences.putAll(preferences);
        
        // Save preferences
        userPreferences.put(username, existingPreferences);
        
        // Build updated configuration
        Map<String, Object> config = buildConfiguration(existingPreferences, null);
        
        return success("Preferences updated", config);
    }

    /**
     * Get quality options
     *
     * @return Quality options
     */
    @GetMapping("/quality-options")
    @Operation(summary = "Get quality options", description = "Get available quality options for streaming")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Quality options retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getQualityOptions() {
        Map<String, Object> options = new HashMap<>();
        
        // Video quality options
        Map<String, Object> videoQualities = new HashMap<>();
        
        // Low quality
        Map<String, Object> lowQuality = new HashMap<>();
        lowQuality.put("width", 480);
        lowQuality.put("height", 360);
        lowQuality.put("frameRate", 15);
        lowQuality.put("bitrate", 500000);
        videoQualities.put("low", lowQuality);
        
        // Medium quality
        Map<String, Object> mediumQuality = new HashMap<>();
        mediumQuality.put("width", 854);
        mediumQuality.put("height", 480);
        mediumQuality.put("frameRate", 24);
        mediumQuality.put("bitrate", 1000000);
        videoQualities.put("medium", mediumQuality);
        
        // High quality
        Map<String, Object> highQuality = new HashMap<>();
        highQuality.put("width", 1280);
        highQuality.put("height", 720);
        highQuality.put("frameRate", 30);
        highQuality.put("bitrate", 2000000);
        videoQualities.put("high", highQuality);
        
        options.put("video", videoQualities);
        
        // Audio quality options
        Map<String, Object> audioQualities = new HashMap<>();
        
        // Low quality audio
        Map<String, Object> lowAudioQuality = new HashMap<>();
        lowAudioQuality.put("sampleRate", 22050);
        lowAudioQuality.put("channels", 1);
        lowAudioQuality.put("bitrate", 32000);
        audioQualities.put("low", lowAudioQuality);
        
        // Medium quality audio
        Map<String, Object> mediumAudioQuality = new HashMap<>();
        mediumAudioQuality.put("sampleRate", 44100);
        mediumAudioQuality.put("channels", 1);
        mediumAudioQuality.put("bitrate", 64000);
        audioQualities.put("medium", mediumAudioQuality);
        
        // High quality audio
        Map<String, Object> highAudioQuality = new HashMap<>();
        highAudioQuality.put("sampleRate", 44100);
        highAudioQuality.put("channels", 2);
        highAudioQuality.put("bitrate", 128000);
        audioQualities.put("high", highAudioQuality);
        
        options.put("audio", audioQualities);
        
        return success("Quality options retrieved", options);
    }

    /**
     * Build client configuration
     *
     * @param preferences User preferences
     * @param deviceInfo Device information
     * @return Client configuration
     */
    private Map<String, Object> buildConfiguration(Map<String, Object> preferences, Map<String, Object> deviceInfo) {
        Map<String, Object> config = new HashMap<>();
        
        // Add feature flags
        Map<String, Object> features = new HashMap<>();
        features.put("webrtc", webRtcEnabled);
        features.put("resolutionScaling", resolutionScalingEnabled);
        features.put("deltaEncoding", deltaEncodingEnabled);
        features.put("backgroundMode", backgroundModeEnabled);
        config.put("features", features);
        
        // Add quality settings
        String quality = (String) preferences.getOrDefault("quality", defaultQuality);
        config.put("quality", quality);
        
        // Add device-specific optimizations if device info is provided
        if (deviceInfo != null) {
            String deviceType = (String) deviceInfo.getOrDefault("type", "desktop");
            String networkType = (String) deviceInfo.getOrDefault("networkType", "wifi");
            Integer batteryLevel = (Integer) deviceInfo.getOrDefault("batteryLevel", 100);
            
            // Adjust configuration based on device type and conditions
            Map<String, Object> optimizations = new HashMap<>();
            
            // Network optimizations
            if ("cellular".equals(networkType)) {
                optimizations.put("maxBitrate", 800000);
                optimizations.put("maxResolution", "480p");
                optimizations.put("maxFrameRate", 24);
            } else {
                optimizations.put("maxBitrate", 2000000);
                optimizations.put("maxResolution", "720p");
                optimizations.put("maxFrameRate", 30);
            }
            
            // Device type optimizations
            if ("mobile".equals(deviceType)) {
                optimizations.put("powerSaving", batteryLevel < 20);
                optimizations.put("preferHardwareCodec", true);
                optimizations.put("audioChannels", 1);
            } else {
                optimizations.put("powerSaving", false);
                optimizations.put("preferHardwareCodec", false);
                optimizations.put("audioChannels", 2);
            }
            
            config.put("optimizations", optimizations);
        }
        
        // Add user preferences
        config.put("preferences", preferences);
        
        return config;
    }
} 