package com.camcheck.controller;

import com.camcheck.service.CameraService;
import com.camcheck.service.MotionDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for camera operations
 */
@Controller
@Slf4j
@Tag(name = "Camera Controller", description = "API endpoints for camera operations")
public class CameraController {

    private final CameraService cameraService;
    private final MotionDetectionService motionDetectionService;
    
    public CameraController(CameraService cameraService, 
                          MotionDetectionService motionDetectionService) {
        this.cameraService = cameraService;
        this.motionDetectionService = motionDetectionService;
    }
    
    /**
     * Main page
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("isStreaming", cameraService.isStreaming());
        model.addAttribute("isMotionDetectionEnabled", motionDetectionService.isEnabled());
        model.addAttribute("isFallbackMode", cameraService.isUsingFallback());
        return "index";
    }
    
    /**
     * Login page
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
    
    /**
     * Start streaming
     */
    @PostMapping("/api/camera/start")
    @ResponseBody
    @Operation(summary = "Start camera streaming", description = "Starts the camera stream for viewing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stream started successfully",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> startStreaming() {
        log.info("Request to start streaming");
        cameraService.startStreaming();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("streaming", true);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Stop streaming
     */
    @PostMapping("/api/camera/stop")
    @ResponseBody
    @Operation(summary = "Stop camera streaming", description = "Stops the camera stream")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stream stopped successfully",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> stopStreaming() {
        log.info("Request to stop streaming");
        cameraService.stopStreaming();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("streaming", false);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Take a snapshot
     */
    @GetMapping("/api/camera/snapshot")
    @ResponseBody
    @Operation(summary = "Take camera snapshot", description = "Captures a single image from the camera")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Snapshot taken successfully",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "500", description = "Server error or failed to take snapshot",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> takeSnapshot() {
        log.info("Request to take snapshot");
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
     * Toggle motion detection
     */
    @PostMapping("/api/motion/{enabled}")
    @ResponseBody
    @Operation(summary = "Toggle motion detection", description = "Enable or disable motion detection")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Motion detection setting updated",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> toggleMotionDetection(
            @Parameter(description = "Enable (true) or disable (false) motion detection") 
            @PathVariable boolean enabled) {
        log.info("Request to set motion detection: {}", enabled);
        motionDetectionService.setEnabled(enabled);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("motionDetection", enabled);
        
        return ResponseEntity.ok(response);
    }
} 