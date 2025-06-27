package com.camcheck.service;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for camera operations
 */
@Service
@Slf4j
public class CameraService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MotionDetectionService motionDetectionService;
    private final RecordingService recordingService;
    
    @Value("${camcheck.camera.device-index}")
    private int deviceIndex;
    
    @Value("${camcheck.camera.width}")
    private int width;
    
    @Value("${camcheck.camera.height}")
    private int height;
    
    @Value("${camcheck.camera.frame-rate}")
    private int frameRate;
    
    private Webcam webcam;
    private ScheduledExecutorService executor;
    private boolean isStreaming = false;
    
    public CameraService(SimpMessagingTemplate messagingTemplate, 
                        MotionDetectionService motionDetectionService,
                        RecordingService recordingService) {
        this.messagingTemplate = messagingTemplate;
        this.motionDetectionService = motionDetectionService;
        this.recordingService = recordingService;
    }
    
    @PostConstruct
    public void init() {
        try {
            log.info("Initializing camera service");
            
            // Get the webcam
            webcam = Webcam.getWebcams().get(deviceIndex);
            
            // Set resolution
            Dimension resolution = new Dimension(width, height);
            webcam.setViewSize(resolution);
            
            // Open the webcam
            webcam.open();
            
            log.info("Camera initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize camera: {}", e.getMessage(), e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        stopStreaming();
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
            log.info("Camera closed");
        }
    }
    
    /**
     * Start streaming camera footage
     */
    public void startStreaming() {
        if (isStreaming) {
            return;
        }
        
        log.info("Starting camera stream");
        isStreaming = true;
        
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                if (webcam != null && webcam.isOpen()) {
                    // Capture frame
                    var image = webcam.getImage();
                    
                    // Check for motion
                    if (motionDetectionService.isEnabled()) {
                        motionDetectionService.detectMotion(image);
                    }
                    
                    // Record if needed
                    if (recordingService.isRecording()) {
                        recordingService.addFrame(image);
                    }
                    
                    // Convert to base64 for streaming
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "jpg", baos);
                    byte[] imageBytes = baos.toByteArray();
                    
                    // Send to all connected clients
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                    messagingTemplate.convertAndSend("/topic/camera", base64Image);
                }
            } catch (IOException e) {
                log.error("Error streaming camera frame: {}", e.getMessage(), e);
            }
        }, 0, 1000 / frameRate, TimeUnit.MILLISECONDS);
        
        log.info("Camera streaming started at {} fps", frameRate);
    }
    
    /**
     * Stop streaming camera footage
     */
    public void stopStreaming() {
        if (!isStreaming) {
            return;
        }
        
        log.info("Stopping camera stream");
        isStreaming = false;
        
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("Camera streaming stopped");
    }
    
    /**
     * Take a snapshot
     * @return Base64 encoded image
     */
    public String takeSnapshot() {
        try {
            if (webcam != null && webcam.isOpen()) {
                var image = webcam.getImage();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);
                return Base64.getEncoder().encodeToString(baos.toByteArray());
            }
        } catch (IOException e) {
            log.error("Error taking snapshot: {}", e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Check if camera is streaming
     */
    public boolean isStreaming() {
        return isStreaming;
    }
} 