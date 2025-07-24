package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.service.FrameCacheService;
import com.camcheck.service.ImageObjectPool;
import com.camcheck.service.MemoryOptimizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for memory management and monitoring
 */
@RestController
@RequestMapping("/api/v1/system/memory")
@Slf4j
public class MemoryController {

    private final MemoryOptimizer memoryOptimizer;
    private final ImageObjectPool imageObjectPool;
    private final FrameCacheService frameCacheService;
    private final MemoryMXBean memoryMXBean;
    
    @Autowired
    public MemoryController(
            MemoryOptimizer memoryOptimizer,
            ImageObjectPool imageObjectPool,
            FrameCacheService frameCacheService) {
        this.memoryOptimizer = memoryOptimizer;
        this.imageObjectPool = imageObjectPool;
        this.frameCacheService = frameCacheService;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
    }
    
    /**
     * Get memory usage statistics
     * 
     * @return Memory usage statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse> getMemoryStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Get memory optimizer stats
        stats.put("optimizer", memoryOptimizer.getStats());
        
        // Get image pool stats
        stats.put("imagePool", imageObjectPool.getStats());
        
        // Get frame cache stats
        stats.put("frameCache", frameCacheService.getStats());
        
        // Get JVM memory stats
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        Map<String, Object> jvmStats = new HashMap<>();
        jvmStats.put("heapUsed", heapUsage.getUsed() / (1024 * 1024) + " MB");
        jvmStats.put("heapMax", heapUsage.getMax() / (1024 * 1024) + " MB");
        jvmStats.put("heapCommitted", heapUsage.getCommitted() / (1024 * 1024) + " MB");
        jvmStats.put("nonHeapUsed", nonHeapUsage.getUsed() / (1024 * 1024) + " MB");
        jvmStats.put("nonHeapMax", nonHeapUsage.getMax() / (1024 * 1024) + " MB");
        
        // Get runtime stats
        Runtime runtime = Runtime.getRuntime();
        jvmStats.put("freeMemory", runtime.freeMemory() / (1024 * 1024) + " MB");
        jvmStats.put("totalMemory", runtime.totalMemory() / (1024 * 1024) + " MB");
        jvmStats.put("maxMemory", runtime.maxMemory() / (1024 * 1024) + " MB");
        jvmStats.put("availableProcessors", runtime.availableProcessors());
        
        stats.put("jvm", jvmStats);
        
        return ResponseEntity.ok(new ApiResponse("success", "Memory stats retrieved", stats));
    }
    
    /**
     * Force garbage collection (admin only)
     * 
     * @return Success response
     */
    @PostMapping("/gc")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> forceGarbageCollection() {
        log.info("Manual garbage collection requested");
        
        // Run garbage collection
        System.gc();
        System.runFinalization();
        
        // Get memory usage after GC
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long usedMemoryMb = heapUsage.getUsed() / (1024 * 1024);
        long maxMemoryMb = heapUsage.getMax() / (1024 * 1024);
        
        Map<String, Object> result = new HashMap<>();
        result.put("memoryUsed", usedMemoryMb + " MB");
        result.put("memoryMax", maxMemoryMb + " MB");
        result.put("memoryUsagePercent", maxMemoryMb > 0 ? (usedMemoryMb * 100 / maxMemoryMb) : 0);
        
        return ResponseEntity.ok(new ApiResponse("success", "Garbage collection performed", result));
    }
    
    /**
     * Clear all caches (admin only)
     * 
     * @return Success response
     */
    @PostMapping("/clear-caches")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> clearCaches() {
        log.info("Manual cache clearing requested");
        
        // Clear image pool
        imageObjectPool.cleanUp();
        
        // Clear frame cache
        frameCacheService.cleanUpCache();
        
        return ResponseEntity.ok(new ApiResponse("success", "All caches cleared", null));
    }
} 