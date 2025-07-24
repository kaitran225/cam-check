package com.camcheck.controller;

import com.camcheck.service.JVMOptimizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for health checks
 * Used by Render.com to determine if the application is healthy
 */
@RestController
@Slf4j
public class HealthCheckController {

    private final Optional<JVMOptimizer> freeTierOptimizer;
    private final MemoryMXBean memoryMXBean;
    
    @Autowired
    public HealthCheckController(Optional<JVMOptimizer> freeTierOptimizer) {
        this.freeTierOptimizer = freeTierOptimizer;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
    }
    
    /**
     * Health check endpoint
     * Used by Render.com to determine if the application is healthy
     * 
     * @return Health check response
     */
    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, Object>> health() {
        // Record activity if free tier mode is enabled
        freeTierOptimizer.ifPresent(JVMOptimizer::recordActivity);
        
        // Check memory usage
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        long usedMemory = heapMemoryUsage.getUsed();
        long maxMemory = heapMemoryUsage.getMax();
        double usagePercent = (double) usedMemory / maxMemory * 100;
        
        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        
        Map<String, Object> details = new HashMap<>();
        details.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        details.put("memory", Map.of(
                "used", usedMemory,
                "max", maxMemory,
                "percent", usagePercent
        ));
        
        response.put("details", details);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Simple ping endpoint for health checks
     * 
     * @return Simple response
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> simplePing() {
        // Record activity if free tier mode is enabled
        freeTierOptimizer.ifPresent(JVMOptimizer::recordActivity);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
} 