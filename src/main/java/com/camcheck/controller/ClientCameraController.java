package com.camcheck.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.camcheck.model.ApiResponse;
import com.camcheck.service.RecordingService;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/**
 * Controller for handling client-side camera streaming
 */
@Controller
@Slf4j
public class ClientCameraController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private RecordingService recordingService;
    
    /**
     * Serve the client camera page
     */
    @GetMapping("/client-camera")
    public String clientCamera() {
        return "client-camera";
    }
    
    /**
     * Handle incoming camera frames from client
     * @param imageData Base64 encoded image data
     */
    @MessageMapping("/client-camera")
    public void handleClientCameraFrame(String imageData) {
        try {
            // Forward the frame to all subscribers
            messagingTemplate.convertAndSend("/topic/camera", imageData);
        } catch (Exception e) {
            log.error("Error handling client camera frame", e);
        }
    }
    
    /**
     * Handle client camera snapshot
     * @param payload Map containing image data
     * @return API response
     */
    @PostMapping("/api/v1/client-camera/snapshot")
    @ResponseBody
    public ApiResponse handleClientSnapshot(@RequestBody Map<String, String> payload) {
        try {
            String imageData = payload.get("image");
            if (imageData == null || imageData.isEmpty()) {
                return ApiResponse.error("No image data provided");
            }
            
            // Decode base64 image
            byte[] imageBytes = Base64.getDecoder().decode(imageData);
            
            // Save snapshot using recording service
            String filename = recordingService.saveSnapshot(imageBytes);
            
            return ApiResponse.success("Snapshot saved", Map.of("filename", filename));
        } catch (IllegalArgumentException e) {
            log.error("Invalid image data", e);
            return ApiResponse.error("Invalid image data");
        } catch (IOException e) {
            log.error("Error saving snapshot", e);
            return ApiResponse.error("Error saving snapshot");
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ApiResponse.error("Unexpected error");
        }
    }
} 