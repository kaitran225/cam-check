package com.camcheck.controller;

import com.camcheck.controller.v1.BaseController;
import com.camcheck.model.ApiResponse;
import com.camcheck.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified camera controller for all clients
 */
@RestController
@RequestMapping("/api/v1/camera")
@Tag(name = "Camera", description = "API endpoints for camera operations")
@Slf4j
public class CameraController extends BaseController {

    private final CameraService cameraService;
    private final CompressionService compressionService;
    private final RecordingService recordingService;
    private final DenoiseService denoiseService;
    private final FrameCacheService frameCacheService;
    private final MemoryMonitorService memoryMonitorService;
    private final RequestThrottleService throttleService;
    private final ImageObjectPool imageObjectPool;
    
    @Value("${camcheck.media.compression-quality:0.85}")
    private double defaultCompressionQuality;
    
    @Value("${camcheck.media.resolution-scaling.enabled:true}")
    private boolean resolutionScalingEnabled;
    
    @Value("${camcheck.media.delta-encoding.enabled:true}")
    private boolean deltaEncodingEnabled;
    
    @Value("${camcheck.media.denoise.enabled:true}")
    private boolean denoiseEnabled;
    
    @Value("${camcheck.media.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Autowired
    public CameraController(
            CameraService cameraService,
            CompressionService compressionService,
            RecordingService recordingService,
            DenoiseService denoiseService,
            FrameCacheService frameCacheService,
            MemoryMonitorService memoryMonitorService,
            RequestThrottleService throttleService,
            ImageObjectPool imageObjectPool) {
        this.cameraService = cameraService;
        this.compressionService = compressionService;
        this.recordingService = recordingService;
        this.denoiseService = denoiseService;
        this.frameCacheService = frameCacheService;
        this.memoryMonitorService = memoryMonitorService;
        this.throttleService = throttleService;
        this.imageObjectPool = imageObjectPool;
    }
    
    /**
     * Handle camera snapshot
     *
     * @param payload Snapshot data
     * @param authentication Authentication object
     * @return API response
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/snapshot")
    @Operation(summary = "Save camera snapshot", description = "Save a camera snapshot with optional compression and denoising")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Snapshot saved successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> handleSnapshot(
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Processing snapshot from user: {}", username);
        
        // Check if we should throttle this request
        if (!throttleService.acquirePermit()) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Server is busy, please try again later"));
        }
        
        try {
            String imageData = (String) payload.get("image");
            if (imageData == null || imageData.isEmpty()) {
                return error("No image data provided");
            }
            
            // Get compression quality if provided, otherwise use default
            Double quality = payload.containsKey("quality") ? 
                    Double.parseDouble(payload.get("quality").toString()) : defaultCompressionQuality;
            
            // Adjust quality based on memory conditions
            if (memoryMonitorService.isHighMemoryMode()) {
                double adjustedQuality = quality * memoryMonitorService.getRecommendedProcessingQuality();
                log.debug("Adjusting quality from {} to {} due to high memory usage", quality, adjustedQuality);
                quality = adjustedQuality;
            }
            
            // Apply denoising if enabled and requested
            if (denoiseEnabled && Boolean.TRUE.equals(payload.get("denoise"))) {
                String method = payload.containsKey("denoiseMethod") ? 
                        payload.get("denoiseMethod").toString() : "fast-bilateral";
                        
                Double strength = payload.containsKey("denoiseStrength") ? 
                        Double.parseDouble(payload.get("denoiseStrength").toString()) : 0.5;
                        
                imageData = denoiseService.denoiseImage(imageData, method, strength);
                log.debug("Applied denoising with method: {} and strength: {}", method, strength);
            }
            
            // Apply compression if requested
            if (payload.containsKey("compress") && Boolean.TRUE.equals(payload.get("compress"))) {
                imageData = compressionService.compressImage(imageData, quality);
                log.debug("Applied compression with quality: {}", quality);
            }
            
            // Apply resolution scaling if enabled and requested
            if (resolutionScalingEnabled && Boolean.TRUE.equals(payload.get("scale"))) {
                Map<String, Object> scaleOptions = (Map<String, Object>) payload.getOrDefault("scaleOptions", new HashMap<>());
                imageData = cameraService.scaleImage(imageData, scaleOptions);
                log.debug("Applied resolution scaling");
            }
            
            // Decode base64 image
            byte[] imageBytes = Base64.getDecoder().decode(imageData);
            
            // Save snapshot
            String filename = recordingService.saveSnapshot(imageBytes);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("filename", filename);
            responseData.put("size", imageBytes.length);
            responseData.put("timestamp", System.currentTimeMillis());
            
            return success("Snapshot saved", responseData);
        } catch (IllegalArgumentException e) {
            log.error("Invalid image data", e);
            return error("Invalid image data");
        } catch (IOException e) {
            log.error("Error saving snapshot", e);
            return error("Error saving snapshot");
        } finally {
            // Always release the permit
            throttleService.releasePermit();
        }
    }
    
    /**
     * Process camera frame (new endpoint for efficient frame processing)
     *
     * @param payload Frame data and processing options
     * @param authentication Authentication object
     * @return Processed frame
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/process-frame")
    @Operation(summary = "Process camera frame", description = "Process a camera frame with denoising, compression, and scaling")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Frame processed successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> processFrame(
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Processing frame from user: {}", username);
        
        // Check if we should throttle this request
        if (!throttleService.acquirePermit()) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Server is busy, please try again later"));
        }
        
        try {
            String imageData = (String) payload.get("image");
            if (imageData == null || imageData.isEmpty()) {
                return error("No image data provided");
            }
            
            Map<String, Object> processingStats = new HashMap<>();
            long startTime = System.currentTimeMillis();
            
            // Build a processing key for cache lookup
            StringBuilder processingKey = new StringBuilder();
            
            // Check if we have this frame in cache
            if (cacheEnabled) {
                boolean applyDenoise = denoiseEnabled && Boolean.TRUE.equals(payload.get("denoise"));
                boolean applyCompress = Boolean.TRUE.equals(payload.get("compress"));
                boolean applyScale = resolutionScalingEnabled && Boolean.TRUE.equals(payload.get("scale"));
                
                if (applyDenoise) {
                    String method = payload.containsKey("denoiseMethod") ? 
                            payload.get("denoiseMethod").toString() : "fast-bilateral";
                    Double strength = payload.containsKey("denoiseStrength") ? 
                            Double.parseDouble(payload.get("denoiseStrength").toString()) : 0.5;
                    processingKey.append("denoise:").append(method).append(":").append(strength).append(";");
                }
                
                if (applyCompress) {
                    Double quality = payload.containsKey("quality") ? 
                            Double.parseDouble(payload.get("quality").toString()) : defaultCompressionQuality;
                    processingKey.append("compress:").append(quality).append(";");
                }
                
                if (applyScale) {
                    Map<String, Object> scaleOptions = (Map<String, Object>) payload.getOrDefault("scaleOptions", new HashMap<>());
                    processingKey.append("scale:").append(scaleOptions).append(";");
                }
                
                // Check cache
                String cachedFrame = frameCacheService.getCachedFrame(imageData, processingKey.toString());
                if (cachedFrame != null) {
                    processingStats.put("cacheHit", true);
                    processingStats.put("processingTime", System.currentTimeMillis() - startTime);
                    
                    Map<String, Object> responseData = new HashMap<>();
                    responseData.put("image", cachedFrame);
                    responseData.put("processingStats", processingStats);
                    responseData.put("timestamp", System.currentTimeMillis());
                    
                    return success("Frame retrieved from cache", responseData);
                }
                
                processingStats.put("cacheHit", false);
            }
            
            // Adjust quality based on memory conditions
            Double quality = payload.containsKey("quality") ? 
                    Double.parseDouble(payload.get("quality").toString()) : defaultCompressionQuality;
            
            if (memoryMonitorService.isHighMemoryMode()) {
                double adjustedQuality = quality * memoryMonitorService.getRecommendedProcessingQuality();
                log.debug("Adjusting quality from {} to {} due to high memory usage", quality, adjustedQuality);
                quality = adjustedQuality;
                processingStats.put("qualityAdjusted", true);
                processingStats.put("originalQuality", payload.get("quality"));
                processingStats.put("adjustedQuality", quality);
            }
            
            // Apply denoising if enabled and requested
            if (denoiseEnabled && Boolean.TRUE.equals(payload.get("denoise"))) {
                String method = payload.containsKey("denoiseMethod") ? 
                        payload.get("denoiseMethod").toString() : "fast-bilateral";
                        
                Double strength = payload.containsKey("denoiseStrength") ? 
                        Double.parseDouble(payload.get("denoiseStrength").toString()) : 0.5;
                
                // Adjust denoising method based on memory conditions
                if (memoryMonitorService.isHighMemoryMode() && !method.equals("fast-bilateral")) {
                    method = "fast-bilateral";
                    processingStats.put("denoiseMethodAdjusted", true);
                }
                
                long denoiseStart = System.currentTimeMillis();
                imageData = denoiseService.denoiseImage(imageData, method, strength);
                long denoiseTime = System.currentTimeMillis() - denoiseStart;
                
                processingStats.put("denoiseTime", denoiseTime);
                processingStats.put("denoiseMethod", method);
                processingStats.put("denoiseStrength", strength);
                
                log.debug("Applied denoising with method: {} and strength: {} in {} ms", 
                        method, strength, denoiseTime);
            }
            
            // Apply compression if requested
            if (payload.containsKey("compress") && Boolean.TRUE.equals(payload.get("compress"))) {
                long compressStart = System.currentTimeMillis();
                imageData = compressionService.compressImage(imageData, quality);
                long compressTime = System.currentTimeMillis() - compressStart;
                
                processingStats.put("compressTime", compressTime);
                processingStats.put("compressQuality", quality);
                
                log.debug("Applied compression with quality: {} in {} ms", quality, compressTime);
            }
            
            // Apply resolution scaling if enabled and requested
            if (resolutionScalingEnabled && Boolean.TRUE.equals(payload.get("scale"))) {
                Map<String, Object> scaleOptions = (Map<String, Object>) payload.getOrDefault("scaleOptions", new HashMap<>());
                
                // Adjust scaling based on memory conditions
                if (memoryMonitorService.isHighMemoryMode()) {
                    // Increase downscaling factor in high memory mode
                    if (scaleOptions.containsKey("factor")) {
                        double factor = Double.parseDouble(scaleOptions.get("factor").toString());
                        double adjustedFactor = factor * 0.75; // Scale down more
                        scaleOptions.put("factor", adjustedFactor);
                        processingStats.put("scaleFactorAdjusted", true);
                        processingStats.put("originalScaleFactor", factor);
                        processingStats.put("adjustedScaleFactor", adjustedFactor);
                    }
                }
                
                long scaleStart = System.currentTimeMillis();
                imageData = cameraService.scaleImage(imageData, scaleOptions);
                long scaleTime = System.currentTimeMillis() - scaleStart;
                
                processingStats.put("scaleTime", scaleTime);
                processingStats.put("scaleOptions", scaleOptions);
                
                log.debug("Applied resolution scaling in {} ms", scaleTime);
            }
            
            // Cache the processed frame
            if (cacheEnabled) {
                frameCacheService.cacheProcessedFrame(
                        (String) payload.get("image"), 
                        processingKey.toString(), 
                        imageData);
            }
            
            // Calculate total processing time
            long totalTime = System.currentTimeMillis() - startTime;
            processingStats.put("totalProcessingTime", totalTime);
            
            // Add memory stats
            processingStats.put("highMemoryMode", memoryMonitorService.isHighMemoryMode());
            processingStats.put("criticalMemoryMode", memoryMonitorService.isCriticalMemoryMode());
            processingStats.put("memoryUsagePercent", memoryMonitorService.getMemoryUsagePercent());
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("image", imageData);
            responseData.put("processingStats", processingStats);
            responseData.put("timestamp", System.currentTimeMillis());
            
            return success("Frame processed successfully", responseData);
        } catch (IllegalArgumentException e) {
            log.error("Invalid image data", e);
            return error("Invalid image data");
        } catch (IOException e) {
            log.error("Error processing frame", e);
            return error("Error processing frame");
        } finally {
            // Always release the permit
            throttleService.releasePermit();
        }
    }
    
    /**
     * Process multiple camera frames in batch
     *
     * @param payload Batch of frames and processing options
     * @param authentication Authentication object
     * @return Processed frames
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/process-frames-batch")
    @Operation(summary = "Process multiple camera frames", description = "Process multiple camera frames in a single request")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Frames processed successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> processFramesBatch(
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Processing batch of frames from user: {}", username);
        
        // Check if we should throttle this request
        // For batch processing, we need to acquire multiple permits
        if (!throttleService.acquirePermit()) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Server is busy, please try again later"));
        }
        
        try {
            List<String> frames = (List<String>) payload.get("frames");
            if (frames == null || frames.isEmpty()) {
                return error("No frames provided");
            }
            
            // Limit batch size based on memory conditions
            int maxBatchSize = memoryMonitorService.isHighMemoryMode() ? 5 : 10;
            if (frames.size() > maxBatchSize) {
                log.warn("Batch size {} exceeds maximum {}. Truncating.", frames.size(), maxBatchSize);
                frames = frames.subList(0, maxBatchSize);
            }
            
            // Get processing options (apply to all frames)
            boolean applyDenoise = Boolean.TRUE.equals(payload.get("denoise"));
            boolean applyCompress = Boolean.TRUE.equals(payload.get("compress"));
            boolean applyScale = resolutionScalingEnabled && Boolean.TRUE.equals(payload.get("scale"));
            
            String denoiseMethod = payload.containsKey("denoiseMethod") ? 
                    payload.get("denoiseMethod").toString() : "fast-bilateral";
                    
            Double denoiseStrength = payload.containsKey("denoiseStrength") ? 
                    Double.parseDouble(payload.get("denoiseStrength").toString()) : 0.5;
                    
            Double compressQuality = payload.containsKey("quality") ? 
                    Double.parseDouble(payload.get("quality").toString()) : defaultCompressionQuality;
                    
            Map<String, Object> scaleOptions = (Map<String, Object>) payload.getOrDefault("scaleOptions", new HashMap<>());
            
            // Adjust quality based on memory conditions
            if (memoryMonitorService.isHighMemoryMode()) {
                double qualityFactor = memoryMonitorService.getRecommendedProcessingQuality();
                compressQuality *= qualityFactor;
                denoiseStrength *= qualityFactor;
                
                // Force fast-bilateral in high memory mode
                denoiseMethod = "fast-bilateral";
                
                // Increase downscaling factor
                if (scaleOptions.containsKey("factor")) {
                    double factor = Double.parseDouble(scaleOptions.get("factor").toString());
                    scaleOptions.put("factor", factor * 0.75);
                }
            }
            
            // Build processing key for cache
            StringBuilder processingKey = new StringBuilder();
            if (applyDenoise) {
                processingKey.append("denoise:").append(denoiseMethod).append(":").append(denoiseStrength).append(";");
            }
            if (applyCompress) {
                processingKey.append("compress:").append(compressQuality).append(";");
            }
            if (applyScale) {
                processingKey.append("scale:").append(scaleOptions).append(";");
            }
            
            // Process each frame
            List<Map<String, Object>> results = new ArrayList<>();
            long totalStartTime = System.currentTimeMillis();
            int cacheHits = 0;
            
            for (int i = 0; i < frames.size(); i++) {
                String imageData = frames.get(i);
                Map<String, Object> frameResult = new HashMap<>();
                Map<String, Object> frameStats = new HashMap<>();
                
                // Check cache first
                if (cacheEnabled) {
                    String cachedFrame = frameCacheService.getCachedFrame(imageData, processingKey.toString());
                    if (cachedFrame != null) {
                        frameResult.put("frameIndex", i);
                        frameResult.put("image", cachedFrame);
                        frameResult.put("cacheHit", true);
                        results.add(frameResult);
                        cacheHits++;
                        continue;
                    }
                }
                
                // Apply denoising if enabled and requested
                if (denoiseEnabled && applyDenoise) {
                    long denoiseStart = System.currentTimeMillis();
                    imageData = denoiseService.denoiseImage(imageData, denoiseMethod, denoiseStrength);
                    long denoiseTime = System.currentTimeMillis() - denoiseStart;
                    frameStats.put("denoiseTime", denoiseTime);
                }
                
                // Apply compression if requested
                if (applyCompress) {
                    long compressStart = System.currentTimeMillis();
                    imageData = compressionService.compressImage(imageData, compressQuality);
                    long compressTime = System.currentTimeMillis() - compressStart;
                    frameStats.put("compressTime", compressTime);
                }
                
                // Apply resolution scaling if enabled and requested
                if (applyScale) {
                    long scaleStart = System.currentTimeMillis();
                    imageData = cameraService.scaleImage(imageData, scaleOptions);
                    long scaleTime = System.currentTimeMillis() - scaleStart;
                    frameStats.put("scaleTime", scaleTime);
                }
                
                // Cache the processed frame
                if (cacheEnabled) {
                    frameCacheService.cacheProcessedFrame(frames.get(i), processingKey.toString(), imageData);
                }
                
                frameResult.put("frameIndex", i);
                frameResult.put("image", imageData);
                frameResult.put("stats", frameStats);
                frameResult.put("cacheHit", false);
                
                results.add(frameResult);
            }
            
            long totalProcessingTime = System.currentTimeMillis() - totalStartTime;
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("processedFrames", results);
            responseData.put("totalFrames", frames.size());
            responseData.put("cacheHits", cacheHits);
            responseData.put("totalProcessingTime", totalProcessingTime);
            responseData.put("averageFrameTime", (double) totalProcessingTime / frames.size());
            responseData.put("timestamp", System.currentTimeMillis());
            responseData.put("highMemoryMode", memoryMonitorService.isHighMemoryMode());
            responseData.put("memoryUsagePercent", memoryMonitorService.getMemoryUsagePercent());
            
            return success("Frames processed successfully", responseData);
        } catch (IllegalArgumentException e) {
            log.error("Invalid image data", e);
            return error("Invalid image data");
        } catch (IOException e) {
            log.error("Error processing frames", e);
            return error("Error processing frames");
        } catch (ClassCastException e) {
            log.error("Invalid payload format", e);
            return error("Invalid payload format");
        } finally {
            // Always release the permit
            throttleService.releasePermit();
        }
    }
    
    /**
     * Start camera recording
     *
     * @param options Recording options
     * @param authentication Authentication object
     * @return API response
     */
    @PostMapping("/record/start")
    @Operation(summary = "Start recording", description = "Start camera recording with specified options")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recording started successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> startRecording(
            @RequestBody Map<String, Object> options,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Starting recording for user: {}", username);
        
        try {
            String recordingId = recordingService.startRecording(username, options);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("recordingId", recordingId);
            responseData.put("startTime", System.currentTimeMillis());
            
            return success("Recording started", responseData);
        } catch (Exception e) {
            log.error("Error starting recording", e);
            return error("Error starting recording: " + e.getMessage());
        }
    }
    
    /**
     * Stop camera recording
     *
     * @param recordingId Recording ID
     * @param authentication Authentication object
     * @return API response
     */
    @PostMapping("/record/stop/{recordingId}")
    @Operation(summary = "Stop recording", description = "Stop an active camera recording")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recording stopped successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Recording not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> stopRecording(
            @PathVariable String recordingId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Stopping recording {} for user: {}", recordingId, username);
        
        try {
            Map<String, Object> recordingInfo = recordingService.stopRecording(username, recordingId);
            if (recordingInfo == null) {
                return error("Recording not found", 404);
            }
            
            return success("Recording stopped", recordingInfo);
        } catch (Exception e) {
            log.error("Error stopping recording", e);
            return error("Error stopping recording: " + e.getMessage());
        }
    }
    
    /**
     * Get camera settings
     *
     * @param authentication Authentication object
     * @return Camera settings
     */
    @GetMapping("/settings")
    @Operation(summary = "Get camera settings", description = "Get current camera settings")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getCameraSettings(Authentication authentication) {
        String username = authentication.getName();
        log.debug("Getting camera settings for user: {}", username);
        
        Map<String, Object> settings = cameraService.getCameraSettings(username);
        
        return success("Settings retrieved", settings);
    }
    
    /**
     * Update camera settings
     *
     * @param settings New settings
     * @param authentication Authentication object
     * @return Updated settings
     */
    @PutMapping("/settings")
    @Operation(summary = "Update camera settings", description = "Update camera settings")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings updated successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid settings", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> updateCameraSettings(
            @RequestBody Map<String, Object> settings,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Updating camera settings for user: {}", username);
        
        try {
            Map<String, Object> updatedSettings = cameraService.updateCameraSettings(username, settings);
            return success("Settings updated", updatedSettings);
        } catch (IllegalArgumentException e) {
            return error("Invalid settings: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error updating settings", e);
            return error("Error updating settings: " + e.getMessage());
        }
    }
    
