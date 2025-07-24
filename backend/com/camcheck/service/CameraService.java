package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CameraService {

    // Store user camera settings
    private final Map<String, Map<String, Object>> userSettings = new ConcurrentHashMap<>();
    
    @Value("${camcheck.media.resolution-scaling.min-width:160}")
    private int minWidth;
    
    @Value("${camcheck.media.resolution-scaling.min-height:120}")
    private int minHeight;
    
    @Value("${camcheck.media.resolution-scaling.max-width:1280}")
    private int maxWidth;
    
    @Value("${camcheck.media.resolution-scaling.max-height:720}")
    private int maxHeight;
    
    /**
     * Scale image based on provided options
     *
     * @param imageData Base64 encoded image data
     * @param options Scaling options
     * @return Scaled image data
     */
    public String scaleImage(String imageData, Map<String, Object> options) {
        // In a real implementation, this would use a library like OpenCV or ImageMagick
        // For now, just return the original image
        log.debug("Scaling image with options: {}", options);
        return imageData;
    }
    
    /**
     * Get camera settings for a user
     *
     * @param username User identifier
     * @return Camera settings
     */
    public Map<String, Object> getCameraSettings(String username) {
        return userSettings.getOrDefault(username, getDefaultSettings());
    }
    
    /**
     * Update camera settings for a user
     *
     * @param username User identifier
     * @param settings New settings
     * @return Updated settings
     */
    public Map<String, Object> updateCameraSettings(String username, Map<String, Object> settings) {
        // Validate settings
        validateSettings(settings);
        
        // Get existing settings or create new
        Map<String, Object> existingSettings = userSettings.computeIfAbsent(username, k -> new HashMap<>());
        
        // Update settings
        existingSettings.putAll(settings);
        
        // Store updated settings
        userSettings.put(username, existingSettings);
        
        return new HashMap<>(existingSettings);
    }
    
    /**
     * Get default camera settings
     *
     * @return Default settings
     */
    private Map<String, Object> getDefaultSettings() {
        Map<String, Object> settings = new HashMap<>();
        
        // Video settings
        Map<String, Object> video = new HashMap<>();
        video.put("width", 640);
        video.put("height", 480);
        video.put("frameRate", 30);
        video.put("facingMode", "user");
        settings.put("video", video);
        
        // Audio settings
        Map<String, Object> audio = new HashMap<>();
        audio.put("enabled", true);
        audio.put("echoCancellation", true);
        audio.put("noiseSuppression", true);
        audio.put("autoGainControl", true);
        settings.put("audio", audio);
        
        // Quality settings
        settings.put("quality", "medium");
        settings.put("compression", 0.85);
        
        return settings;
    }
    
    /**
     * Get camera service statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeUsers", userSettings.size());
        stats.put("maxResolution", String.format("%dx%d", maxWidth, maxHeight));
        stats.put("minResolution", String.format("%dx%d", minWidth, minHeight));
        return stats;
    }
    
    /**
     * Validate camera settings
     *
     * @param settings Settings to validate
     * @throws IllegalArgumentException if settings are invalid
     */
    @SuppressWarnings("unchecked")
    private void validateSettings(Map<String, Object> settings) {
        if (settings.containsKey("video")) {
            Map<String, Object> video = (Map<String, Object>) settings.get("video");
            
            // Validate resolution
            if (video.containsKey("width")) {
                int width = ((Number) video.get("width")).intValue();
                if (width < minWidth || width > maxWidth) {
                    throw new IllegalArgumentException(
                            String.format("Width must be between %d and %d", minWidth, maxWidth));
                }
            }
            
            if (video.containsKey("height")) {
                int height = ((Number) video.get("height")).intValue();
                if (height < minHeight || height > maxHeight) {
                    throw new IllegalArgumentException(
                            String.format("Height must be between %d and %d", minHeight, maxHeight));
                }
            }
            
            // Validate frame rate
            if (video.containsKey("frameRate")) {
                int frameRate = ((Number) video.get("frameRate")).intValue();
                if (frameRate < 1 || frameRate > 60) {
                    throw new IllegalArgumentException("Frame rate must be between 1 and 60");
                }
            }
        }
        
        // Validate quality
        if (settings.containsKey("quality")) {
            String quality = (String) settings.get("quality");
            if (!quality.matches("^(low|medium|high)$")) {
                throw new IllegalArgumentException("Quality must be low, medium, or high");
            }
        }
        
        // Validate compression
        if (settings.containsKey("compression")) {
            double compression = ((Number) settings.get("compression")).doubleValue();
            if (compression < 0.0 || compression > 1.0) {
                throw new IllegalArgumentException("Compression must be between 0.0 and 1.0");
            }
        }
    }
}