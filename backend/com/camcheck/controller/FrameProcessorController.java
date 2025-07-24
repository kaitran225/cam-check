package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.service.DynamicMemoryOptimizer;
import com.camcheck.service.MemoryEfficientFrameProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for memory-efficient frame processing
 */
@RestController
@RequestMapping("/api/v1/frames")
@Slf4j
public class FrameProcessorController {

    private final MemoryEfficientFrameProcessor frameProcessor;
    private final DynamicMemoryOptimizer memoryOptimizer;
    
    @Autowired
    public FrameProcessorController(
            MemoryEfficientFrameProcessor frameProcessor,
            DynamicMemoryOptimizer memoryOptimizer) {
        this.frameProcessor = frameProcessor;
        this.memoryOptimizer = memoryOptimizer;
    }
    
    /**
     * Process a single frame
     * 
     * @param requestBody The request body containing the frame and options
     * @return The processed frame
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processFrame(@RequestBody Map<String, Object> requestBody) {
        // Check if we're in critical memory mode
        if (memoryOptimizer.isCriticalMemory()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Server is in critical memory mode, processing is disabled");
            response.put("frame", requestBody.get("frame"));
            response.put("memoryStats", memoryOptimizer.getStats());
            return ResponseEntity.ok(response);
        }
        
        // Get frame and options from request
        String inputFrame = (String) requestBody.get("frame");
        Map<String, Object> options = new HashMap<>();
        
        // Extract options from request
        if (requestBody.containsKey("options") && requestBody.get("options") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> requestOptions = (Map<String, Object>) requestBody.get("options");
            options.putAll(requestOptions);
        }
        
        // Apply dynamic quality based on memory usage
        if (!options.containsKey("quality")) {
            options.put("quality", memoryOptimizer.getRecommendedQuality());
        }
        
        try {
            // Process the frame
            String processedFrame = frameProcessor.processFrame(inputFrame, options);
            
            // Return response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("frame", processedFrame);
            
            // Add memory stats if requested
            if (options.containsKey("includeStats") && (Boolean) options.get("includeStats")) {
                response.put("memoryStats", memoryOptimizer.getStats());
                response.put("processorStats", frameProcessor.getStats());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing frame", e);
            
            // Return error response with original frame
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error processing frame: " + e.getMessage());
            response.put("frame", inputFrame);
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * Process multiple frames in batch
     * 
     * @param requestBody The request body containing the frames and options
     * @return The processed frames
     */
    @PostMapping("/process-batch")
    public ResponseEntity<ApiResponse> processFrameBatch(@RequestBody Map<String, Object> requestBody) {
        // Check if we're in critical memory mode
        if (memoryOptimizer.isCriticalMemory()) {
            return ResponseEntity.ok(new ApiResponse("error", 
                    "Server is in critical memory mode, batch processing is disabled", requestBody));
        }
        
        // Get frames and options from request
        @SuppressWarnings("unchecked")
        java.util.List<String> inputFrames = (java.util.List<String>) requestBody.get("frames");
        Map<String, Object> options = new HashMap<>();
        
        // Extract options from request
        if (requestBody.containsKey("options") && requestBody.get("options") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> requestOptions = (Map<String, Object>) requestBody.get("options");
            options.putAll(requestOptions);
        }
        
        // Apply dynamic quality based on memory usage
        if (!options.containsKey("quality")) {
            options.put("quality", memoryOptimizer.getRecommendedQuality());
        }
        
        try {
            // Process the frames
            java.util.List<String> processedFrames = new java.util.ArrayList<>();
            for (String inputFrame : inputFrames) {
                String processedFrame = frameProcessor.processFrame(inputFrame, options);
                processedFrames.add(processedFrame);
            }
            
            // Return response
            Map<String, Object> response = new HashMap<>();
            response.put("frames", processedFrames);
            
            // Add memory stats if requested
            if (options.containsKey("includeStats") && (Boolean) options.get("includeStats")) {
                response.put("memoryStats", memoryOptimizer.getStats());
                response.put("processorStats", frameProcessor.getStats());
            }
            
            return ResponseEntity.ok(new ApiResponse("success", 
                    "Processed " + processedFrames.size() + " frames", response));
        } catch (Exception e) {
            log.error("Error processing frames", e);
            
            // Return error response with original frames
            return ResponseEntity.ok(new ApiResponse("error", 
                    "Error processing frames: " + e.getMessage(), requestBody));
        }
    }
    
    /**
     * Get frame processor statistics
     * 
     * @return The frame processor statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("processor", frameProcessor.getStats());
        stats.put("memory", memoryOptimizer.getStats());
        
        return ResponseEntity.ok(new ApiResponse("success", "Frame processor statistics", stats));
    }
} 