package com.camcheck.service;

import com.github.sarxos.webcam.Webcam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for camera operations
 */
@Service
@Slf4j
public class CameraService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MotionDetectionService motionDetectionService;
    
    @Value("${camcheck.camera.device-index}")
    private int deviceIndex;
    
    @Value("${camcheck.camera.width}")
    private int width;
    
    @Value("${camcheck.camera.height}")
    private int height;
    
    @Value("${camcheck.camera.frame-rate}")
    private int frameRate;
    
    @Value("${camcheck.camera.force-fallback:false}")
    private boolean forceFallback;
    
    private Webcam webcam;
    private ScheduledExecutorService executor;
    private boolean isStreaming = false;
    private boolean useFallbackMode = false;
    private BufferedImage fallbackImage;
    private long frameCount = 0;
    private List<Map<String, Object>> availableCameras = new ArrayList<>();
    
    public CameraService(SimpMessagingTemplate messagingTemplate, 
                        MotionDetectionService motionDetectionService) {
        this.messagingTemplate = messagingTemplate;
        this.motionDetectionService = motionDetectionService;
    }
    
    @PostConstruct
    public void init() {
        try {
            log.info("Initializing camera service");
            
            // Detect available cameras
            detectCameras();
            
            // Check if fallback mode is forced
            if (forceFallback) {
                log.info("Fallback mode is forced by configuration");
                initFallbackMode();
                return;
            }
            
            // Try to get available webcams
            List<Webcam> webcams = Webcam.getWebcams();
            
            if (webcams.isEmpty()) {
                log.warn("No webcams detected. Using fallback mode.");
                initFallbackMode();
                return;
            }
            
            // Check if requested device index is valid
            if (deviceIndex >= webcams.size()) {
                log.warn("Device index {} is out of range (max: {}). Using first available camera.", 
                         deviceIndex, webcams.size() - 1);
                deviceIndex = 0;
            }
            
            // Get the webcam
            webcam = webcams.get(deviceIndex);
            log.info("Attempting to initialize camera: {}", webcam.getName());
            
            // Set resolution
            Dimension[] supportedSizes = webcam.getViewSizes();
            Dimension resolution = new Dimension(width, height);
            
            // Find the closest supported resolution
            boolean foundExactMatch = false;
            for (Dimension size : supportedSizes) {
                if (size.width == width && size.height == height) {
                    foundExactMatch = true;
                    break;
                }
            }
            
            if (!foundExactMatch) {
                log.warn("Requested resolution {}x{} not supported by camera. Available resolutions:", width, height);
                for (Dimension size : supportedSizes) {
                    log.warn("  - {}x{}", size.width, size.height);
                }
                
                // Use the first available resolution
                if (supportedSizes.length > 0) {
                    resolution = supportedSizes[0];
                    log.info("Using resolution: {}x{}", resolution.width, resolution.height);
                }
            }
            
            webcam.setViewSize(resolution);
            
            // Try to open the webcam with retries
            boolean opened = false;
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    log.info("Attempt {} to open camera", attempt);
                    webcam.open(true); // true = with timeout
                    opened = true;
                    log.info("Camera initialized successfully: {}", webcam.getName());
                    break;
                } catch (Exception e) {
                    log.warn("Attempt {} failed to open camera: {}", attempt, e.getMessage());
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(1000); // Wait before retry
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
            
            if (!opened) {
                log.error("Failed to open camera after {} attempts. Using fallback mode.", maxRetries);
                initFallbackMode();
            }
        } catch (Exception e) {
            log.error("Failed to initialize camera: {}. Using fallback mode.", e.getMessage(), e);
            initFallbackMode();
        }
        
        // Broadcast initial status
        broadcastStatus();
    }
    
    /**
     * Detect available cameras on the system
     */
    public List<Map<String, Object>> detectCameras() {
        log.info("Detecting available cameras...");
        availableCameras.clear();
        
        try {
            // Get all webcams
            List<Webcam> webcams = Webcam.getWebcams();
            
            if (webcams.isEmpty()) {
                log.warn("No cameras detected on the system");
                return availableCameras;
            }
            
            // Log detected cameras
            log.info("Detected {} camera(s)", webcams.size());
            
            // Process each camera
            for (int i = 0; i < webcams.size(); i++) {
                Webcam cam = webcams.get(i);
                Map<String, Object> cameraInfo = new HashMap<>();
                
                cameraInfo.put("index", i);
                cameraInfo.put("name", cam.getName());
                cameraInfo.put("id", cam.getName()); // Use name as ID
                
                // Get supported resolutions
                List<Map<String, Object>> resolutions = new ArrayList<>();
                for (Dimension dim : cam.getViewSizes()) {
                    Map<String, Object> resolution = new HashMap<>();
                    resolution.put("width", dim.width);
                    resolution.put("height", dim.height);
                    resolutions.add(resolution);
                }
                cameraInfo.put("resolutions", resolutions);
                
                // Add to available cameras list
                availableCameras.add(cameraInfo);
                
                log.info("Camera {}: {}", i, cam.getName());
            }
        } catch (Exception e) {
            log.error("Error detecting cameras: {}", e.getMessage(), e);
        }
        
        return availableCameras;
    }
    
    /**
     * Get list of available cameras
     */
    public List<Map<String, Object>> getAvailableCameras() {
        if (availableCameras.isEmpty()) {
            detectCameras();
        }
        return availableCameras;
    }
    
    /**
     * Switch to a different camera
     * @param newDeviceIndex The index of the camera to switch to
     * @return True if successful, false otherwise
     */
    public boolean switchCamera(int newDeviceIndex) {
        if (isStreaming) {
            stopStreaming();
        }
        
        // If fallback is forced, we can't switch cameras
        if (forceFallback) {
            log.warn("Cannot switch camera: fallback mode is forced by configuration");
            return false;
        }
        
        List<Webcam> webcams = Webcam.getWebcams();
        
        // Check if the requested camera exists
        if (webcams.isEmpty() || newDeviceIndex >= webcams.size()) {
            log.error("Camera index {} is out of range or no cameras available", newDeviceIndex);
            return false;
        }
        
        try {
            log.info("Switching to camera {}: {}", newDeviceIndex, webcams.get(newDeviceIndex).getName());
            
            // Close current webcam if open
            if (webcam != null && webcam.isOpen()) {
                log.info("Closing current camera: {}", webcam.getName());
                webcam.close();
            }
            
            // Switch to new webcam
            webcam = webcams.get(newDeviceIndex);
            deviceIndex = newDeviceIndex;
            
            // Set resolution
            Dimension[] supportedSizes = webcam.getViewSizes();
            Dimension resolution = new Dimension(width, height);
            
            // Find the closest supported resolution
            boolean foundExactMatch = false;
            for (Dimension size : supportedSizes) {
                if (size.width == width && size.height == height) {
                    foundExactMatch = true;
                    break;
                }
            }
            
            if (!foundExactMatch && supportedSizes.length > 0) {
                resolution = supportedSizes[0];
                log.info("Using resolution: {}x{}", resolution.width, resolution.height);
            }
            
            webcam.setViewSize(resolution);
            
            // Try to open the webcam with retries
            boolean opened = false;
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    log.info("Attempt {} to open camera {}", attempt, webcam.getName());
                    webcam.open(true); // true = with timeout
                    opened = true;
                    log.info("Camera switched successfully: {}", webcam.getName());
                    break;
                } catch (Exception e) {
                    log.warn("Attempt {} failed to open camera: {}", attempt, e.getMessage());
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(1000); // Wait before retry
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
            
            if (!opened) {
                log.error("Failed to open camera {} after {} attempts.", webcam.getName(), maxRetries);
                return false;
            }
            
            // Exit fallback mode if we were in it
            useFallbackMode = false;
            
            // Broadcast status update
            broadcastStatus();
            
            return true;
        } catch (Exception e) {
            log.error("Failed to switch camera: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Initialize fallback mode with a test pattern image
     */
    private void initFallbackMode() {
        useFallbackMode = true;
        fallbackImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = fallbackImage.createGraphics();
        
        // Fill background
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, width, height);
        
        // Draw test pattern
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("CamCheck - No Camera", width/2 - 120, height/2);
        g.drawString("Fallback Mode", width/2 - 80, height/2 + 40);
        
        // Draw border
        g.setColor(Color.RED);
        g.drawRect(5, 5, width - 10, height - 10);
        
        g.dispose();
        
        log.info("Fallback mode initialized");
    }
    
    @PreDestroy
    public void shutdown() {
        stopStreaming();
        if (!useFallbackMode && webcam != null && webcam.isOpen()) {
            webcam.close();
            log.info("Camera closed");
        }
    }
    
    /**
     * Start streaming camera footage
     */
    public synchronized void startStreaming() {
        if (isStreaming) {
            log.debug("Camera is already streaming, ignoring start request");
            return;
        }
        
        log.info("Starting camera stream");
        
        // If we're not in fallback mode but the webcam is not open, try to open it
        if (!useFallbackMode && (webcam == null || !webcam.isOpen())) {
            log.info("Camera not open, attempting to initialize");
            
            try {
                List<Webcam> webcams = Webcam.getWebcams();
                if (webcams.isEmpty()) {
                    log.warn("No webcams detected. Using fallback mode.");
                    initFallbackMode();
                } else {
                    if (deviceIndex >= webcams.size()) {
                        deviceIndex = 0;
                    }
                    
                    // If there's an existing webcam, try to close it first
                    if (webcam != null) {
                        try {
                            if (webcam.isOpen()) {
                                webcam.close();
                                log.debug("Closed existing webcam before reopening");
                            }
                        } catch (Exception e) {
                            log.warn("Error closing existing webcam: {}", e.getMessage());
                            // Continue anyway
                        }
                    }
                    
                    webcam = webcams.get(deviceIndex);
                    Dimension resolution = new Dimension(width, height);
                    webcam.setViewSize(resolution);
                    
                    try {
                        webcam.open(true); // true = with timeout
                        log.info("Camera initialized successfully: {}", webcam.getName());
                    } catch (Exception e) {
                        log.error("Failed to open camera: {}. Using fallback mode.", e.getMessage());
                        initFallbackMode();
                    }
                }
            } catch (Exception e) {
                log.error("Failed to initialize camera: {}. Using fallback mode.", e.getMessage(), e);
                initFallbackMode();
            }
        }
        
        // Make sure any existing executor is shut down
        if (executor != null && !executor.isShutdown()) {
            try {
                executor.shutdown();
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Error shutting down existing executor: {}", e.getMessage());
            }
        }
        
        isStreaming = true;
        
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                BufferedImage image;
                
                if (useFallbackMode) {
                    // Use fallback image and modify it slightly to simulate movement
                    image = getModifiedFallbackImage();
                } else if (webcam != null && webcam.isOpen()) {
                    // Capture frame from real camera
                    image = webcam.getImage();
                    if (image == null) {
                        // If camera fails during streaming, switch to fallback
                        log.warn("Camera returned null image. Switching to fallback mode.");
                        initFallbackMode();
                        image = fallbackImage;
                    }
                } else {
                    // Should not happen, but just in case
                    log.warn("Webcam not available or not open. Switching to fallback mode.");
                    initFallbackMode();
                    image = fallbackImage;
                }
                
                // Motion detection disabled per user request
                // No motion detection processing here
                
                // Convert to base64 for streaming
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);
                byte[] imageBytes = baos.toByteArray();
                
                // Send to all connected clients
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                messagingTemplate.convertAndSend("/topic/camera", base64Image);
                
            } catch (IOException e) {
                log.error("Error streaming camera frame: {}", e.getMessage(), e);
            }
        }, 0, 1000 / frameRate, TimeUnit.MILLISECONDS);
        
        log.info("Camera streaming started at {} fps", frameRate);
        
        // Broadcast updated status
        broadcastStatus();
    }
    
    /**
     * Get a modified version of the fallback image to simulate movement
     */
    private BufferedImage getModifiedFallbackImage() {
        // Create a copy of the fallback image
        BufferedImage modifiedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = modifiedImage.createGraphics();
        g.drawImage(fallbackImage, 0, 0, null);
        
        // Add a timestamp and frame counter to simulate changing content
        frameCount++;
        g.setColor(Color.GREEN);
        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.drawString("Frame: " + frameCount, 20, height - 50);
        g.drawString("Time: " + System.currentTimeMillis(), 20, height - 30);
        
        // Add a moving element
        int position = (int)(frameCount % width);
        g.setColor(Color.YELLOW);
        g.fillOval(position, 50, 20, 20);
        
        g.dispose();
        return modifiedImage;
    }
    
    /**
     * Stop streaming camera footage
     */
    public synchronized void stopStreaming() {
        if (!isStreaming) {
            log.debug("Camera is not streaming, ignoring stop request");
            return;
        }
        
        log.info("Stopping camera stream");
        
        // Set flag first to prevent any new frames from being sent
        isStreaming = false;
        
        // Shut down the executor
        if (executor != null) {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("Interrupted while stopping camera stream");
            } catch (Exception e) {
                log.error("Error shutting down camera stream: {}", e.getMessage(), e);
                executor.shutdownNow();
            } finally {
                executor = null;
            }
        }
        
        log.info("Camera streaming stopped");
        
        // Broadcast updated status
        broadcastStatus();
    }
    
    /**
     * Take a snapshot
     * @return Base64 encoded image
     */
    public String takeSnapshot() {
        try {
            BufferedImage image;
            
            if (useFallbackMode) {
                image = getModifiedFallbackImage();
            } else if (webcam != null && webcam.isOpen()) {
                image = webcam.getImage();
                if (image == null) {
                    initFallbackMode();
                    image = fallbackImage;
                }
            } else {
                initFallbackMode();
                image = fallbackImage;
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.error("Error taking snapshot: {}", e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Check if camera is streaming
     * This is a read-only method that should not affect the camera stream
     */
    public synchronized boolean isStreaming() {
        return isStreaming;
    }
    
    /**
     * Check if using fallback mode
     * This is a read-only method that should not affect the camera stream
     */
    public synchronized boolean isUsingFallback() {
        return useFallbackMode;
    }
    
    /**
     * Set fallback mode
     * @param enabled True to enable fallback mode, false to disable
     * @return True if the operation was successful
     */
    public boolean setFallbackMode(boolean enabled) {
        if (enabled == useFallbackMode) {
            return true; // Already in the requested state
        }
        
        if (enabled) {
            // Switch to fallback mode
            if (isStreaming) {
                stopStreaming();
            }
            
            // Close webcam if open
            if (webcam != null && webcam.isOpen()) {
                try {
                    webcam.close();
                } catch (Exception e) {
                    log.error("Error closing webcam: {}", e.getMessage(), e);
                }
            }
            
            initFallbackMode();
            log.info("Switched to fallback mode");
            return true;
        } else {
            // Exit fallback mode and try to initialize a real camera
            if (isStreaming) {
                stopStreaming();
            }
            
            try {
                List<Webcam> webcams = Webcam.getWebcams();
                if (webcams.isEmpty()) {
                    log.warn("Cannot exit fallback mode: no cameras available");
                    return false;
                }
                
                // Check if requested device index is valid
                if (deviceIndex >= webcams.size()) {
                    deviceIndex = 0;
                }
                
                // Get the webcam
                webcam = webcams.get(deviceIndex);
                
                // Set resolution
                Dimension[] supportedSizes = webcam.getViewSizes();
                Dimension resolution = new Dimension(width, height);
                
                if (supportedSizes.length > 0) {
                    boolean foundMatch = false;
                    for (Dimension size : supportedSizes) {
                        if (size.width == width && size.height == height) {
                            foundMatch = true;
                            break;
                        }
                    }
                    
                    if (!foundMatch) {
                        resolution = supportedSizes[0];
                    }
                }
                
                webcam.setViewSize(resolution);
                
                // Try to open the webcam
                webcam.open(true);
                useFallbackMode = false;
                log.info("Exited fallback mode, camera initialized: {}", webcam.getName());
                
                // Broadcast status update
                broadcastStatus();
                return true;
            } catch (Exception e) {
                log.error("Failed to exit fallback mode: {}", e.getMessage(), e);
                initFallbackMode();
                return false;
            }
        }
    }
    
    /**
     * Broadcast camera status to all connected clients
     */
    public void broadcastStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("streaming", isStreaming);
            status.put("fallbackMode", useFallbackMode);
            status.put("motionDetection", motionDetectionService.isEnabled());
            
            messagingTemplate.convertAndSend("/topic/status", status);
            log.debug("Camera status broadcast: {}", status);
        } catch (Exception e) {
            log.error("Error broadcasting camera status: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Periodic health check to ensure camera status is accurate
     * Runs every 30 seconds
     */
    @Scheduled(fixedRate = 30000)
    public void healthCheck() {
        log.debug("Performing camera health check");
        
        try {
            // If we're streaming but webcam is not available/open, verify status
            if (isStreaming && !useFallbackMode && (webcam == null || !webcam.isOpen())) {
                log.warn("Health check detected inconsistent state: isStreaming=true but webcam is not available/open");
                
                // Don't try to reopen the webcam as it may disrupt the stream
                // Just update the status flag to match reality
                if (webcam == null || !webcam.isOpen()) {
                    log.info("Camera appears to be disconnected but streaming flag is true. Updating status only.");
                    
                    // Instead of broadcasting status, which might cause UI disruption,
                    // just log the issue and let the client handle it
                    log.warn("Camera health check detected inconsistency - not broadcasting to avoid disruption");
                }
            }
            
            // Don't attempt to reinitialize the camera during health checks
            // This can disrupt ongoing streams
            
        } catch (Exception e) {
            log.error("Error during camera health check: {}", e.getMessage(), e);
            // Don't take any action that might disrupt the stream
        }
    }
}