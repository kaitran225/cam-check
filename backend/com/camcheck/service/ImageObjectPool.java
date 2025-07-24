package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Object pool for BufferedImage objects to reduce GC pressure
 * This helps reduce memory fragmentation and GC pauses by reusing image objects
 */
@Component
@Slf4j
public class ImageObjectPool {

    @Value("${camcheck.media.pool.enabled:true}")
    private boolean poolEnabled;
    
    @Value("${camcheck.media.pool.max-size:5}")
    private int maxPoolSize;
    
    @Value("${camcheck.media.pool.max-age-ms:30000}")
    private long maxAgeMs;
    
    @Value("${LOW_RESOURCE_MODE:false}")
    private boolean lowResourceMode;
    
    // Maps dimension key to a pool of images with those dimensions
    private final Map<String, ConcurrentImageQueue> imagePools = new ConcurrentHashMap<>();
    
    // Counter for monitoring
    private final AtomicInteger totalBorrowed = new AtomicInteger(0);
    private final AtomicInteger totalReturned = new AtomicInteger(0);
    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicInteger totalDiscarded = new AtomicInteger(0);
    
    @PostConstruct
    public void init() {
        if (lowResourceMode) {
            // Further reduce pool size in low resource mode
            maxPoolSize = Math.min(maxPoolSize, 3);
            log.info("ImageObjectPool initialized with reduced max size: {}", maxPoolSize);
        }
    }
    
    /**
     * Borrow an image from the pool or create a new one if none available
     * 
     * @param width Image width
     * @param height Image height
     * @param imageType Image type (e.g., BufferedImage.TYPE_INT_RGB)
     * @return A BufferedImage object
     */
    public BufferedImage borrowImage(int width, int height, int imageType) {
        if (!poolEnabled) {
            totalCreated.incrementAndGet();
            return new BufferedImage(width, height, imageType);
        }
        
        // In low resource mode, limit image sizes
        if (lowResourceMode) {
            // Limit maximum dimensions to save memory
            int maxDimension = 800; // Max 800x800 in low resource mode
            if (width > maxDimension || height > maxDimension) {
                // Scale down while maintaining aspect ratio
                if (width > height) {
                    height = (int) (height * ((double) maxDimension / width));
                    width = maxDimension;
                } else {
                    width = (int) (width * ((double) maxDimension / height));
                    height = maxDimension;
                }
                log.debug("Reduced image dimensions to {}x{} to save memory", width, height);
            }
        }
        
        String key = getKey(width, height, imageType);
        ConcurrentImageQueue queue = imagePools.computeIfAbsent(key, k -> new ConcurrentImageQueue(maxPoolSize));
        
        BufferedImage image = queue.poll();
        if (image == null) {
            // No image in pool, create a new one
            image = new BufferedImage(width, height, imageType);
            totalCreated.incrementAndGet();
        }
        
        totalBorrowed.incrementAndGet();
        return image;
    }
    
