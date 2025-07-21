package com.camcheck.controller;

import com.camcheck.service.CameraService;
import com.camcheck.service.MotionDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for camera operations
 * Simplified version with no server-side camera functionality
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Camera API", description = "REST API endpoints for camera operations")
@Slf4j
public class ApiController {

    @SuppressWarnings("unused")

    private final CameraService cameraService;
    @SuppressWarnings("unused")

    private final MotionDetectionService motionDetectionService;

    @Value("${camcheck.camera.frame-rate}")
    private int frameRate;

    @Value("${camcheck.camera.force-fallback:false}")
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> getStatus() {
        log.debug("API request for camera status");

        Map<String, Object> statusData = new HashMap<>();
        statusData.put("streaming", false); // Always false as we don't use server cameras
        statusData.put("motionDetection", false); // Always false as motion detection is disabled
        statusData.put("fallbackMode", false); // Always false as we don't use server cameras

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", statusData);

        return ResponseEntity.ok(response);
    }

    /**
     * Start streaming - disabled, using client cameras only
     */
    @PostMapping("/camera/stream/start")
    @Operation(summary = "Start camera streaming", description = "No longer functional - using client cameras only")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request acknowledged", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> startStreaming() {
        log.info("API request to start streaming - ignored, using client cameras only");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Server-side camera streaming is disabled, using client cameras only");

        return ResponseEntity.ok(response);
    }

    /**
     * Stop streaming - disabled, using client cameras only
     */
    @PostMapping("/camera/stream/stop")
    @Operation(summary = "Stop camera streaming", description = "No longer functional - using client cameras only")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request acknowledged", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> stopStreaming() {
        log.info("API request to stop streaming - ignored, using client cameras only");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Server-side camera streaming is disabled, using client cameras only");

        return ResponseEntity.ok(response);
    }

    /**
     * Take a snapshot - disabled, using client cameras only
     */
    @GetMapping("/camera/snapshot")
    @Operation(summary = "Take camera snapshot", description = "No longer functional - using client cameras only")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request acknowledged", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> takeSnapshot() {
        log.info("API request to take snapshot - ignored, using client cameras only");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Server-side camera snapshots are disabled, use client cameras only");

        return ResponseEntity.ok(response);
    }

    /**
     * Set motion detection status - disabled
     */
    @PutMapping("/motion-detection/{enabled}")
    @Operation(summary = "Set motion detection status", description = "No longer functional - motion detection is disabled")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request acknowledged", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> setMotionDetection(
            @Parameter(description = "Enable (true) or disable (false) motion detection") @PathVariable boolean enabled) {
        log.info("API request to set motion detection: {} - ignored, motion detection is disabled", enabled);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Motion detection is permanently disabled");

        return ResponseEntity.ok(response);
    }

    /**
     * Get current system settings
     */
    @GetMapping("/settings")
    @Operation(summary = "Get system settings", description = "Returns the current system settings")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> getSettings() {
        log.info("API request for system settings");

        Map<String, Object> settings = new HashMap<>();
        settings.put("frameRate", frameRate);
        settings.put("forceFallback", forceFallback);
        settings.put("sensitivity", sensitivity);
        settings.put("ipCameraUrl", ipCameraUrl);
        settings.put("clientCameraOnly", true);

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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings applied successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> applySettings(@RequestBody Map<String, Object> settings) {
        log.info("API request to apply settings: {}", settings);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Settings received but server-side camera functionality is disabled");

        return ResponseEntity.ok(response);
    }

    /**
     * Detect available cameras - disabled, using client cameras only
     */
    @GetMapping("/cameras/detect")
    @Operation(summary = "Detect cameras", description = "No longer functional - using client cameras only")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request acknowledged", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> detectCameras() {
        log.info("API request to detect cameras - ignored, using client cameras only");

        // Return empty list since we're not using server cameras
        List<Map<String, Object>> cameras = new ArrayList<>();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Server-side camera detection is disabled, use client cameras only");
        response.put("cameras", cameras);
        response.put("count", 0);

        return ResponseEntity.ok(response);
    }

    /**
     * Get available cameras - disabled, using client cameras only
     */
    @GetMapping("/cameras")
    @Operation(summary = "Get available cameras", description = "No longer functional - using client cameras only")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request acknowledged", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> getAvailableCameras() {
        log.info("API request for available cameras - ignored, using client cameras only");

        // Return empty list since we're not using server cameras
        List<Map<String, Object>> cameras = new ArrayList<>();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Server-side camera listing is disabled, use client cameras only");
        response.put("cameras", cameras);
        response.put("count", 0);

        return ResponseEntity.ok(response);
    }

    /**
     * Switch to a different camera - disabled, using client cameras only
     */
    @PostMapping("/cameras/switch/{cameraIndex}")
    @Operation(summary = "Switch camera", description = "No longer functional - using client cameras only")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request acknowledged", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> switchCamera(
            @Parameter(description = "Index of the camera to switch to") @PathVariable int cameraIndex) {
        log.info("API request to switch to camera: {} - ignored, using client cameras only", cameraIndex);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Server-side camera switching is disabled, use client cameras only");

        return ResponseEntity.ok(response);
    }

    /**
     * Toggle fallback mode - disabled, using client cameras only
     */
    @PostMapping("/fallback/{enabled}")
    @Operation(summary = "Toggle fallback mode", description = "No longer functional - using client cameras only")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request acknowledged", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> setFallbackMode(
            @Parameter(description = "Enable (true) or disable (false) fallback mode") @PathVariable boolean enabled) {
        log.info("API request to set fallback mode: {} - ignored, using client cameras only", enabled);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Server-side fallback mode is disabled, use client cameras only");

        return ResponseEntity.ok(response);
    }

    /**
     * Restart system
     */
    @PostMapping("/system/restart")
    @Operation(summary = "Restart system", description = "Initiates a system restart")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Restart initiated successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> restartSystem() {
        log.info("API request to restart system");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Restart initiated (feature not fully implemented)");

        return ResponseEntity.ok(response);
    }
}