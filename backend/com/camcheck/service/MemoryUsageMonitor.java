package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to monitor memory usage over time
 * Keeps a history of memory usage for analysis
 */
@Service
@Slf4j
public class MemoryUsageMonitor {

    @Value("${memory.monitor.enabled:true}")
    private boolean monitorEnabled;
    
    @Value("${memory.monitor.interval-ms:10000}")
    private int monitorIntervalMs;
    
    @Value("${memory.monitor.history-size:60}")
    private int historySize;
    
    private final MemoryMXBean memoryMXBean;
    private final List<MemoryPoolMXBean> memoryPoolMXBeans;
    private final List<GarbageCollectorMXBean> gcMXBeans;
    
    private final Deque<MemorySnapshot> history = new ArrayDeque<>();
    private final AtomicLong totalGcCount = new AtomicLong(0);
    private final AtomicLong totalGcTimeMs = new AtomicLong(0);
    private final AtomicLong lastGcCount = new AtomicLong(0);
    private final AtomicLong lastGcTimeMs = new AtomicLong(0);
    
    private long startTimeMs;
    
    public MemoryUsageMonitor() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        this.gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.startTimeMs = System.currentTimeMillis();
    }
    
    @PostConstruct
    public void init() {
        log.info("Memory usage monitor initialized with interval={}ms, historySize={}", 
                monitorIntervalMs, historySize);
    }
    
    /**
     * Take a memory snapshot at regular intervals
     */
    @Scheduled(fixedRateString = "${memory.monitor.interval-ms:10000}")
    public void takeSnapshot() {
        if (!monitorEnabled) {
            return;
        }
        
        // Create a new snapshot
        MemorySnapshot snapshot = createSnapshot();
        
        // Add to history
        synchronized (history) {
            history.addLast(snapshot);
            
            // Keep history size limited
            while (history.size() > historySize) {
                history.removeFirst();
            }
        }
        
        // Log memory usage periodically (every ~5 snapshots)
        if (history.size() % 5 == 0) {
            logMemoryUsage(snapshot);
        }
    }
    
    /**
     * Create a memory snapshot
     * 
     * @return The memory snapshot
     */
    private MemorySnapshot createSnapshot() {
        MemorySnapshot snapshot = new MemorySnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        
        // Heap memory
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        snapshot.heapUsed = heapUsage.getUsed() / (1024 * 1024);
        snapshot.heapCommitted = heapUsage.getCommitted() / (1024 * 1024);
        snapshot.heapMax = heapUsage.getMax() / (1024 * 1024);
        
        // Non-heap memory
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        snapshot.nonHeapUsed = nonHeapUsage.getUsed() / (1024 * 1024);
        
        // Memory pools
        snapshot.pools = new HashMap<>();
        for (MemoryPoolMXBean pool : memoryPoolMXBeans) {
            MemoryUsage usage = pool.getUsage();
            if (usage != null) {
                Map<String, Object> poolStats = new HashMap<>();
                poolStats.put("used", usage.getUsed() / (1024 * 1024));
                poolStats.put("committed", usage.getCommitted() / (1024 * 1024));
                poolStats.put("max", usage.getMax() / (1024 * 1024));
                snapshot.pools.put(pool.getName(), poolStats);
            }
        }
        
        // GC stats
        long gcCount = 0;
        long gcTimeMs = 0;
        for (GarbageCollectorMXBean gcBean : gcMXBeans) {
            gcCount += gcBean.getCollectionCount();
            gcTimeMs += gcBean.getCollectionTime();
        }
        
        snapshot.gcCount = gcCount - lastGcCount.get();
        snapshot.gcTimeMs = gcTimeMs - lastGcTimeMs.get();
        
        // Update totals and last values
        totalGcCount.addAndGet(snapshot.gcCount);
        totalGcTimeMs.addAndGet(snapshot.gcTimeMs);
        lastGcCount.set(gcCount);
        lastGcTimeMs.set(gcTimeMs);
        
        return snapshot;
    }
    
    /**
     * Log memory usage
     * 
     * @param snapshot The memory snapshot
     */
    private void logMemoryUsage(MemorySnapshot snapshot) {
        log.info("Memory usage: heap={}MB ({}% of max), non-heap={}MB, GC count={}, GC time={}ms",
                snapshot.heapUsed,
                snapshot.heapMax > 0 ? (snapshot.heapUsed * 100 / snapshot.heapMax) : 0,
                snapshot.nonHeapUsed,
                snapshot.gcCount,
                snapshot.gcTimeMs);
    }
    
    /**
     * Get memory usage statistics
     * 
     * @return Memory usage statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Current memory usage
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        stats.put("heapUsedMB", heapUsage.getUsed() / (1024 * 1024));
        stats.put("heapCommittedMB", heapUsage.getCommitted() / (1024 * 1024));
        stats.put("heapMaxMB", heapUsage.getMax() / (1024 * 1024));
        stats.put("heapUsagePercent", heapUsage.getMax() > 0 ? 
                (heapUsage.getUsed() * 100 / heapUsage.getMax()) : 0);
        
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        stats.put("nonHeapUsedMB", nonHeapUsage.getUsed() / (1024 * 1024));
        stats.put("nonHeapCommittedMB", nonHeapUsage.getCommitted() / (1024 * 1024));
        
        // GC stats
        stats.put("totalGcCount", totalGcCount.get());
        stats.put("totalGcTimeMs", totalGcTimeMs.get());
        
        // Runtime stats
        stats.put("upTimeMs", System.currentTimeMillis() - startTimeMs);
        stats.put("upTimeMinutes", (System.currentTimeMillis() - startTimeMs) / (60 * 1000));
        
        // Memory history
        synchronized (history) {
            stats.put("historySize", history.size());
            
            // Extract trends
            if (!history.isEmpty()) {
                Map<String, Object> trends = new HashMap<>();
                
                // Calculate min, max, avg for heap usage
                long minHeapUsed = Long.MAX_VALUE;
                long maxHeapUsed = 0;
                long totalHeapUsed = 0;
                
                // Calculate GC frequency
                long gcCount = 0;
                long gcTimeMs = 0;
                
                for (MemorySnapshot snapshot : history) {
                    minHeapUsed = Math.min(minHeapUsed, snapshot.heapUsed);
                    maxHeapUsed = Math.max(maxHeapUsed, snapshot.heapUsed);
                    totalHeapUsed += snapshot.heapUsed;
                    
                    gcCount += snapshot.gcCount;
                    gcTimeMs += snapshot.gcTimeMs;
                }
                
                trends.put("minHeapUsedMB", minHeapUsed);
                trends.put("maxHeapUsedMB", maxHeapUsed);
                trends.put("avgHeapUsedMB", history.size() > 0 ? totalHeapUsed / history.size() : 0);
                trends.put("gcFrequencyPerMinute", history.size() > 0 ? 
                        (gcCount * 60000) / (monitorIntervalMs * history.size()) : 0);
                trends.put("gcTimePerMinute", history.size() > 0 ? 
                        (gcTimeMs * 60000) / (monitorIntervalMs * history.size()) : 0);
                
                stats.put("trends", trends);
                
                // Include last snapshot
                MemorySnapshot lastSnapshot = history.getLast();
                Map<String, Object> lastStats = new HashMap<>();
                lastStats.put("timestamp", lastSnapshot.timestamp);
                lastStats.put("heapUsedMB", lastSnapshot.heapUsed);
                lastStats.put("nonHeapUsedMB", lastSnapshot.nonHeapUsed);
                lastStats.put("gcCount", lastSnapshot.gcCount);
                lastStats.put("gcTimeMs", lastSnapshot.gcTimeMs);
                
                stats.put("lastSnapshot", lastStats);
            }
        }
        
        return stats;
    }
    
    /**
     * Get memory usage history
     * 
     * @return Memory usage history
     */
    public List<Map<String, Object>> getHistory() {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        
        synchronized (history) {
            for (MemorySnapshot snapshot : history) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("timestamp", snapshot.timestamp);
                entry.put("heapUsedMB", snapshot.heapUsed);
                entry.put("heapCommittedMB", snapshot.heapCommitted);
                entry.put("heapMaxMB", snapshot.heapMax);
                entry.put("nonHeapUsedMB", snapshot.nonHeapUsed);
                entry.put("gcCount", snapshot.gcCount);
                entry.put("gcTimeMs", snapshot.gcTimeMs);
                result.add(entry);
            }
        }
        
        return result;
    }
    
    /**
     * Memory snapshot class
     */
    private static class MemorySnapshot {
        long timestamp;
        long heapUsed;
        long heapCommitted;
        long heapMax;
        long nonHeapUsed;
        Map<String, Map<String, Object>> pools;
        long gcCount;
        long gcTimeMs;
    }
} 