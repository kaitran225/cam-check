package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.model.CameraStatus;
import com.camcheck.service.CameraService;
import com.camcheck.service.MotionDetectionService;
import com.camcheck.service.RecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
    private final RecordingService recordingService;
    
    public ApiController(CameraService cameraService, 
                       MotionDetectionService motionDetectionService,
                       RecordingService recordingService) {
        this.cameraService = cameraService;
        this.motionDetectionService = motionDetectionService;
        this.recordingService = recordingService;
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
            recordingService.isRecording(),
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
     * Start recording
     */
    @PostMapping("/recording/start")
    @Operation(summary = "Start recording", description = "Start recording video from the camera")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recording started successfully",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> startRecording() {
        log.info("API request to start recording");
        recordingService.startRecording();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("recording", true);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Stop recording
     */
    @PostMapping("/recording/stop")
    @Operation(summary = "Stop recording", description = "Stop recording video and save the recording")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recording stopped successfully",
                content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error",
                content = @Content)
    })
    public ResponseEntity<Map<String, Object>> stopRecording() {
        log.info("API request to stop recording");
        recordingService.stopRecording();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("recording", false);
        
        return ResponseEntity.ok(response);
    }
} 