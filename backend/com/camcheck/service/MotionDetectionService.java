package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for motion detection
 * Motion detection functionality is disabled
 */
@Service
@Slf4j
public class MotionDetectionService {
    
    @Value("${camcheck.motion-detection.enabled:false}")
    private boolean motionDetectionEnabled;
    
    @SuppressWarnings("unused")
    private final SimpMessagingTemplate messagingTemplate;
    @SuppressWarnings("unused")
    private final RecordingService recordingService;
    
    public MotionDetectionService(SimpMessagingTemplate messagingTemplate, RecordingService recordingService) {
        this.messagingTemplate = messagingTemplate;
        this.recordingService = recordingService;
        log.info("Motion detection service initialized with detection disabled");
    }
    
    /**
     * Check if motion detection is enabled
     * Always returns false as motion detection is disabled
     */
    public boolean isEnabled() {
        return false;
    }
    
    /**
     * Enable motion detection - no effect as functionality is disabled
     */
    public void enable() {
        log.info("Motion detection is disabled and cannot be enabled");
    }
    
    /**
     * Disable motion detection - no effect as already disabled
     */
    public void disable() {
        log.info("Motion detection is already disabled");
    }
    
    /**
     * Motion detection function - disabled
     * @param imageData Raw image data bytes
     * @return Always false as motion detection is disabled
     */
    public boolean detectMotion(byte[] imageData) {
        return false;
    }
} 