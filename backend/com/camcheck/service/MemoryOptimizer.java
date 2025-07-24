package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to aggressively optimize memory usage to stay around 30MB
 */
@Service
@Slf4j
public class MemoryOptimizer {

    @Value("${memory.target.mb:30}")
    private int targetMemoryMb;
    
    @Value("${memory.max.mb:40}")
    private int maxMemoryMb;
    
    @Value("${memory.check.interval.ms:5000}")
    private int checkIntervalMs;
    
    @Value("${memory.aggressive.gc:true}")
    private boolean aggressiveGc;
    
    private final MemoryMXBean memoryMXBean;
    private final List<MemoryPoolMXBean> memoryPoolMXBeans;
    private final List<GarbageCollectorMXBean> gcMXBeans;
    
    private final AtomicBoolean isHighMemory = new AtomicBoolean(false);
    private final AtomicInteger gcCount = new AtomicInteger(0);
    private final AtomicInteger emergencyGcCount = new AtomicInteger(0);
    
    // Optional dependencies
    private final ImageObjectPool imageObjectPool;
    private final FrameCacheService frameCacheService;
    
    @Autowired
    public MemoryOptimizer(
            @Lazy ImageObjectPool imageObjectPool,
            @Lazy FrameCacheService frameCacheService) {
        this.imageObjectPool = imageObjectPool;
        this.frameCacheService = frameCacheService;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        this.gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
    }
    
    @PostConstruct
    public void init() {
        log.info("Memory optimizer initialized with target: {}MB, max: {}MB", targetMemoryMb, maxMemoryMb);
        
        // Apply initial optimizations
        Runtime.getRuntime().gc();
        
        // Log initial memory state
        logMemoryUsage();
        
        // Apply aggressive optimizations
        applyLowMemoryOptimizations();
    }
    
    /**
     * Check memory usage regularly and take action if needed
     */
    @Scheduled(fixedRateString = "${memory.check.interval.ms:5000}")
    public void checkMemory() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long usedMemoryMb = heapUsage.getUsed() / (1024 * 1024);
        
        // Check if we're above target memory
        if (usedMemoryMb > targetMemoryMb) {
            if (!isHighMemory.get()) {
                log.info("Memory usage above target: {}MB > {}MB, applying optimizations", 
                        usedMemoryMb, targetMemoryMb);
                isHighMemory.set(true);
            }
            
            // Apply optimizations based on how far we are from target
            if (usedMemoryMb > maxMemoryMb) {
                // Emergency: well above max, take drastic measures
                applyEmergencyOptimizations();
            } else if (usedMemoryMb > (targetMemoryMb + (maxMemoryMb - targetMemoryMb) / 2)) {
                // High: significantly above target, take strong measures
                applyHighMemoryOptimizations();
            } else {
                // Moderate: slightly above target, take moderate measures
                applyModerateMemoryOptimizations();
            }
        } else if (isHighMemory.get()) {
            // We're back below target
            log.info("Memory usage back below target: {}MB < {}MB", usedMemoryMb, targetMemoryMb);
            isHighMemory.set(false);
        }
        
