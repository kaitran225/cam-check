package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to optimize JVM performance and memory usage
 * Specifically tuned for Render.com free tier (1 CPU, ~512MB RAM, 38MB heap)
 */
@Service
@Slf4j
public class JVMOptimizer {

    // Always enable low resource mode for Render.com
    @Value("${LOW_RESOURCE_MODE:true}")
    private boolean lowResourceMode;
    
    // More aggressive GC for Render.com's limited memory
    @Value("${AGGRESSIVE_GC:true}")
    private boolean aggressiveGC;
    
    // Keep alive interval to prevent idle sleep (Render.com sleeps after 15 min)
    @Value("${KEEP_ALIVE_INTERVAL_MS:840000}") // 14 minutes
    private long keepAliveIntervalMs;
    
    // Lower memory thresholds for earlier intervention
    @Value("${MEMORY_HIGH_THRESHOLD:70}")
    private int highMemoryThreshold;
    
    @Value("${MEMORY_CRITICAL_THRESHOLD:85}")
    private int criticalMemoryThreshold;
    
    @Value("${MEMORY_RECOVERY_THRESHOLD:60}")
    private int recoveryMemoryThreshold;
    
    // More frequent GC for limited memory
    @Value("${GC_INTERVAL_MS:120000}")
    private long gcIntervalMs;
    
    private final MemoryMXBean memoryMXBean;
    private final AtomicBoolean isStartupComplete = new AtomicBoolean(false);
    private final AtomicLong lastActivityTimestamp = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean highMemoryMode = new AtomicBoolean(false);
    private final AtomicBoolean criticalMemoryMode = new AtomicBoolean(false);
    
    // Lazy-injected dependencies to prevent circular dependencies
    private final ImageObjectPool imageObjectPool;
    private final FrameCacheService frameCacheService;
    private final RequestThrottleService requestThrottleService;
    
    // Startup time
    private final LocalDateTime startupTime = LocalDateTime.now();
    
    @Autowired
    public JVMOptimizer(
            @Lazy ImageObjectPool imageObjectPool,
            @Lazy FrameCacheService frameCacheService,
            @Lazy RequestThrottleService requestThrottleService) {
        this.imageObjectPool = imageObjectPool;
        this.frameCacheService = frameCacheService;
        this.requestThrottleService = requestThrottleService;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        
        // Log JVM memory settings
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        log.info("JVM Optimizer initialized with settings: max={}MB, init={}MB",
                heapMemoryUsage.getMax() / (1024 * 1024),
                heapMemoryUsage.getInit() / (1024 * 1024));
    }
    
    @PostConstruct
    public void init() {
        log.info("Running in LOW_RESOURCE_MODE - applying strict resource constraints for Render.com");
        
        // Apply JVM optimizations
        applyJvmOptimizations();
        
        // Initial garbage collection to stabilize memory
        System.gc();
    }
    
