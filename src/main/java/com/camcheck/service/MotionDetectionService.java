package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for motion detection - DISABLED PER USER REQUEST
 * Simplified version with no server-side camera functionality
 */
@Service
@Slf4j
public class MotionDetectionService {

    public MotionDetectionService() {
        log.info("Motion detection service initialized in disabled mode");
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
    }
} 