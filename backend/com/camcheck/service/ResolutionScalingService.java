package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling dynamic resolution scaling based on network conditions
 */
@Service
@Slf4j
public class ResolutionScalingService {

    @Value("${camcheck.media.resolution-scaling.enabled:true}")
    private boolean scalingEnabled;
    
    @Value("${camcheck.media.resolution-scaling.min-width:160}")
    private int minWidth;
    
    @Value("${camcheck.media.resolution-scaling.min-height:120}")
    private int minHeight;
    
    @Value("${camcheck.media.resolution-scaling.max-width:1280}")
    private int maxWidth;
    
    @Value("${camcheck.media.resolution-scaling.max-height:720}")
    private int maxHeight;
    
    @Value("${camcheck.media.resolution-scaling.default-scale:0.8}")
    private double defaultScale;
    
    @Value("${camcheck.media.resolution-scaling.latency-threshold-high:300}")
    private int latencyThresholdHigh;
    
    @Value("${camcheck.media.resolution-scaling.latency-threshold-medium:150}")
    private int latencyThresholdMedium;
    
    @Value("${camcheck.media.resolution-scaling.latency-threshold-low:50}")
    private int latencyThresholdLow;
    
    // Store current resolution scales for each user connection
    private final Map<String, Double> userScales = new ConcurrentHashMap<>();
    
    // Store network statistics for each user connection
    private final Map<String, NetworkStats> networkStats = new ConcurrentHashMap<>();
    
    /**
     * Scale an image based on network conditions
     * @param base64Image Base64 encoded image data
     * @param connectionId Unique identifier for the connection (typically username)
     * @param latency Current network latency in milliseconds
     * @param packetLoss Current packet loss percentage (0-100)
     * @return Scaled image as Base64 string
     * @throws IOException If scaling fails
     */
    public String scaleImage(String base64Image, String connectionId, int latency, double packetLoss) throws IOException {
        if (!scalingEnabled) {
            return base64Image;
        }
        
        // Update network statistics
        updateNetworkStats(connectionId, latency, packetLoss);
        
        // Calculate appropriate scale based on network conditions
        double scale = calculateScale(connectionId);
        
        // Extract actual base64 data if it contains the data URL prefix
        String base64Data = base64Image;
        if (base64Image.contains(",")) {
            base64Data = base64Image.split(",")[1];
        }
        
        // Decode base64 to binary
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        
        // Read the image
        ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
        BufferedImage originalImage = ImageIO.read(bis);
        bis.close();
        
        if (originalImage == null) {
            throw new IOException("Could not read image data");
        }
        
        // Calculate new dimensions
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        int newWidth = Math.max(minWidth, Math.min(maxWidth, (int) (originalWidth * scale)));
        int newHeight = Math.max(minHeight, Math.min(maxHeight, (int) (originalHeight * scale)));
        
        // Skip scaling if dimensions haven't changed significantly (within 5%)
        if (Math.abs(newWidth - originalWidth) < originalWidth * 0.05 && 
            Math.abs(newHeight - originalHeight) < originalHeight * 0.05) {
            return base64Image;
        }
        
        // Scale the image
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaledImage.createGraphics();
        
        // Use high quality scaling
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();
        
        // Convert back to base64
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(scaledImage, "jpeg", bos);
        byte[] scaledBytes = bos.toByteArray();
        bos.close();
        
        String base64Scaled = Base64.getEncoder().encodeToString(scaledBytes);
        
        log.debug("Scaled image for {}: original size={}x{}, new size={}x{}, scale={}, data reduction={}%", 
                connectionId, originalWidth, originalHeight, newWidth, newHeight, scale,
                Math.round((1 - (double) scaledBytes.length / imageBytes.length) * 100));
        
        return base64Scaled;
    }
    
