package com.camcheck.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to dynamically optimize memory usage based on measured VM capacity
 */
@Service
@Slf4j
public class DynamicMemoryOptimizer {

    @Value("${dynamic.memory.enabled:true}")
    private boolean dynamicMemoryEnabled;
    
    @Value("${dynamic.memory.target.percent:75}")
    private int targetMemoryPercent;
    
    private final VMCapacityMeasurementService vmCapacityService;
    private final ImageObjectPool imageObjectPool;
    private final FrameCacheService frameCacheService;
    
    private final AtomicBoolean isHighMemory = new AtomicBoolean(false);
    private final AtomicBoolean isCriticalMemory = new AtomicBoolean(false);
    private final AtomicInteger gcCount = new AtomicInteger(0);
    
    // Dynamic memory limits
    private int targetMemoryMB = 30; // Default target
    private int maxMemoryMB = 40;    // Default max
    private int criticalMemoryMB = 35; // Default critical threshold
    
    @Autowired
    public DynamicMemoryOptimizer(
            VMCapacityMeasurementService vmCapacityService,
            ImageObjectPool imageObjectPool,
            FrameCacheService frameCacheService,
            ApplicationEventPublisher eventPublisher) {
        this.vmCapacityService = vmCapacityService;
        this.imageObjectPool = imageObjectPool;
        this.frameCacheService = frameCacheService;
    }
    
