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

/**
 * Service for throttling requests to prevent memory spikes
 */
@Service
@Slf4j
public class RequestThrottleService {

    @Value("${camcheck.throttle.enabled:true}")
    private boolean throttleEnabled;
    
    @Value("${camcheck.throttle.max-concurrent-requests:5}")
    private int maxConcurrentRequests;
    
    @Value("${camcheck.throttle.timeout-ms:5000}")
    private long timeoutMs;
    
    private final MemoryMonitorService memoryMonitorService;
    private final Semaphore semaphore;
    
    // Counters for monitoring
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger rejectedRequests = new AtomicInteger(0);
    private final AtomicInteger throttledRequests = new AtomicInteger(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    
    @Autowired
    public RequestThrottleService(MemoryMonitorService memoryMonitorService) {
        this.memoryMonitorService = memoryMonitorService;
        this.semaphore = new Semaphore(maxConcurrentRequests, true);
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
        
        // If memory is critical, reject immediately
        if (memoryMonitorService.isCriticalMemoryMode()) {
            rejectedRequests.incrementAndGet();
            log.warn("Request rejected due to critical memory mode");
            return false;
        }
        
        // Determine available permits based on memory usage
        int availablePermits = getAvailablePermits();
        
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
     * Get the number of available permits based on memory usage
     * 
     * @return Available permits
     */
    private int getAvailablePermits() {
        if (memoryMonitorService.isHighMemoryMode()) {
            // Reduce permits by half in high memory mode
            return Math.max(1, maxConcurrentRequests / 2);
        } else {
            return maxConcurrentRequests;
        }
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
        stats.put("availablePermits", semaphore.availablePermits());
        stats.put("queueLength", semaphore.getQueueLength());
        stats.put("totalRequests", totalRequests.get());
        stats.put("successfulRequests", successfulRequests.get());
        stats.put("throttledRequests", throttledRequests.get());
        stats.put("rejectedRequests", rejectedRequests.get());
        
        // Calculate rejection rate
        int total = totalRequests.get();
        double rejectionRate = total > 0 ? 
                (double) (throttledRequests.get() + rejectedRequests.get()) / total : 0;
        stats.put("rejectionRate", rejectionRate);
        
        return stats;
    }
} 