    /**
     * Return an image to the pool for reuse
     * 
     * @param image The image to return
     */
    public void returnImage(BufferedImage image) {
        if (!poolEnabled || image == null) {
            return;
        }
        
        // Clear the image (fill with transparent black)
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            int[] pixels = new int[image.getWidth()];
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(0, y, image.getWidth(), 1, pixels, 0, image.getWidth());
            }
        }
        
        // In low resource mode, be more aggressive about discarding images
        if (lowResourceMode && (totalReturned.get() % 5 == 0)) {
            // Discard every 5th image to prevent pool growth
            totalDiscarded.incrementAndGet();
            return;
        }
        
        String key = getKey(image.getWidth(), image.getHeight(), image.getType());
        ConcurrentImageQueue queue = imagePools.computeIfAbsent(key, k -> new ConcurrentImageQueue(maxPoolSize));
        
        boolean added = queue.offer(image, System.currentTimeMillis());
        if (added) {
            totalReturned.incrementAndGet();
        } else {
            totalDiscarded.incrementAndGet();
        }
    }
    
    /**
     * Clean up old images from the pool
     */
    public void cleanUp() {
        if (!poolEnabled) {
            return;
        }
        
        long now = System.currentTimeMillis();
        int removed = 0;
        
        // In low resource mode, use a shorter age threshold
        long ageThreshold = lowResourceMode ? maxAgeMs / 2 : maxAgeMs;
        
        for (ConcurrentImageQueue queue : imagePools.values()) {
            removed += queue.removeOld(now - ageThreshold);
        }
        
        // In low resource mode, limit the number of pool types
        if (lowResourceMode && imagePools.size() > 5) {
            // Keep only the 5 most recently used pools
            List<Map.Entry<String, ConcurrentImageQueue>> entries = new ArrayList<>(imagePools.entrySet());
            entries.sort((a, b) -> Long.compare(b.getValue().getLastAccessTime(), a.getValue().getLastAccessTime()));
            
            for (int i = 5; i < entries.size(); i++) {
                imagePools.remove(entries.get(i).getKey());
                removed += entries.get(i).getValue().size();
            }
        }
        
        if (removed > 0) {
            log.debug("Removed {} stale images from pool", removed);
            totalDiscarded.addAndGet(removed);
        }
    }
    
    /**
     * Get pool statistics
     * 
     * @return Statistics about the image pool
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("enabled", poolEnabled);
        stats.put("poolCount", imagePools.size());
        stats.put("totalBorrowed", totalBorrowed.get());
        stats.put("totalReturned", totalReturned.get());
        stats.put("totalCreated", totalCreated.get());
        stats.put("totalDiscarded", totalDiscarded.get());
        
        int totalPooled = 0;
        for (ConcurrentImageQueue queue : imagePools.values()) {
            totalPooled += queue.size();
        }
        stats.put("currentPoolSize", totalPooled);
        
        // Calculate hit rate
        int totalRequests = totalBorrowed.get();
        int hits = totalBorrowed.get() - totalCreated.get();
        double hitRate = totalRequests > 0 ? (double) hits / totalRequests : 0;
        stats.put("hitRate", hitRate);
        
        return stats;
    }
    
    /**
     * Generate a key for the image dimensions and type
     */
    private String getKey(int width, int height, int imageType) {
        return width + "x" + height + ":" + imageType;
    }
    
    /**
     * Thread-safe queue with timestamp tracking for pooled images
     */
    private static class ConcurrentImageQueue {
        private final Map<BufferedImage, Long> imageTimestamps = new ConcurrentHashMap<>();
        private final int maxSize;
        private long lastAccessTime = System.currentTimeMillis();
        
        public ConcurrentImageQueue(int maxSize) {
            this.maxSize = maxSize;
        }
        
        public BufferedImage poll() {
            if (imageTimestamps.isEmpty()) {
                return null;
            }
            
            // Get any image from the map
            BufferedImage image = imageTimestamps.keySet().iterator().next();
            if (image != null) {
                imageTimestamps.remove(image);
                lastAccessTime = System.currentTimeMillis();
            }
            
            return image;
        }
        
        public boolean offer(BufferedImage image, long timestamp) {
            if (imageTimestamps.size() >= maxSize) {
                return false;
            }
            
            imageTimestamps.put(image, timestamp);
            lastAccessTime = System.currentTimeMillis();
            return true;
        }
        
        public int size() {
            return imageTimestamps.size();
        }
        
        public int removeOld(long cutoffTime) {
            int count = 0;
            for (Map.Entry<BufferedImage, Long> entry : imageTimestamps.entrySet()) {
                if (entry.getValue() < cutoffTime) {
                    imageTimestamps.remove(entry.getKey());
                    count++;
                }
            }
            return count;
        }
        
        public long getLastAccessTime() {
            return lastAccessTime;
        }
    }
} 