        // Periodically log memory usage (every ~30 seconds)
        if (System.currentTimeMillis() % 30000 < checkIntervalMs) {
            logMemoryUsage();
        }
    }
    
    /**
     * Apply emergency optimizations when memory usage is critical
     */
    private void applyEmergencyOptimizations() {
        log.warn("Applying emergency memory optimizations");
        
        // Force full garbage collection
        System.gc();
        System.runFinalization();
        emergencyGcCount.incrementAndGet();
        
        // Clear all caches
        clearAllCaches();
        
        // Suggest to JVM to reduce memory footprint
        suggestJvmShrink();
    }
    
    /**
     * Apply high memory optimizations when memory usage is high
     */
    private void applyHighMemoryOptimizations() {
        log.info("Applying high memory optimizations");
        
        // Clear soft references and weak caches
        if (aggressiveGc) {
            System.gc();
            gcCount.incrementAndGet();
        }
        
        // Clear image pool
        imageObjectPool.cleanUp();
        
        // Reduce frame cache size
        frameCacheService.cleanUpCache();
    }
    
    /**
     * Apply moderate memory optimizations when memory usage is above target
     */
    private void applyModerateMemoryOptimizations() {
        // Clear unused resources
        imageObjectPool.cleanUp();
        
        // Apply GC if aggressive mode is enabled
        if (aggressiveGc && System.currentTimeMillis() % 60000 < checkIntervalMs) {
            System.gc();
            gcCount.incrementAndGet();
        }
    }
    
    /**
     * Apply low memory optimizations on startup
     */
    private void applyLowMemoryOptimizations() {
        // Set thread priorities
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        
        // Suggest to JVM to reduce memory footprint
        suggestJvmShrink();
    }
    
    /**
     * Clear all caches in the application
     */
    private void clearAllCaches() {
        // Clear image pool
        imageObjectPool.cleanUp();
        
        // Clear frame cache
        frameCacheService.cleanUpCache();
        
        // Clear any other caches here
    }
    
    /**
     * Suggest to JVM to reduce memory footprint
     * This doesn't guarantee memory will be released but can help
     */
    private void suggestJvmShrink() {
        // Force finalization of pending objects
        System.runFinalization();
        
        // Run garbage collector
        System.gc();
        
        // Compact strings if possible (Java 9+)
        try {
            // This is a hack to access internal JVM method via reflection
            // It's not guaranteed to work on all JVMs
            Class<?> stringDeduplicationClass = Class.forName("java.lang.StringDeduplication");
            if (stringDeduplicationClass != null) {
                try {
                    java.lang.reflect.Method configMethod = stringDeduplicationClass.getDeclaredMethod("config");
                    if (configMethod != null) {
                        configMethod.setAccessible(true);
                        configMethod.invoke(null);
                    }
                } catch (Exception e) {
                    // Ignore, this is just an optimization attempt
                }
            }
        } catch (Exception e) {
            // Ignore, this is just an optimization attempt
        }
    }
    
    /**
     * Log detailed memory usage information
     */
    private void logMemoryUsage() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long usedMemoryMb = heapUsage.getUsed() / (1024 * 1024);
        long maxMemoryMb = heapUsage.getMax() / (1024 * 1024);
        long committedMemoryMb = heapUsage.getCommitted() / (1024 * 1024);
        
        log.info("Memory usage: {}MB used, {}MB committed, {}MB max ({}% of max)", 
                usedMemoryMb, committedMemoryMb, maxMemoryMb, 
                maxMemoryMb > 0 ? (usedMemoryMb * 100 / maxMemoryMb) : 0);
        
        // Log GC statistics
        long totalGcCount = 0;
        long totalGcTime = 0;
        for (GarbageCollectorMXBean gcBean : gcMXBeans) {
            totalGcCount += gcBean.getCollectionCount();
            totalGcTime += gcBean.getCollectionTime();
        }
        
        log.debug("GC stats: {} collections, {} ms total time, {} manual GCs, {} emergency GCs", 
                totalGcCount, totalGcTime, gcCount.get(), emergencyGcCount.get());
    }
    
    /**
     * Get memory statistics
     * 
     * @return Memory statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        stats.put("usedMemoryMb", heapUsage.getUsed() / (1024 * 1024));
        stats.put("maxMemoryMb", heapUsage.getMax() / (1024 * 1024));
        stats.put("committedMemoryMb", heapUsage.getCommitted() / (1024 * 1024));
        stats.put("targetMemoryMb", targetMemoryMb);
        stats.put("maxTargetMemoryMb", maxMemoryMb);
        stats.put("isHighMemory", isHighMemory.get());
        stats.put("gcCount", gcCount.get());
        stats.put("emergencyGcCount", emergencyGcCount.get());
        
        // Add memory pool information
        Map<String, Object> pools = new HashMap<>();
        for (MemoryPoolMXBean pool : memoryPoolMXBeans) {
            Map<String, Object> poolStats = new HashMap<>();
            MemoryUsage usage = pool.getUsage();
            poolStats.put("used", usage.getUsed() / (1024 * 1024) + "MB");
            poolStats.put("max", usage.getMax() / (1024 * 1024) + "MB");
            poolStats.put("committed", usage.getCommitted() / (1024 * 1024) + "MB");
            pools.put(pool.getName(), poolStats);
        }
        stats.put("memoryPools", pools);
        
        return stats;
    }
} 