    @PostConstruct
    public void init() {
        log.info("Dynamic Memory Optimizer initialized");
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationStartup() {
        // Wait for VM capacity measurement to complete
        waitForCapacityMeasurement();
        
        // Calculate optimal memory settings based on VM capacity
        calculateOptimalMemorySettings();
        
        // Apply initial optimizations
        applyMemoryOptimizations();
    }
    
    /**
     * Wait for VM capacity measurement to complete
     */
    private void waitForCapacityMeasurement() {
        int maxAttempts = 10;
        int attempts = 0;
        
        while (!vmCapacityService.isMeasurementComplete() && attempts < maxAttempts) {
            try {
                log.info("Waiting for VM capacity measurement to complete...");
                Thread.sleep(1000);
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        if (!vmCapacityService.isMeasurementComplete()) {
            log.warn("VM capacity measurement did not complete in time, using default settings");
        }
    }
    
    /**
     * Calculate optimal memory settings based on VM capacity
     */
    @SuppressWarnings("unchecked")
    private void calculateOptimalMemorySettings() {
        if (!vmCapacityService.isMeasurementComplete()) {
            log.info("Using default memory settings: target={}MB, max={}MB", targetMemoryMB, maxMemoryMB);
            return;
        }
        
        Map<String, Object> capacity = vmCapacityService.getCapacityInfo();
        Map<String, Object> memoryStats = (Map<String, Object>) capacity.get("memory");
        
        if (memoryStats != null && memoryStats.containsKey("maxMemoryMB")) {
            long maxJvmMemory = ((Number) memoryStats.get("maxMemoryMB")).longValue();
            
            // Calculate target memory as a percentage of max JVM memory
            targetMemoryMB = (int) (maxJvmMemory * targetMemoryPercent / 100);
            
            // Calculate max memory as 90% of max JVM memory
            maxMemoryMB = (int) (maxJvmMemory * 0.9);
            
            // Calculate critical memory as halfway between target and max
            criticalMemoryMB = (targetMemoryMB + maxMemoryMB) / 2;
            
            log.info("Calculated optimal memory settings based on VM capacity:");
            log.info("Max JVM Memory: {}MB", maxJvmMemory);
            log.info("Target Memory: {}MB ({}% of max)", targetMemoryMB, targetMemoryPercent);
            log.info("Critical Memory: {}MB", criticalMemoryMB);
            log.info("Max Memory: {}MB", maxMemoryMB);
        } else {
            log.warn("Could not determine max JVM memory from capacity measurement, using defaults");
        }
    }
    
    /**
     * Apply memory optimizations based on current memory usage
     */
    @Scheduled(fixedRateString = "${dynamic.memory.check.interval:5000}")
    public void checkAndOptimizeMemory() {
        if (!dynamicMemoryEnabled) {
            return;
        }
        
        long usedMemoryMB = getCurrentUsedMemoryMB();
        
        // Check if we're above target memory
        if (usedMemoryMB > targetMemoryMB) {
            if (!isHighMemory.get()) {
                log.info("Memory usage above target: {}MB > {}MB, applying optimizations", 
                        usedMemoryMB, targetMemoryMB);
                isHighMemory.set(true);
            }
            
            // Apply optimizations based on how far we are from target
            if (usedMemoryMB > maxMemoryMB) {
                // Emergency: well above max, take drastic measures
                applyEmergencyOptimizations();
            } else if (usedMemoryMB > criticalMemoryMB) {
                // Critical: approaching max, take strong measures
                applyCriticalMemoryOptimizations();
            } else {
                // High: above target but below critical, take moderate measures
                applyHighMemoryOptimizations();
            }
        } else if (isHighMemory.get()) {
            // We're back below target
            log.info("Memory usage back below target: {}MB < {}MB", usedMemoryMB, targetMemoryMB);
            isHighMemory.set(false);
            isCriticalMemory.set(false);
        }
        
        // Periodically log memory usage (every ~30 seconds)
        if (System.currentTimeMillis() % 30000 < 5000) {
            logMemoryUsage();
        }
    }
    
    /**
     * Apply initial memory optimizations
     */
    private void applyMemoryOptimizations() {
        // Force initial garbage collection
        System.gc();
        System.runFinalization();
        
        // Log initial memory state
        logMemoryUsage();
    }
    
    /**
     * Apply emergency optimizations when memory usage is critical
     */
    private void applyEmergencyOptimizations() {
        log.warn("Applying emergency memory optimizations");
        
        // Force full garbage collection
        System.gc();
        System.runFinalization();
        gcCount.incrementAndGet();
        
        // Clear all caches
        imageObjectPool.cleanUp();
        frameCacheService.cleanUpCache();
        
        // Set critical memory flag
        isCriticalMemory.set(true);
    }
    
    /**
     * Apply optimizations when memory usage is approaching critical levels
     */
    private void applyCriticalMemoryOptimizations() {
        if (!isCriticalMemory.getAndSet(true)) {
            log.warn("Memory usage approaching critical level, applying stronger optimizations");
        }
        
        // Force garbage collection every other check
        if (System.currentTimeMillis() % 10000 < 5000) {
            System.gc();
            gcCount.incrementAndGet();
        }
        
        // Clear image pool
        imageObjectPool.cleanUp();
        
        // Reduce frame cache size
        frameCacheService.cleanUpCache();
    }
    
    /**
     * Apply optimizations when memory usage is high but not critical
     */
    private void applyHighMemoryOptimizations() {
        // Clear unused resources
        imageObjectPool.cleanUp();
        
        // Apply GC less frequently
        if (System.currentTimeMillis() % 30000 < 5000) {
            System.gc();
            gcCount.incrementAndGet();
        }
    }
    
    /**
     * Get current used memory in MB
     * 
     * @return Used memory in MB
     */
    private long getCurrentUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    /**
     * Log memory usage information
     */
    private void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
        long totalMemoryMB = runtime.totalMemory() / (1024 * 1024);
        long freeMemoryMB = runtime.freeMemory() / (1024 * 1024);
        long usedMemoryMB = totalMemoryMB - freeMemoryMB;
        
        log.info("Memory usage: {}MB used, {}MB total, {}MB max ({}% of max)", 
                usedMemoryMB, totalMemoryMB, maxMemoryMB, 
                maxMemoryMB > 0 ? (usedMemoryMB * 100 / maxMemoryMB) : 0);
        
        log.info("Memory thresholds: target={}MB, critical={}MB, max={}MB, GC count={}", 
                targetMemoryMB, criticalMemoryMB, maxMemoryMB, gcCount.get());
    }
    
    /**
     * Get memory statistics
     * 
     * @return Memory statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        Runtime runtime = Runtime.getRuntime();
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
        long totalMemoryMB = runtime.totalMemory() / (1024 * 1024);
        long freeMemoryMB = runtime.freeMemory() / (1024 * 1024);
        long usedMemoryMB = totalMemoryMB - freeMemoryMB;
        
        stats.put("usedMemoryMB", usedMemoryMB);
        stats.put("totalMemoryMB", totalMemoryMB);
        stats.put("maxMemoryMB", maxMemoryMB);
        stats.put("freeMemoryMB", freeMemoryMB);
        stats.put("usagePercent", maxMemoryMB > 0 ? (usedMemoryMB * 100 / maxMemoryMB) : 0);
        
        stats.put("targetMemoryMB", targetMemoryMB);
        stats.put("criticalMemoryMB", criticalMemoryMB);
        stats.put("maxMemoryThresholdMB", maxMemoryMB);
        stats.put("isHighMemory", isHighMemory.get());
        stats.put("isCriticalMemory", isCriticalMemory.get());
        stats.put("gcCount", gcCount.get());
        
        return stats;
    }
    
    /**
     * Check if memory usage is high
     * 
     * @return True if memory usage is high
     */
    public boolean isHighMemory() {
        return isHighMemory.get();
    }
    
    /**
     * Check if memory usage is critical
     * 
     * @return True if memory usage is critical
     */
    public boolean isCriticalMemory() {
        return isCriticalMemory.get();
    }
    
    /**
     * Get recommended quality level for processing based on memory usage
     * 
     * @return Quality level between 0.0 (lowest) and 1.0 (highest)
     */
    public double getRecommendedQuality() {
        long usedMemoryMB = getCurrentUsedMemoryMB();
        
        if (usedMemoryMB >= maxMemoryMB) {
            // Emergency: lowest quality
            return 0.2;
        } else if (usedMemoryMB >= criticalMemoryMB) {
            // Critical: low quality
            return 0.4;
        } else if (usedMemoryMB >= targetMemoryMB) {
            // High: medium quality
            return 0.6;
        } else {
            // Normal: high quality
            return 0.8;
        }
    }
} 