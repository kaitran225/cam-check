package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for monitoring memory usage and adjusting processing parameters accordingly
 */
@Service
@Slf4j
public class MemoryMonitorService {

    @Value("${camcheck.memory.high-threshold:80}")
    private int highMemoryThreshold;
    
    @Value("${camcheck.memory.critical-threshold:90}")
    private int criticalMemoryThreshold;
    
    @Value("${camcheck.memory.recovery-threshold:70}")
    private int recoveryMemoryThreshold;
    
    @Value("${camcheck.memory.check-interval-ms:10000}")
    private long checkIntervalMs;
    
    private final AtomicBoolean highMemoryMode = new AtomicBoolean(false);
    private final AtomicBoolean criticalMemoryMode = new AtomicBoolean(false);
    private final AtomicInteger consecutiveHighMemory = new AtomicInteger(0);
    private final AtomicInteger consecutiveLowMemory = new AtomicInteger(0);
    
    private final MemoryMXBean memoryMXBean;
    
    public MemoryMonitorService() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        log.info("Memory monitor initialized with thresholds: high={}%, critical={}%, recovery={}%",
                highMemoryThreshold, criticalMemoryThreshold, recoveryMemoryThreshold);
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
     * Get memory statistics
     * 
     * @return Memory statistics
     */
    public Map<String, Object> getMemoryStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        long heapUsed = heapMemoryUsage.getUsed();
        long heapMax = heapMemoryUsage.getMax();
        double heapUsagePercent = (double) heapUsed / heapMax * 100;
        
        stats.put("heapUsedBytes", heapUsed);
        stats.put("heapMaxBytes", heapMax);
        stats.put("heapUsagePercent", heapUsagePercent);
        stats.put("nonHeapUsedBytes", nonHeapMemoryUsage.getUsed());
        stats.put("highMemoryMode", highMemoryMode.get());
        stats.put("criticalMemoryMode", criticalMemoryMode.get());
        stats.put("consecutiveHighMemory", consecutiveHighMemory.get());
        stats.put("consecutiveLowMemory", consecutiveLowMemory.get());
        
        // Add system memory info if available
        try {
            com.sun.management.OperatingSystemMXBean osBean = 
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long totalMemoryBytes = osBean.getTotalMemorySize();
            long freeMemoryBytes = osBean.getFreeMemorySize();
            long usedMemoryBytes = totalMemoryBytes - freeMemoryBytes;
            double systemMemoryUsagePercent = (double) usedMemoryBytes / totalMemoryBytes * 100;
            
            stats.put("systemTotalBytes", totalMemoryBytes);
            stats.put("systemFreeBytes", freeMemoryBytes);
            stats.put("systemUsedBytes", usedMemoryBytes);
            stats.put("systemUsagePercent", systemMemoryUsagePercent);
        } catch (Exception e) {
            log.debug("Could not get system memory info: {}", e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Periodically check memory usage and update memory mode flags
     */
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void checkMemoryUsage() {
        double memoryUsagePercent = getMemoryUsagePercent();
        
        // Check for critical memory
        if (memoryUsagePercent >= criticalMemoryThreshold) {
            if (!criticalMemoryMode.get()) {
                log.warn("Entering critical memory mode: usage={}%", String.format("%.1f", memoryUsagePercent));
                criticalMemoryMode.set(true);
                highMemoryMode.set(true);
                consecutiveHighMemory.incrementAndGet();
                consecutiveLowMemory.set(0);
                
                // Force garbage collection in critical mode
                System.gc();
            }
        } 
        // Check for high memory
        else if (memoryUsagePercent >= highMemoryThreshold) {
            if (!highMemoryMode.get()) {
                log.info("Entering high memory mode: usage={}%", String.format("%.1f", memoryUsagePercent));
                highMemoryMode.set(true);
                consecutiveHighMemory.incrementAndGet();
                consecutiveLowMemory.set(0);
            }
            
            // Exit critical mode if we're below the critical threshold
            if (criticalMemoryMode.get()) {
                log.info("Exiting critical memory mode: usage={}%", String.format("%.1f", memoryUsagePercent));
                criticalMemoryMode.set(false);
            }
        } 
        // Check for recovery
        else if (memoryUsagePercent <= recoveryMemoryThreshold) {
            consecutiveLowMemory.incrementAndGet();
            
            // Only exit high/critical memory mode after consecutive low readings
            if (consecutiveLowMemory.get() >= 2) {
                if (highMemoryMode.get() || criticalMemoryMode.get()) {
                    log.info("Exiting high/critical memory mode: usage={}%", String.format("%.1f", memoryUsagePercent));
                    highMemoryMode.set(false);
                    criticalMemoryMode.set(false);
                    consecutiveHighMemory.set(0);
                }
            }
        }
        
        // Log memory stats periodically
        if (log.isDebugEnabled()) {
            log.debug("Memory usage: {}%, high mode: {}, critical mode: {}", 
                    String.format("%.1f", memoryUsagePercent),
                    highMemoryMode.get(),
                    criticalMemoryMode.get());
        }
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
} 