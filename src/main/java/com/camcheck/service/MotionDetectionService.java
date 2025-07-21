package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

/**
 * Service for motion detection - DISABLED PER USER REQUEST
 */
@Service
@Slf4j
public class MotionDetectionService {

    private final SimpMessagingTemplate messagingTemplate;
    
    @Value("${camcheck.motion-detection.enabled}")
    private boolean enabled;
    
    public MotionDetectionService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.enabled = false; // Always disabled
        log.info("Motion detection service initialized in disabled mode");
    }
    
    /**
     * Detect motion in the current frame - DISABLED
     * @param currentFrame The current frame to check
     * @return Always false (motion detection disabled)
     */
    public boolean detectMotion(BufferedImage currentFrame) {
        // Motion detection completely disabled per user request
        return false;
    }
    
    /**
     * Check if motion detection is enabled
     */
    public boolean isEnabled() {
        // Always return false as motion detection is disabled
        return false;
    }
    
    /**
     * Enable or disable motion detection
     * This method is kept for API compatibility but motion detection is always disabled
     */
    public void setEnabled(boolean enabled) {
        // Ignore the parameter and log the attempt
        log.debug("Attempt to {} motion detection ignored - motion detection permanently disabled", 
                 enabled ? "enable" : "disable");
        this.enabled = false;
    }
} 