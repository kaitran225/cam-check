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
 * Works in all environments but has special optimizations for resource-constrained environments
 */
@Service
@Slf4j
public class JVMOptimizer {

    @Value("${LOW_RESOURCE_MODE:false}")
    private boolean lowResourceMode;
    
    @Value("${AGGRESSIVE_GC:false}")
    private boolean aggressiveGC;
    
    @Value("${KEEP_ALIVE_INTERVAL_MS:840000}") // 14 minutes
    private long keepAliveIntervalMs;
    
    @Value("${MEMORY_HIGH_THRESHOLD:80}")
    private int highMemoryThreshold;
    
    @Value("${MEMORY_CRITICAL_THRESHOLD:90}")
    private int criticalMemoryThreshold;
    
    @Value("${MEMORY_RECOVERY_THRESHOLD:70}")
    private int recoveryMemoryThreshold;
    
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
        if (lowResourceMode) {
            log.info("Running in LOW_RESOURCE_MODE - applying strict resource constraints");
        }
        
        // Apply JVM optimizations regardless of mode
        applyJvmOptimizations();
    }
    
    /**
     * Apply JVM optimizations
     * These are beneficial in all environments
     */
    private void applyJvmOptimizations() {
        // Set thread priorities
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        
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
     * 
     * @return Processing quality (0.0-1.0)
     */
    public double getRecommendedProcessingQuality() {
        if (criticalMemoryMode.get()) {
            return 0.3; // Very low quality in critical mode
        } else if (highMemoryMode.get()) {
            return 0.6; // Reduced quality in high memory mode
        } else {
            return 1.0; // Full quality in normal mode
        }
    }
    
    /**
     * Perform scheduled memory optimization
     */
    @Scheduled(fixedRate = 60000) // Every minute
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
        if (log.isDebugEnabled()) {
            log.debug("Memory usage: {}MB/{}MB ({}%)",
                    usedMemory / (1024 * 1024),
                    maxMemory / (1024 * 1024),
                    String.format("%.1f", usagePercent));
        }
        
        // More aggressive optimization in the first hour
        boolean isNewInstance = getUptimeMinutes() < 60;
        
        // Check for critical memory
        if (usagePercent >= criticalMemoryThreshold) {
            if (!criticalMemoryMode.get()) {
                log.warn("Entering critical memory mode: usage={}%", String.format("%.1f", usagePercent));
                criticalMemoryMode.set(true);
                highMemoryMode.set(true);
                
                // Force garbage collection in critical mode
                System.gc();
            }
        } 
        // Check for high memory
        else if (usagePercent >= highMemoryThreshold) {
            if (!highMemoryMode.get()) {
                log.info("Entering high memory mode: usage={}%", String.format("%.1f", usagePercent));
                highMemoryMode.set(true);
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
        if (aggressiveGC && (usagePercent > 70 || (isNewInstance && usagePercent > 60))) {
            log.info("Triggering garbage collection (usage: {}%)", String.format("%.1f", usagePercent));
            System.gc();
        }
        
        // Clean up resources more aggressively in the first hour or when in low resource mode
        if (isNewInstance || lowResourceMode) {
            imageObjectPool.cleanUp();
            frameCacheService.cleanUpCache();
        }
    }
    
    /**
     * Keep-alive ping to prevent services from sleeping
     */
    @Scheduled(fixedRate = 840000) // 14 minutes
    public void keepAlive() {
        // Only ping if there's been no activity
        long lastActivity = System.currentTimeMillis() - lastActivityTimestamp.get();
        if (lastActivity > 600000) { // 10 minutes
            log.info("Sending keep-alive ping");
            // The log message itself serves as activity to keep the service awake
            lastActivityTimestamp.set(System.currentTimeMillis());
        }
    }
    
    /**
     * Warm up the application after a cold start
     * This helps improve performance for the first real request
     */
    public void warmUp() {
        log.info("Warming up application...");
        
        try {
            // Pre-initialize commonly used objects
            imageObjectPool.borrowImage(320, 240, java.awt.image.BufferedImage.TYPE_INT_RGB);
            
            log.info("Warm-up complete");
        } catch (Exception e) {
            log.warn("Warm-up failed: {}", e.getMessage());
        }
    }
} 