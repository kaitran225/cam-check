package com.camcheck.controller.v1;

import com.camcheck.model.ApiResponse;
import com.camcheck.service.CameraService;
import com.camcheck.service.CompressionService;
import com.camcheck.service.RecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
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
    
    @Value("${camcheck.media.compression-quality:0.85}")
    private double defaultCompressionQuality;
    
    @Value("${camcheck.media.resolution-scaling.enabled:true}")
    private boolean resolutionScalingEnabled;
    
    @Value("${camcheck.media.delta-encoding.enabled:true}")
    private boolean deltaEncodingEnabled;
    
    @Autowired
    public CameraController(
            CameraService cameraService,
            CompressionService compressionService,
            RecordingService recordingService) {
        this.cameraService = cameraService;
        this.compressionService = compressionService;
        this.recordingService = recordingService;
    }
    
    /**
     * Handle camera snapshot
     *
     * @param payload Snapshot data
     * @param authentication Authentication object
     * @return API response
     */
    @PostMapping("/snapshot")
    @Operation(summary = "Save camera snapshot", description = "Save a camera snapshot with optional compression")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Snapshot saved successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> handleSnapshot(
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Processing snapshot from user: {}", username);
        
        try {
            String imageData = (String) payload.get("image");
            if (imageData == null || imageData.isEmpty()) {
                return error("No image data provided");
            }
            
            // Get compression quality if provided, otherwise use default
            Double quality = payload.containsKey("quality") ? 
                    Double.parseDouble(payload.get("quality").toString()) : defaultCompressionQuality;
            
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
} 