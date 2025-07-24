package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for caching processed frames to avoid redundant processing
 */
@Service
@Slf4j
public class FrameCacheService {

    @Value("${camcheck.media.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${camcheck.media.cache.max-size:100}")
    private int maxCacheSize;
    
    @Value("${camcheck.media.cache.max-age-ms:30000}")
    private long maxAgeMs;
    
    @Value("${LOW_RESOURCE_MODE:false}")
    private boolean lowResourceMode;
    
    // Cache of processed frames: hash -> CachedFrame
    private final Map<String, CachedFrame> frameCache = new ConcurrentHashMap<>();
    
    // Performance counters
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    
    @PostConstruct
    public void init() {
        if (lowResourceMode) {
            // Reduce cache size in low resource mode
            maxCacheSize = Math.min(maxCacheSize, 20);
            maxAgeMs = Math.min(maxAgeMs, 15000); // 15 seconds max
            log.info("FrameCacheService initialized with reduced settings: maxSize={}, maxAge={}ms", 
                    maxCacheSize, maxAgeMs);
        }
    }
    
    /**
     * Get a cached frame if available
     * 
     * @param inputFrame Input frame data
     * @param processingKey Processing parameters key
     * @return Cached frame or null if not found
     */
    public String getCachedFrame(String inputFrame, String processingKey) {
        if (!cacheEnabled) {
            cacheMisses.incrementAndGet();
            return null;
        }
        
        try {
            String hash = computeFrameHash(inputFrame, processingKey);
            CachedFrame cachedFrame = frameCache.get(hash);
            
            if (cachedFrame != null && !isExpired(cachedFrame)) {
                cachedFrame.updateLastAccess();
                cacheHits.incrementAndGet();
                return cachedFrame.getProcessedFrame();
            }
        } catch (Exception e) {
            log.warn("Error accessing frame cache: {}", e.getMessage());
        }
        
        cacheMisses.incrementAndGet();
        return null;
    }
    
    /**
     * Store a processed frame in the cache
     * 
     * @param inputFrame Input frame data
     * @param processingKey Processing parameters key
     * @param processedFrame Processed frame data
     */
    public void cacheProcessedFrame(String inputFrame, String processingKey, String processedFrame) {
        if (!cacheEnabled) {
            return;
        }
        
        try {
            // In low resource mode, be more selective about what we cache
            if (lowResourceMode) {
                // Only cache every other frame to save memory
                if (System.nanoTime() % 2 == 0) {
                    return;
                }
                
                // Skip caching very large frames
                if (processedFrame.length() > 100000) { // 100KB limit
                    return;
                }
            }
            
            String hash = computeFrameHash(inputFrame, processingKey);
            
            // Check if we need to evict entries
            if (frameCache.size() >= maxCacheSize) {
                evictOldestEntry();
            }
            
            frameCache.put(hash, new CachedFrame(processedFrame));
        } catch (Exception e) {
            log.warn("Error storing frame in cache: {}", e.getMessage());
        }
    }
    
    /**
     * Compute a hash for the frame and processing parameters
     * 
     * @param inputFrame Input frame data
     * @param processingKey Processing parameters key
     * @return Hash string
     * @throws NoSuchAlgorithmException If SHA-256 is not available
     */
    private String computeFrameHash(String inputFrame, String processingKey) throws NoSuchAlgorithmException {
        // For performance, we'll hash only a sample of the frame data
        // This is a trade-off between accuracy and performance
        String sampleData;
        
        // In low resource mode, use an even smaller sample
        int sampleSize = lowResourceMode ? 200 : 500;
        
        if (inputFrame.length() > sampleSize * 2) {
            // Sample from beginning and end
            sampleData = inputFrame.substring(0, sampleSize) + 
                         inputFrame.substring(inputFrame.length() - sampleSize);
        } else {
            sampleData = inputFrame;
        }
        
        // Combine with processing key
        String dataToHash = sampleData + processingKey;
        
        // Use faster MD5 in low resource mode instead of SHA-256
        MessageDigest digest = MessageDigest.getInstance(lowResourceMode ? "MD5" : "SHA-256");
        byte[] hashBytes = digest.digest(dataToHash.getBytes());
        
        // Convert to hex string
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }
    
    /**
     * Check if a cached frame has expired
     * 
     * @param cachedFrame Cached frame
     * @return True if expired
     */
    private boolean isExpired(CachedFrame cachedFrame) {
        return System.currentTimeMillis() - cachedFrame.getLastAccess() > maxAgeMs;
    }
    
    /**
     * Evict the oldest entry from the cache
     */
    private void evictOldestEntry() {
        if (frameCache.isEmpty()) {
            return;
        }
        
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<String, CachedFrame> entry : frameCache.entrySet()) {
            if (entry.getValue().getLastAccess() < oldestTime) {
                oldestTime = entry.getValue().getLastAccess();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            frameCache.remove(oldestKey);
        }
    }
    
    /**
     * Clean up expired cache entries
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanUpCache() {
        if (!cacheEnabled) {
            return;
        }
        
        int removedCount = 0;
        for (Map.Entry<String, CachedFrame> entry : frameCache.entrySet()) {
            if (isExpired(entry.getValue())) {
                frameCache.remove(entry.getKey());
                removedCount++;
            }
        }
        
        // In low resource mode, be more aggressive
        if (lowResourceMode && frameCache.size() > maxCacheSize / 2) {
            // Keep only half of max size in low resource mode
            int toRemove = frameCache.size() - maxCacheSize / 2;
            if (toRemove > 0) {
                // Find oldest entries
                Map.Entry<String, CachedFrame>[] entries = frameCache.entrySet().toArray(new Map.Entry[0]);
                java.util.Arrays.sort(entries, (a, b) -> 
                    Long.compare(a.getValue().getLastAccess(), b.getValue().getLastAccess()));
                
                // Remove oldest entries
                for (int i = 0; i < Math.min(toRemove, entries.length); i++) {
                    frameCache.remove(entries[i].getKey());
                    removedCount++;
                }
            }
        }
        
        if (removedCount > 0) {
            log.debug("Removed {} expired entries from frame cache", removedCount);
        }
    }
    
    /**
     * Get cache statistics
     * 
     * @return Statistics about the frame cache
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("enabled", cacheEnabled);
        stats.put("size", frameCache.size());
        stats.put("maxSize", maxCacheSize);
        stats.put("hits", cacheHits.get());
        stats.put("misses", cacheMisses.get());
        
        // Calculate hit rate
        int totalRequests = cacheHits.get() + cacheMisses.get();
        double hitRate = totalRequests > 0 ? (double) cacheHits.get() / totalRequests : 0;
        stats.put("hitRate", hitRate);
        
        return stats;
    }
    
    /**
     * Class representing a cached frame
     */
    private static class CachedFrame {
        private final String processedFrame;
        private long lastAccess;
        
        public CachedFrame(String processedFrame) {
            this.processedFrame = processedFrame;
            this.lastAccess = System.currentTimeMillis();
        }
        
        public String getProcessedFrame() {
            return processedFrame;
        }
        
        public long getLastAccess() {
            return lastAccess;
        }
        
        public void updateLastAccess() {
            this.lastAccess = System.currentTimeMillis();
        }
    }
} 