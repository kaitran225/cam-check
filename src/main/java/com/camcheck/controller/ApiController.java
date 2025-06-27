package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.model.CameraStatus;
import com.camcheck.service.CameraService;
import com.camcheck.service.MotionDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for camera operations
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Camera API", description = "REST API endpoints for camera operations")
@Slf4j
public class ApiController {

    private final CameraService cameraService;
    private final MotionDetectionService motionDetectionService;
    
    @Value("${camcheck.camera.frame-rate}")
    private int frameRate;
    
    @Value("${camcheck.camera.force-fallback}")
    private boolean forceFallback;
    
    @Value("${camcheck.motion-detection.sensitivity}")
    private int sensitivity;
    
    @Value("${camcheck.camera.ip-url}")
    private String ipCameraUrl;
    
    public ApiController(CameraService cameraService, 
                       MotionDetectionService motionDetectionService) {
        this.cameraService = cameraService;
        this.motionDetectionService = motionDetectionService;
    }
    
    /**
     * Get camera status
     */
    @GetMapping("/status")
    @Operation(summary = "Get camera status", description = "Returns the current status of the camera system")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<CameraStatus>> getStatus() {
        log.info("API request for camera status");
        
        CameraStatus status = new CameraStatus(
            cameraService.isStreaming(),
            motionDetectionService.isEnabled(),
            cameraService.isUsingFallback()
        );
        
        return ResponseEntity.ok(ApiResponse.success(status));
    }
    
    /**
     * Start streaming
     */
    @PostMapping("/camera/stream/start")
    @Operation(summary = "Start camera streaming", description = "Starts the camera stream for viewing")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stream started successfully",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> startStreaming() {
        log.info("API request to start streaming");
        cameraService.startStreaming();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("streaming", true);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Stop streaming
     */
    @PostMapping("/camera/stream/stop")
    @Operation(summary = "Stop camera streaming", description = "Stops the camera stream")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stream stopped successfully",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> stopStreaming() {
        log.info("API request to stop streaming");
        cameraService.stopStreaming();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("streaming", false);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Take a snapshot
     */
    @GetMapping("/camera/snapshot")
    @Operation(summary = "Take camera snapshot", description = "Captures a single image from the camera")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Snapshot taken successfully",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error or failed to take snapshot",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> takeSnapshot() {
        log.info("API request to take snapshot");
        String imageData = cameraService.takeSnapshot();
        
        Map<String, Object> response = new HashMap<>();
        if (imageData != null) {
            response.put("status", "success");
            response.put("image", imageData);
        } else {
            response.put("status", "error");
            response.put("message", "Failed to take snapshot");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Set motion detection status
     */
    @PutMapping("/motion-detection/{enabled}")
    @Operation(summary = "Set motion detection status", description = "Enable or disable motion detection")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Motion detection setting updated",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> setMotionDetection(
            @Parameter(description = "Enable (true) or disable (false) motion detection") 
            @PathVariable boolean enabled) {
        log.info("API request to set motion detection: {}", enabled);
        motionDetectionService.setEnabled(enabled);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("motionDetection", enabled);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get current system settings
     */
    @GetMapping("/settings")
    @Operation(summary = "Get system settings", description = "Returns the current system settings")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings retrieved successfully",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> getSettings() {
        log.info("API request for system settings");
        
        Map<String, Object> settings = new HashMap<>();
        settings.put("frameRate", frameRate);
        settings.put("forceFallback", forceFallback);
        settings.put("sensitivity", sensitivity);
        settings.put("ipCameraUrl", ipCameraUrl);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("settings", settings);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Apply system settings
     */
    @PostMapping("/settings")
    @Operation(summary = "Apply system settings", description = "Applies new system settings")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings applied successfully",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid settings",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> applySettings(@RequestBody Map<String, Object> settings) {
        log.info("API request to apply settings: {}", settings);
        
        // For now, just log the settings and return success
        // In a real implementation, you would apply these settings to the system
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Settings received but not applied (feature not fully implemented)");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Detect available cameras
     */
    @GetMapping("/cameras/detect")
    @Operation(summary = "Detect cameras", description = "Detects available cameras on the system")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Camera detection completed",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> detectCameras() {
        log.info("API request to detect cameras");
        
        List<Map<String, Object>> cameras = cameraService.detectCameras();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("cameras", cameras);
        response.put("count", cameras.size());
        response.put("fallbackMode", cameraService.isUsingFallback());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get available cameras
     */
    @GetMapping("/cameras")
    @Operation(summary = "Get available cameras", description = "Returns list of available cameras")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Camera list retrieved successfully",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> getAvailableCameras() {
        log.info("API request for available cameras");
        
        List<Map<String, Object>> cameras = cameraService.getAvailableCameras();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("cameras", cameras);
        response.put("count", cameras.size());
        response.put("fallbackMode", cameraService.isUsingFallback());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Switch to a different camera
     */
    @PostMapping("/cameras/switch/{cameraIndex}")
    @Operation(summary = "Switch camera", description = "Switches to a different camera")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Camera switched successfully",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid camera index",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> switchCamera(
            @Parameter(description = "Index of the camera to switch to") 
            @PathVariable int cameraIndex) {
        log.info("API request to switch to camera: {}", cameraIndex);
        
        boolean success = cameraService.switchCamera(cameraIndex);
        
        Map<String, Object> response = new HashMap<>();
        if (success) {
            response.put("status", "success");
            response.put("message", "Camera switched successfully");
        } else {
            response.put("status", "error");
            response.put("message", "Failed to switch camera");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Toggle fallback mode
     */
    @PostMapping("/fallback/{enabled}")
    @Operation(summary = "Toggle fallback mode", description = "Enable or disable fallback mode")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Fallback mode setting updated",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> setFallbackMode(
            @Parameter(description = "Enable (true) or disable (false) fallback mode") 
            @PathVariable boolean enabled) {
        log.info("API request to set fallback mode: {}", enabled);
        
        boolean success = cameraService.setFallbackMode(enabled);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("fallbackMode", cameraService.isUsingFallback());
        response.put("message", enabled ? "Fallback mode enabled" : "Fallback mode disabled");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Restart system
     */
    @PostMapping("/system/restart")
    @Operation(summary = "Restart system", description = "Initiates a system restart")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Restart initiated successfully",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> restartSystem() {
        log.info("API request to restart system");
        
        // For now, just log the request and return success
        // In a real implementation, you would initiate a system restart
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Restart initiated (feature not fully implemented)");
        
        return ResponseEntity.ok(response);
    }
} 