    /**
     * Get recording status
     *
     * @param recordingId Recording ID
     * @param authentication Authentication object
     * @return Recording status
     */
    @GetMapping("/record/status/{recordingId}")
    @Operation(summary = "Get recording status", description = "Get status of a recording")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status retrieved successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Recording not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getRecordingStatus(
            @PathVariable String recordingId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Getting status for recording {} (user: {})", recordingId, username);
        
        Map<String, Object> status = recordingService.getRecordingStatus(username, recordingId);
        if (status == null) {
            return error("Recording not found", 404);
        }
        
        return success("Status retrieved", status);
    }
    
    /**
     * List recordings
     *
     * @param authentication Authentication object
     * @return List of recordings
     */
    @GetMapping("/recordings")
    @Operation(summary = "List recordings", description = "Get list of user's recordings")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recordings retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> listRecordings(Authentication authentication) {
        String username = authentication.getName();
        log.debug("Listing recordings for user: {}", username);
        
        Map<String, Object> recordings = recordingService.listRecordings(username);
        
        return success("Recordings retrieved", recordings);
    }
    
    /**
     * Delete recording
     *
     * @param recordingId Recording ID
     * @param authentication Authentication object
     * @return Success response
     */
    @DeleteMapping("/recordings/{recordingId}")
    @Operation(summary = "Delete recording", description = "Delete a recording")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recording deleted successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Recording not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> deleteRecording(
            @PathVariable String recordingId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Deleting recording {} for user: {}", recordingId, username);
        
        boolean deleted = recordingService.deleteRecording(username, recordingId);
        if (!deleted) {
            return error("Recording not found", 404);
        }
        
        return success("Recording deleted");
    }

