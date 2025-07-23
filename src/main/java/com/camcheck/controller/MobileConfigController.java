package com.camcheck.controller;

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
 * Controller for mobile-specific configuration
 */
@RestController
@RequestMapping("/api/v2/config")
@Tag(name = "Mobile Configuration", description = "API endpoints for mobile app configuration")
@Slf4j
public class MobileConfigController {

    // User-specific preferences (in a real app, this would be stored in a database)
    private static final Map<String, Map<String, Object>> userPreferences = new ConcurrentHashMap<>();
    
    @Value("${camcheck.mobile.default-quality:medium}")
    private String defaultQuality;
    
    @Value("${camcheck.mobile.background-mode-enabled:false}")
    private boolean backgroundModeEnabled;
    
    @Value("${camcheck.mobile.max-sessions:1}")
    private int maxSessions;
    
    @Value("${camcheck.mobile.push-notifications.enabled:true}")
    private boolean pushNotificationsEnabled;
    
    @Value("${camcheck.webrtc.enabled:true}")
    private boolean webRtcEnabled;
    
    @Value("${camcheck.api.latest-version:v2}")
    private String latestApiVersion;

    /**
     * Get mobile configuration
     *
     * @param authentication Authentication object
     * @return Mobile configuration
     */
    @GetMapping
    @Operation(summary = "Get mobile configuration", description = "Get mobile-specific configuration")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Configuration retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getMobileConfig(Authentication authentication) {
        String username = authentication.getName();
        log.debug("Getting mobile configuration for user: {}", username);
        
        // Get user-specific preferences or create default
        Map<String, Object> preferences = userPreferences.computeIfAbsent(username, k -> new HashMap<>());
        
        // Build configuration
        Map<String, Object> config = buildConfiguration(preferences);
        
        return ResponseEntity.ok(ApiResponse.success("Configuration retrieved", config));
    }

    /**
     * Update mobile preferences
     *
     * @param preferences User preferences
     * @param authentication Authentication object
     * @return Updated configuration
     */
    @PostMapping("/preferences")
    @Operation(summary = "Update preferences", description = "Update user's mobile preferences")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Preferences updated successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> updatePreferences(
            @RequestBody Map<String, Object> preferences,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Updating mobile preferences for user: {}", username);
        
        // Get existing preferences or create new
        Map<String, Object> existingPreferences = userPreferences.computeIfAbsent(username, k -> new HashMap<>());
        
        // Update preferences
        existingPreferences.putAll(preferences);
        
        // Save preferences
        userPreferences.put(username, existingPreferences);
        
        // Build updated configuration
        Map<String, Object> config = buildConfiguration(existingPreferences);
        
        return ResponseEntity.ok(ApiResponse.success("Preferences updated", config));
    }

    /**
     * Get quality options
     *
     * @return Quality options
     */
    @GetMapping("/quality-options")
    @Operation(summary = "Get quality options", description = "Get available video and audio quality options")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Quality options retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getQualityOptions() {
        log.debug("Getting quality options");
        
        // Define available quality options
        Map<String, Object> qualityOptions = new HashMap<>();
        
        // Video quality options
        Map<String, Object> videoQualities = new HashMap<>();
        
        // Low quality
        Map<String, Object> lowQuality = new HashMap<>();
        lowQuality.put("resolution", "320x240");
        lowQuality.put("frameRate", 15);
        lowQuality.put("bitrate", 250000);
        lowQuality.put("codec", "h264");
        
        // Medium quality
        Map<String, Object> mediumQuality = new HashMap<>();
        mediumQuality.put("resolution", "640x480");
        mediumQuality.put("frameRate", 25);
        mediumQuality.put("bitrate", 800000);
        mediumQuality.put("codec", "h264");
        
        // High quality
        Map<String, Object> highQuality = new HashMap<>();
        highQuality.put("resolution", "1280x720");
        highQuality.put("frameRate", 30);
        highQuality.put("bitrate", 1500000);
        highQuality.put("codec", "h264");
        
        videoQualities.put("low", lowQuality);
        videoQualities.put("medium", mediumQuality);
        videoQualities.put("high", highQuality);
        
        // Audio quality options
        Map<String, Object> audioQualities = new HashMap<>();
        
        // Low quality
        Map<String, Object> lowAudioQuality = new HashMap<>();
        lowAudioQuality.put("sampleRate", 8000);
        lowAudioQuality.put("channels", 1);
        lowAudioQuality.put("bitrate", 16000);
        lowAudioQuality.put("codec", "opus");
        
        // Medium quality
        Map<String, Object> mediumAudioQuality = new HashMap<>();
        mediumAudioQuality.put("sampleRate", 22050);
        mediumAudioQuality.put("channels", 1);
        mediumAudioQuality.put("bitrate", 32000);
        mediumAudioQuality.put("codec", "opus");
        
        // High quality
        Map<String, Object> highAudioQuality = new HashMap<>();
        highAudioQuality.put("sampleRate", 44100);
        highAudioQuality.put("channels", 2);
        highAudioQuality.put("bitrate", 64000);
        highAudioQuality.put("codec", "opus");
        
        audioQualities.put("low", lowAudioQuality);
        audioQualities.put("medium", mediumAudioQuality);
        audioQualities.put("high", highAudioQuality);
        
        qualityOptions.put("video", videoQualities);
        qualityOptions.put("audio", audioQualities);
        
        return ResponseEntity.ok(ApiResponse.success("Quality options retrieved", qualityOptions));
    }

