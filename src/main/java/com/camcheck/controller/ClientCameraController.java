package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.service.RecordingService;
import com.camcheck.service.CompressionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for client camera functionality
 * Enhanced with audio support and advanced compression options
 */
@Controller
@Slf4j
public class ClientCameraController {

    @Autowired
    private RecordingService recordingService;
    
    @Autowired
    private CompressionService compressionService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Value("${camcheck.media.max-audio-size:100000}")
    private int maxAudioSize;
    
    @Value("${camcheck.media.compression-quality:0.8}")
    private double defaultCompressionQuality;

    @Value("${camcheck.media.audio-enabled:true}")
    private boolean audioEnabled;

    @Value("${camcheck.media.audio-format:opus}")
    private String defaultAudioFormat;
    
    /**
     * Redirect client camera page to main page
     */
    @GetMapping("/client-camera")
    public String clientCamera() {
        return "client-camera"; // This now contains a redirect to the main page
    }
    
    /**
     * Handle client camera snapshot with compression options
     * @param payload Map containing image data and compression options
     * @return API response
     */
    @PostMapping("/api/client-camera/snapshot")
    @ResponseBody
    public ApiResponse handleClientSnapshot(@RequestBody Map<String, Object> payload) {
        try {
            String imageData = (String) payload.get("image");
            if (imageData == null || imageData.isEmpty()) {
                return ApiResponse.error("No image data provided");
            }
            
            // Get compression quality if provided, otherwise use default
            Double quality = payload.containsKey("quality") ? 
                Double.parseDouble(payload.get("quality").toString()) : defaultCompressionQuality;
            
            // Apply any requested compression techniques
            if (payload.containsKey("compress") && Boolean.TRUE.equals(payload.get("compress"))) {
                imageData = compressionService.compressImage(imageData, quality);
                log.debug("Applied compression with quality: {}", quality);
            }
            
            // Decode base64 image
            byte[] imageBytes = Base64.getDecoder().decode(imageData);
            
            // Save snapshot using recording service
            String filename = recordingService.saveSnapshot(imageBytes);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("filename", filename);
            responseData.put("size", imageBytes.length);
            responseData.put("timestamp", System.currentTimeMillis());
            
            return ApiResponse.success("Snapshot saved", responseData);
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
    
    /**
     * Handle audio data transfer
     * @param payload Map containing audio data
     * @return API response
     */
    @PostMapping("/api/client-camera/audio")
    @ResponseBody
    public ApiResponse handleAudioData(@RequestBody Map<String, Object> payload) {
        try {
            if (!audioEnabled) {
                return ApiResponse.error("Audio processing is disabled");
            }
            
            String audioData = (String) payload.get("audio");
            if (audioData == null || audioData.isEmpty()) {
                return ApiResponse.error("No audio data provided");
            }
            
            String recipient = (String) payload.get("recipient");
            if (recipient == null || recipient.isEmpty()) {
                return ApiResponse.error("No recipient specified");
            }
            
            // Get format and quality if provided, otherwise use defaults
            String format = payload.containsKey("format") ? 
                payload.get("format").toString() : defaultAudioFormat;
            
            Double quality = payload.containsKey("quality") ? 
                Double.parseDouble(payload.get("quality").toString()) : defaultCompressionQuality;
            
            // Apply compression if requested
            if (payload.containsKey("compress") && Boolean.TRUE.equals(payload.get("compress"))) {
                try {
                    audioData = compressionService.compressAudio(audioData, format, quality);
                    log.debug("Applied audio compression with format: {}, quality: {}", format, quality);
                } catch (IOException e) {
                    log.warn("Audio compression failed, using uncompressed data", e);
                }
            }
            
            // Decode base64 audio
            byte[] audioBytes = Base64.getDecoder().decode(audioData);
            
            // Check size limits
            if (audioBytes.length > maxAudioSize) {
                log.warn("Audio data exceeds maximum size: {} > {}", audioBytes.length, maxAudioSize);
                return ApiResponse.error("Audio data exceeds maximum size");
            }
            
            // Forward audio data to the recipient
            String sender = (String) payload.get("sender");
            Map<String, Object> audioMessage = new HashMap<>();
            audioMessage.put("audio", audioData);
            audioMessage.put("sender", sender);
            audioMessage.put("timestamp", System.currentTimeMillis());
            audioMessage.put("format", format);
            
            // Send to the specific user's audio topic
            messagingTemplate.convertAndSendToUser(
                recipient,
                "/topic/audio",
                audioMessage
            );
            
            log.debug("Forwarded audio data ({} bytes) from {} to {}", audioBytes.length, sender, recipient);
            
            return ApiResponse.success("Audio data processed", Map.of(
                "size", audioBytes.length,
                "timestamp", System.currentTimeMillis(),
                "recipient", recipient
            ));
        } catch (IllegalArgumentException e) {
            log.error("Invalid audio data", e);
            return ApiResponse.error("Invalid audio data");
        } catch (Exception e) {
            log.error("Unexpected error processing audio", e);
            return ApiResponse.error("Unexpected error processing audio");
        }
    }
    
    /**
     * Get compression settings
     * @return API response with compression settings
     */
    @GetMapping("/api/client-camera/compression-settings")
    @ResponseBody
    public ApiResponse getCompressionSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("defaultQuality", defaultCompressionQuality);
        settings.put("supportedImageFormats", compressionService.getSupportedFormats());
        settings.put("supportedAudioFormats", compressionService.getSupportedAudioFormats());
        settings.put("maxAudioSize", maxAudioSize);
        settings.put("audioEnabled", audioEnabled);
        settings.put("defaultAudioFormat", defaultAudioFormat);
        
        return ApiResponse.success("Compression settings retrieved", settings);
    }
} 