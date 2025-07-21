package com.camcheck.controller;

import com.camcheck.service.CameraService;
import com.camcheck.service.MotionDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for camera operations
 * Simplified version with no server-side camera functionality
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
        model.addAttribute("isStreaming", false); // Always false as we don't use server cameras
        model.addAttribute("isMotionDetectionEnabled", false); // Always false as motion detection is disabled
        model.addAttribute("isFallbackMode", false); // Always false as we don't use server cameras
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
     * Get system status
     */
    @GetMapping("/api/status")
    @ResponseBody
    @Operation(summary = "Get system status", description = "Returns the current system status")
    @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
            content = @Content(mediaType = "application/json"))
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        
        Map<String, Object> data = new HashMap<>();
        data.put("streaming", false); // Always false as we don't use server cameras
        data.put("motionDetection", false); // Always false as motion detection is disabled
        data.put("fallbackMode", false); // Always false as we don't use server cameras
        
        response.put("data", data);
        
        return ResponseEntity.ok(response);
    }
} 