    /**
     * Get app version information
     *
     * @param platform Platform (ANDROID, IOS)
     * @param currentVersion Current app version
     * @return Version information
     */
    @GetMapping("/version")
    @Operation(summary = "Get version information", description = "Get app version information and update availability")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Version information retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getVersionInfo(
            @RequestParam(defaultValue = "ANDROID") String platform,
            @RequestParam(required = false) String currentVersion) {
        
        log.debug("Getting version information for platform: {}, current version: {}", platform, currentVersion);
        
        // In a real implementation, you would check against your latest version
        // For this example, we'll hardcode some values
        String latestVersion = "1.0.0";
        boolean updateAvailable = currentVersion != null && !currentVersion.equals(latestVersion);
        boolean updateRequired = false; // Force update if critical
        String updateUrl = "https://play.google.com/store/apps/details?id=com.camcheck.app";
        
        if ("IOS".equalsIgnoreCase(platform)) {
            updateUrl = "https://apps.apple.com/app/id123456789";
        }
        
        Map<String, Object> versionInfo = new HashMap<>();
        versionInfo.put("latestVersion", latestVersion);
        versionInfo.put("updateAvailable", updateAvailable);
        versionInfo.put("updateRequired", updateRequired);
        versionInfo.put("updateUrl", updateUrl);
        versionInfo.put("releaseNotes", "Bug fixes and improvements");
        
        return ResponseEntity.ok(ApiResponse.success("Version information retrieved", versionInfo));
    }

    /**
     * Build configuration object
     *
     * @param preferences User preferences
     * @return Configuration object
     */
    private Map<String, Object> buildConfiguration(Map<String, Object> preferences) {
        Map<String, Object> config = new HashMap<>();
        
        // Basic configuration
        config.put("apiVersion", latestApiVersion);
        config.put("webRtcEnabled", webRtcEnabled);
        config.put("maxSessions", maxSessions);
        config.put("backgroundModeEnabled", backgroundModeEnabled);
        
        // User preferences with defaults
        config.put("videoQuality", preferences.getOrDefault("videoQuality", defaultQuality));
        config.put("audioQuality", preferences.getOrDefault("audioQuality", defaultQuality));
        config.put("pushNotificationsEnabled", preferences.getOrDefault("pushNotificationsEnabled", pushNotificationsEnabled));
        config.put("dataUsageLimit", preferences.getOrDefault("dataUsageLimit", false));
        config.put("automaticRecording", preferences.getOrDefault("automaticRecording", false));
        config.put("motionDetectionEnabled", preferences.getOrDefault("motionDetectionEnabled", false));
        config.put("motionDetectionSensitivity", preferences.getOrDefault("motionDetectionSensitivity", 5));
        config.put("nightModeEnabled", preferences.getOrDefault("nightModeEnabled", false));
        
        // Add all other preferences
        for (Map.Entry<String, Object> entry : preferences.entrySet()) {
            if (!config.containsKey(entry.getKey())) {
                config.put(entry.getKey(), entry.getValue());
            }
        }
        
        return config;
    }
} 