    /**
     * Get advanced camera processing settings
     *
     * @param authentication Authentication object
     * @return Advanced camera settings
     */
    @GetMapping("/advanced-settings")
    @Operation(summary = "Get advanced camera settings", description = "Get advanced camera processing settings including denoising options")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getAdvancedSettings(Authentication authentication) {
        String username = authentication.getName();
        log.debug("Getting advanced camera settings for user: {}", username);
        
        Map<String, Object> settings = new HashMap<>();
        
        // Basic camera settings
        settings.put("camera", cameraService.getCameraSettings(username));
        
        // Compression settings
        Map<String, Object> compressionSettings = new HashMap<>();
        compressionSettings.put("defaultQuality", defaultCompressionQuality);
        compressionSettings.put("supportedFormats", compressionService.getSupportedFormats());
        settings.put("compression", compressionSettings);
        
        // Denoising settings
        Map<String, Object> denoiseSettings = new HashMap<>();
        denoiseSettings.put("enabled", denoiseEnabled);
        denoiseSettings.put("defaultStrength", 0.5);
        denoiseSettings.put("supportedMethods", Arrays.asList("fast-bilateral", "bilateral", "gaussian", "median"));
        settings.put("denoise", denoiseSettings);
        
        // Resolution scaling settings
        Map<String, Object> scalingSettings = new HashMap<>();
        scalingSettings.put("enabled", resolutionScalingEnabled);
        settings.put("scaling", scalingSettings);
        
        return success("Advanced settings retrieved", settings);
    }
    
