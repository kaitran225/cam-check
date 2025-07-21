package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for camera operations
 * This version only handles client camera streams, no server-side camera usage
 */
@Service
@Slf4j
public class CameraService {

    @SuppressWarnings("unused")
    private final SimpMessagingTemplate messagingTemplate;
    
    public CameraService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        log.info("Camera service initialized in client-only mode");
    }
    
    /**
     * Check if camera is streaming - always returns false as we don't use server cameras
     */
    public boolean isStreaming() {
        return false;
    }
    
    /**
     * Check if using fallback mode - always returns false as we don't use server cameras
     */
    public boolean isUsingFallback() {
        return false;
    }
    
    /**
     * Start streaming - does nothing as we don't use server cameras
     */
    public void startStreaming() {
        log.info("Server-side camera streaming is disabled, using client cameras only");
    }
    
    /**
     * Stop streaming - does nothing as we don't use server cameras
     */
    public void stopStreaming() {
        log.info("Server-side camera streaming is disabled, using client cameras only");
    }
    
    /**
     * Take a snapshot - returns null as we don't use server cameras
     */
    public String takeSnapshot() {
        log.info("Server-side camera snapshots are disabled, use client cameras only");
        return null;
    }
}