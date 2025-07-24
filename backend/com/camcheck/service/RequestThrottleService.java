package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for throttling requests to prevent CPU and memory spikes
 * Optimized for Render.com's single CPU environment
 */
@Service
@Slf4j
public class RequestThrottleService {

    @Value("${camcheck.throttle.enabled:true}")
    private boolean throttleEnabled;
    
    // Reduce concurrent requests for single CPU environment
    @Value("${camcheck.throttle.max-concurrent-requests:2}")
    private int maxConcurrentRequests;
    
    // Shorter timeout to prevent request pileup
    @Value("${camcheck.throttle.timeout-ms:3000}")
    private long timeoutMs;
    
    // Track CPU usage to dynamically adjust throttling
    @Value("${camcheck.throttle.cpu-threshold:0.8}")
    private double cpuThreshold;
    
    private final MemoryMonitorService memoryMonitorService;
    private final Semaphore semaphore;
    
    // Counters for monitoring
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger rejectedRequests = new AtomicInteger(0);
    private final AtomicInteger throttledRequests = new AtomicInteger(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicLong lastCpuCheck = new AtomicLong(0);
    private final AtomicInteger availablePermits = new AtomicInteger(2);
    
    // CPU usage tracking
    private double lastCpuUsage = 0.0;
    
    @Autowired
    public RequestThrottleService(MemoryMonitorService memoryMonitorService) {
        this.memoryMonitorService = memoryMonitorService;
        this.semaphore = new Semaphore(maxConcurrentRequests, true);
        this.availablePermits.set(maxConcurrentRequests);
        log.info("Request throttle initialized with max concurrent requests: {}", maxConcurrentRequests);
    }
    
    /**
     * Acquire a permit to process a request
     * 
     * @return True if the request can proceed, false if it should be rejected
     */
    public boolean acquirePermit() {
        if (!throttleEnabled) {
            successfulRequests.incrementAndGet();
            return true;
        }
        
        totalRequests.incrementAndGet();
        
        // Check CPU usage periodically
        checkCpuUsage();
        
        // If memory is critical, reject immediately
        if (memoryMonitorService.isCriticalMemoryMode()) {
            rejectedRequests.incrementAndGet();
            log.warn("Request rejected due to critical memory mode");
            return false;
        }
        
        // Determine available permits based on memory and CPU usage
        int permits = getAvailablePermits();
        
        // If we've reduced permits below current, drain some
        int currentPermits = semaphore.availablePermits();
        if (permits < currentPermits && currentPermits > 0) {
            semaphore.drainPermits();
            semaphore.release(permits);
        }
        
        try {
            // Try to acquire a permit with timeout
            boolean acquired = semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            
            if (acquired) {
                successfulRequests.incrementAndGet();
                return true;
            } else {
                throttledRequests.incrementAndGet();
                log.debug("Request throttled due to concurrent request limit");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rejectedRequests.incrementAndGet();
            log.warn("Request interrupted while waiting for permit");
            return false;
        }
    }
    
    /**
     * Release a permit after processing a request
     */
    public void releasePermit() {
        if (throttleEnabled) {
            semaphore.release();
        }
    }
    
    /**
     * Check CPU usage and adjust throttling accordingly
     */
    private void checkCpuUsage() {
        long now = System.currentTimeMillis();
        long lastCheck = lastCpuCheck.get();
        
        // Only check every 5 seconds to reduce overhead
        if (now - lastCheck > 5000) {
            if (lastCpuCheck.compareAndSet(lastCheck, now)) {
                try {
                    // Get CPU usage through JMX
                    com.sun.management.OperatingSystemMXBean osMBean = 
                            (com.sun.management.OperatingSystemMXBean) 
                            java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                    
                    lastCpuUsage = osMBean.getProcessCpuLoad();
                    
                    // Adjust available permits based on CPU usage
                    if (lastCpuUsage > cpuThreshold) {
                        // High CPU usage, reduce permits
                        availablePermits.set(Math.max(1, maxConcurrentRequests / 2));
                    } else {
                        // Normal CPU usage, restore permits
                        availablePermits.set(maxConcurrentRequests);
                    }
                } catch (Exception e) {
                    log.debug("Error checking CPU usage: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Get the number of available permits based on memory and CPU usage
     * 
     * @return Available permits
     */
    private int getAvailablePermits() {
        int permits = availablePermits.get();
        
        if (memoryMonitorService.isHighMemoryMode()) {
            // Reduce permits by half in high memory mode
            permits = Math.max(1, permits / 2);
        }
        
        if (lastCpuUsage > cpuThreshold) {
            // Reduce permits further if CPU usage is high
            permits = Math.max(1, permits / 2);
        }
        
        return permits;
    }
    
    /**
     * Get throttling statistics
     * 
     * @return Statistics about request throttling
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("enabled", throttleEnabled);
        stats.put("maxConcurrentRequests", maxConcurrentRequests);
        stats.put("currentMaxPermits", availablePermits.get());
        stats.put("availablePermits", semaphore.availablePermits());
        stats.put("queueLength", semaphore.getQueueLength());
        stats.put("totalRequests", totalRequests.get());
        stats.put("successfulRequests", successfulRequests.get());
        stats.put("throttledRequests", throttledRequests.get());
        stats.put("rejectedRequests", rejectedRequests.get());
        stats.put("lastCpuUsage", lastCpuUsage);
        
        // Calculate rejection rate
        int total = totalRequests.get();
        double rejectionRate = total > 0 ? 
                (double) (throttledRequests.get() + rejectedRequests.get()) / total : 0;
        stats.put("rejectionRate", rejectionRate);
        
        return stats;
    }
} 