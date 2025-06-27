package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Service for motion detection
 */
@Service
@Slf4j
public class MotionDetectionService {

    private final SimpMessagingTemplate messagingTemplate;
    
    @Value("${camcheck.motion-detection.enabled}")
    private boolean enabled;
    
    @Value("${camcheck.motion-detection.sensitivity}")
    private int sensitivity;
    
    @Value("${camcheck.motion-detection.cooldown-period}")
    private int cooldownPeriod;
    
    private BufferedImage previousFrame;
    private LocalDateTime lastMotionDetected;
    
    public MotionDetectionService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Detect motion in the current frame
     * @param currentFrame The current frame to check
     * @return True if motion is detected
     */
    public boolean detectMotion(BufferedImage currentFrame) {
        if (!enabled || currentFrame == null) {
            return false;
        }
        
        // First frame, just store it
        if (previousFrame == null) {
            previousFrame = deepCopy(currentFrame);
            return false;
        }
        
        // Check if we're still in cooldown period
        if (lastMotionDetected != null && 
            ChronoUnit.SECONDS.between(lastMotionDetected, LocalDateTime.now()) < cooldownPeriod) {
            return false;
        }
        
        // Calculate difference between frames
        int width = currentFrame.getWidth();
        int height = currentFrame.getHeight();
        
        // Sample points (for performance)
        int sampleSize = 10;
        int diffCount = 0;
        int threshold = (100 - sensitivity) * 3; // Lower sensitivity = higher threshold
        
        for (int y = 0; y < height; y += sampleSize) {
            for (int x = 0; x < width; x += sampleSize) {
                Color c1 = new Color(currentFrame.getRGB(x, y));
                Color c2 = new Color(previousFrame.getRGB(x, y));
                
                int diff = Math.abs(c1.getRed() - c2.getRed()) + 
                           Math.abs(c1.getGreen() - c2.getGreen()) + 
                           Math.abs(c1.getBlue() - c2.getBlue());
                
                if (diff > threshold) {
                    diffCount++;
                }
            }
        }
        
        // Update previous frame
        previousFrame = deepCopy(currentFrame);
        
        // Calculate percentage of changed pixels
        int totalSamples = (width / sampleSize) * (height / sampleSize);
        double changePercent = (double) diffCount / totalSamples * 100;
        
        // Detect motion if change percentage is significant
        if (changePercent > 5.0) {
            motionDetected();
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle motion detection event
     */
    private void motionDetected() {
        lastMotionDetected = LocalDateTime.now();
        log.info("Motion detected at {}", lastMotionDetected);
        
        // Notify clients
        messagingTemplate.convertAndSend("/topic/motion", 
                                         Map.of("time", lastMotionDetected.toString()));
    }
    
    /**
     * Create a deep copy of a BufferedImage
     */
    private BufferedImage deepCopy(BufferedImage bi) {
        BufferedImage copy = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics g = copy.createGraphics();
        g.drawImage(bi, 0, 0, null);
        g.dispose();
        return copy;
    }
    
    /**
     * Check if motion detection is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Enable or disable motion detection
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("Motion detection {}", enabled ? "enabled" : "disabled");
    }
} 