    /**
     * Update advanced camera processing settings
     *
     * @param settings New settings
     * @param authentication Authentication object
     * @return Updated settings
     */
    @PutMapping("/advanced-settings")
    @Operation(summary = "Update advanced camera settings", description = "Update advanced camera processing settings")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings updated successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid settings", content = @Content(mediaType = "application/json"))
    })
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse> updateAdvancedSettings(
            @RequestBody Map<String, Object> settings,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Updating advanced camera settings for user: {}", username);
        
        try {
            Map<String, Object> updatedSettings = new HashMap<>();
            
            // Update camera settings if provided
            if (settings.containsKey("camera")) {
                Map<String, Object> cameraSettings = (Map<String, Object>) settings.get("camera");
                updatedSettings.put("camera", cameraService.updateCameraSettings(username, cameraSettings));
            }
            
            // Other settings are stored in application configuration
            // In a real implementation, these would be stored in a database or user preferences
            
            return success("Advanced settings updated", updatedSettings);
        } catch (IllegalArgumentException e) {
            return error("Invalid settings: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error updating advanced settings", e);
            return error("Error updating settings: " + e.getMessage());
        }
    }
    
    /**
     * Get system status and statistics
     *
     * @param authentication Authentication object
     * @return System status and statistics
     */
    @GetMapping("/system-status")
    @Operation(summary = "Get system status", description = "Get system status and statistics")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getSystemStatus(Authentication authentication) {
        String username = authentication.getName();
        log.debug("Getting system status for user: {}", username);
        
        Map<String, Object> status = new HashMap<>();
        
        // Memory stats
        status.put("memory", memoryMonitorService.getMemoryStats());
        
        // Image pool stats
        status.put("imagePool", imageObjectPool.getStats());
        
        // Frame cache stats
        status.put("frameCache", frameCacheService.getStats());
        
        // Request throttle stats
        status.put("requestThrottle", throttleService.getStats());
        
        return success("System status retrieved", status);
    }
} 