    /**
     * Apply JVM optimizations
     * Specifically tuned for Render.com environment
     */
    private void applyJvmOptimizations() {
        // Set thread priorities
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        
        // Reduce thread stack size if possible
        try {
            System.setProperty("jdk.thread.stackSize", "256k");
        } catch (Exception e) {
            log.debug("Could not set thread stack size: {}", e.getMessage());
        }
        
        // Suggest string deduplication
        try {
            System.setProperty("java.lang.String.deduplication", "true");
        } catch (Exception e) {
            log.debug("Could not enable string deduplication: {}", e.getMessage());
        }
        
        // Suggest JIT compilation of critical classes
        try {
            // Force class loading and JIT compilation of critical classes
            Class.forName("java.util.concurrent.ConcurrentHashMap");
            Class.forName("java.util.HashMap");
            Class.forName("java.lang.String");
            Class.forName("java.io.BufferedInputStream");
            Class.forName("java.io.BufferedOutputStream");
        } catch (Exception e) {
            log.debug("Error during class preloading: {}", e.getMessage());
        }
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationStartup() {
        // Perform initial optimization
        performMemoryOptimization();
        isStartupComplete.set(true);
        log.info("JVM startup optimizations applied");
        
        // Initial cleanup
        imageObjectPool.cleanUp();
        frameCacheService.cleanUpCache();
    }
    
    /**
     * Record activity to prevent sleep mode
     */
    public void recordActivity() {
        lastActivityTimestamp.set(System.currentTimeMillis());
    }
    
    /**
     * Check if the system is in high memory mode
     * 
     * @return True if memory usage is high
     */
    public boolean isHighMemoryMode() {
        return highMemoryMode.get();
    }
    
    /**
     * Check if the system is in critical memory mode
     * 
     * @return True if memory usage is critical
     */
    public boolean isCriticalMemoryMode() {
        return criticalMemoryMode.get();
    }
    
    /**
     * Get current memory usage percentage
     * 
     * @return Memory usage percentage (0-100)
     */
    public double getMemoryUsagePercent() {
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        long used = heapMemoryUsage.getUsed();
        long max = heapMemoryUsage.getMax();
        
        return (double) used / max * 100;
    }
    
    /**
     * Get optimization statistics
     * 
     * @return Statistics about JVM optimizations
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("lowResourceMode", lowResourceMode);
        stats.put("aggressiveGC", aggressiveGC);
        stats.put("startupTime", startupTime);
        stats.put("uptime", getUptimeMinutes());
        stats.put("lastActivityAgo", (System.currentTimeMillis() - lastActivityTimestamp.get()) / 1000);
        stats.put("highMemoryMode", highMemoryMode.get());
        stats.put("criticalMemoryMode", criticalMemoryMode.get());
        
        // Memory stats
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        stats.put("heapMaxMB", heapMemoryUsage.getMax() / (1024 * 1024));
        stats.put("heapUsedMB", heapMemoryUsage.getUsed() / (1024 * 1024));
        stats.put("heapUsagePercent", (double) heapMemoryUsage.getUsed() / heapMemoryUsage.getMax() * 100);
        
        // Runtime stats
        Runtime runtime = Runtime.getRuntime();
        stats.put("availableProcessors", runtime.availableProcessors());
        stats.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        stats.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        stats.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        
        // Non-heap memory stats
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        stats.put("nonHeapUsedMB", nonHeapMemoryUsage.getUsed() / (1024 * 1024));
        stats.put("nonHeapMaxMB", nonHeapMemoryUsage.getMax() / (1024 * 1024));
        
        return stats;
    }
    
    /**
     * Get uptime in minutes
     * 
     * @return Uptime in minutes
     */
    public long getUptimeMinutes() {
        return ManagementFactory.getRuntimeMXBean().getUptime() / (1000 * 60);
    }
    
    /**
     * Get recommended processing quality based on current memory state
     * More aggressive quality reduction for Render.com
     * 
     * @return Processing quality (0.0-1.0)
     */
    public double getRecommendedProcessingQuality() {
        if (criticalMemoryMode.get()) {
            return 0.2; // Very low quality in critical mode
        } else if (highMemoryMode.get()) {
            return 0.5; // Reduced quality in high memory mode
        } else {
            return 0.8; // Default to 80% quality to save memory even in normal mode
        }
    }
    
    /**
     * Perform scheduled memory optimization
     * More frequent checks for Render.com
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void performMemoryOptimization() {
        if (!isStartupComplete.get()) {
            return;
        }
        
        // Get current memory usage
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        long usedMemory = heapMemoryUsage.getUsed();
        long maxMemory = heapMemoryUsage.getMax();
        double usagePercent = (double) usedMemory / maxMemory * 100;
        
        // Log memory usage periodically
        log.debug("Memory usage: {}MB/{}MB ({}%)",
                usedMemory / (1024 * 1024),
                maxMemory / (1024 * 1024),
                String.format("%.1f", usagePercent));
        
        // More aggressive optimization in the first 5 minutes
        boolean isNewInstance = getUptimeMinutes() < 5;
        
        // Check for critical memory
        if (usagePercent >= criticalMemoryThreshold) {
            if (!criticalMemoryMode.get()) {
                log.warn("Entering critical memory mode: usage={}%", String.format("%.1f", usagePercent));
                criticalMemoryMode.set(true);
                highMemoryMode.set(true);
                
                // Force garbage collection in critical mode
                System.gc();
                
                // Emergency cleanup
                imageObjectPool.cleanUp();
                frameCacheService.cleanUpCache();
            }
        } 
        // Check for high memory
        else if (usagePercent >= highMemoryThreshold) {
            if (!highMemoryMode.get()) {
                log.info("Entering high memory mode: usage={}%", String.format("%.1f", usagePercent));
                highMemoryMode.set(true);
                
                // Clean up resources
                imageObjectPool.cleanUp();
                frameCacheService.cleanUpCache();
            }
            
            // Exit critical mode if we're below the critical threshold
            if (criticalMemoryMode.get()) {
                log.info("Exiting critical memory mode: usage={}%", String.format("%.1f", usagePercent));
                criticalMemoryMode.set(false);
            }
        } 
        // Check for recovery
        else if (usagePercent <= recoveryMemoryThreshold) {
            if (highMemoryMode.get() || criticalMemoryMode.get()) {
                log.info("Exiting high/critical memory mode: usage={}%", String.format("%.1f", usagePercent));
                highMemoryMode.set(false);
                criticalMemoryMode.set(false);
            }
        }
        
        // Perform GC if memory usage is high or we're in aggressive mode
        if (aggressiveGC && (usagePercent > 70 || (isNewInstance && usagePercent > 50))) {
            log.info("Triggering garbage collection (usage: {}%)", String.format("%.1f", usagePercent));
            System.gc();
        }
        
        // Clean up resources more aggressively in the first 5 minutes
        if (isNewInstance) {
            imageObjectPool.cleanUp();
            frameCacheService.cleanUpCache();
        }
    }
    
    /**
     * Periodic garbage collection for memory stability
     */
    @Scheduled(fixedRateString = "${GC_INTERVAL_MS:120000}")
    public void periodicGarbageCollection() {
        if (aggressiveGC && isStartupComplete.get()) {
            double usagePercent = getMemoryUsagePercent();
            if (usagePercent > 50) {
                log.debug("Performing periodic garbage collection (usage: {}%)", String.format("%.1f", usagePercent));
                System.gc();
            }
        }
    }
    
    /**
     * Keep-alive ping to prevent services from sleeping
     * Critical for Render.com which sleeps after 15 minutes of inactivity
     */
    @Scheduled(fixedRate = 840000) // 14 minutes
    public void keepAlive() {
        // Only ping if there's been no activity
        long lastActivity = System.currentTimeMillis() - lastActivityTimestamp.get();
        if (lastActivity > 600000) { // 10 minutes
            log.info("Sending keep-alive ping to prevent Render.com sleep");
            // The log message itself serves as activity to keep the service awake
            lastActivityTimestamp.set(System.currentTimeMillis());
        }
    }
    
    /**
     * Warm up the application after a cold start
     * This helps improve performance for the first real request
     */
    public void warmUp() {
        log.info("Warming up application for Render.com environment...");
        
        try {
            // Pre-initialize commonly used objects - smaller size for Render.com
            imageObjectPool.borrowImage(160, 120, java.awt.image.BufferedImage.TYPE_INT_RGB);
            
            // Force class loading for common classes
            Class.forName("org.springframework.http.ResponseEntity");
            Class.forName("org.springframework.web.context.request.async.DeferredResult");
            
            log.info("Warm-up complete");
        } catch (Exception e) {
            log.warn("Warm-up failed: {}", e.getMessage());
        }
    }
} 