    /**
     * Update network statistics for a connection
     * @param connectionId Connection identifier
     * @param latency Current latency in milliseconds
     * @param packetLoss Current packet loss percentage
     */
    public void updateNetworkStats(String connectionId, int latency, double packetLoss) {
        NetworkStats stats = networkStats.computeIfAbsent(connectionId, k -> new NetworkStats());
        stats.updateLatency(latency);
        stats.updatePacketLoss(packetLoss);
        
        log.debug("Updated network stats for {}: avg latency={}ms, avg packet loss={}%", 
                connectionId, stats.getAvgLatency(), stats.getAvgPacketLoss());
    }
    
    /**
     * Calculate appropriate scale based on network conditions
     * @param connectionId Connection identifier
     * @return Scale factor (0.0-1.0)
     */
    private double calculateScale(String connectionId) {
        // Get current scale or use default if not set
        double currentScale = userScales.getOrDefault(connectionId, defaultScale);
        
        // Get network stats
        NetworkStats stats = networkStats.getOrDefault(connectionId, new NetworkStats());
        int avgLatency = stats.getAvgLatency();
        double avgPacketLoss = stats.getAvgPacketLoss();
        
        // Calculate new scale based on network conditions
        double newScale;
        
        if (avgLatency > latencyThresholdHigh || avgPacketLoss > 5.0) {
            // Poor network conditions - reduce resolution significantly
            newScale = 0.4;
        } else if (avgLatency > latencyThresholdMedium || avgPacketLoss > 2.0) {
            // Medium network conditions - reduce resolution moderately
            newScale = 0.6;
        } else if (avgLatency > latencyThresholdLow || avgPacketLoss > 0.5) {
            // Slightly degraded network - reduce resolution slightly
            newScale = 0.8;
        } else {
            // Good network conditions - use full resolution
            newScale = 1.0;
        }
        
        // Smooth the transition (don't change scale too abruptly)
        double smoothedScale = currentScale * 0.7 + newScale * 0.3;
        
        // Update the scale for this connection
        userScales.put(connectionId, smoothedScale);
        
        return smoothedScale;
    }
    
    /**
     * Get current resolution scale for a connection
     * @param connectionId Connection identifier
     * @return Current scale factor (0.0-1.0)
     */
    public double getCurrentScale(String connectionId) {
        return userScales.getOrDefault(connectionId, defaultScale);
    }
    
    /**
     * Get current network statistics for a connection
     * @param connectionId Connection identifier
     * @return Map containing network statistics
     */
    public Map<String, Object> getNetworkStats(String connectionId) {
        NetworkStats stats = networkStats.getOrDefault(connectionId, new NetworkStats());
        Map<String, Object> result = new HashMap<>();
        
        result.put("avgLatency", stats.getAvgLatency());
        result.put("avgPacketLoss", stats.getAvgPacketLoss());
        result.put("currentScale", getCurrentScale(connectionId));
        result.put("samplesCollected", stats.getSamplesCount());
        
        return result;
    }
    
    /**
     * Reset network statistics for a connection
     * @param connectionId Connection identifier
     */
    public void resetStats(String connectionId) {
        networkStats.remove(connectionId);
        userScales.remove(connectionId);
        log.debug("Reset network stats for {}", connectionId);
    }
    
    /**
     * Inner class to track network statistics with exponential moving averages
     */
    private static class NetworkStats {
        private static final double ALPHA = 0.3; // Weight for new samples in EMA
        
        private int avgLatency = 0;
        private double avgPacketLoss = 0.0;
        private int samplesCount = 0;
        
        public void updateLatency(int latency) {
            if (samplesCount == 0) {
                avgLatency = latency;
            } else {
                avgLatency = (int) (avgLatency * (1 - ALPHA) + latency * ALPHA);
            }
            samplesCount++;
        }
        
        public void updatePacketLoss(double packetLoss) {
            if (samplesCount == 0) {
                avgPacketLoss = packetLoss;
            } else {
                avgPacketLoss = avgPacketLoss * (1 - ALPHA) + packetLoss * ALPHA;
            }
        }
        
        public int getAvgLatency() {
            return avgLatency;
        }
        
        public double getAvgPacketLoss() {
            return avgPacketLoss;
        }
        
        public int getSamplesCount() {
            return samplesCount;
        }
    }
} 