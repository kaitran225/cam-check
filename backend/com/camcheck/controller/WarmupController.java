package com.camcheck.controller;

import com.camcheck.service.JVMOptimizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for warming up the application after cold starts
 * Particularly useful for Render.com free tier
 */
@RestController
@RequestMapping("/api/internal")
@Slf4j
@ConditionalOnBean(JVMOptimizer.class)
public class WarmupController {

    private final JVMOptimizer freeTierOptimizer;
    
    @Autowired
    public WarmupController(JVMOptimizer freeTierOptimizer) {
        this.freeTierOptimizer = freeTierOptimizer;
    }
    
    /**
     * Warm up endpoint to be called after cold starts
     * This helps improve performance for the first real request
     * 
     * @return Simple response with timestamp
     */
    @GetMapping("/warmup")
    public ResponseEntity<Map<String, Object>> warmup() {
        log.info("Warmup request received");
        
        // Record activity to prevent sleep
        freeTierOptimizer.recordActivity();
        
        // Perform warm-up operations
        freeTierOptimizer.warmUp();
        
        // Return simple response
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", System.currentTimeMillis());
        response.put("uptime", freeTierOptimizer.getUptimeMinutes());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Keep-alive endpoint to prevent the service from sleeping
     * Render.com free tier sleeps after 15 minutes of inactivity
     * 
     * @return Simple response with timestamp
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        // Record activity to prevent sleep
        freeTierOptimizer.recordActivity();
        
        // Return simple response
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get free tier statistics
     * 
     * @return Free tier statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        // Record activity to prevent sleep
        freeTierOptimizer.recordActivity();
        
        // Return statistics
        return ResponseEntity.ok(freeTierOptimizer.getStats());
    }
} 