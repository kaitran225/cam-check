package com.camcheck.service;

import com.camcheck.util.CompactByteArray;
import com.camcheck.util.MemoryEfficientCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/**
 * Memory-efficient frame processor service
 * Uses compact data structures and weak references to minimize memory usage
 */
@Service
@Slf4j
public class MemoryEfficientFrameProcessor {

    @Value("${frame.processor.cache.size:10}")
    private int cacheSize;
    
    @Value("${frame.processor.cache.expire-ms:10000}")
    private int cacheExpireMs;
    
    @Value("${frame.processor.max-concurrent:3}")
    private int maxConcurrentProcessing;
    
    @Value("${LOW_RESOURCE_MODE:false}")
    private boolean lowResourceMode;
    
    private final ImageObjectPool imageObjectPool;
    private final DenoiseService denoiseService;
    private final DynamicMemoryOptimizer memoryOptimizer;
    
    private MemoryEfficientCache<String, CompactByteArray> frameCache;
    private final Semaphore processingSemaphore;
    private final AtomicInteger processedFrames = new AtomicInteger(0);
    private final AtomicInteger droppedFrames = new AtomicInteger(0);
    
    @Autowired
    public MemoryEfficientFrameProcessor(
            ImageObjectPool imageObjectPool,
            DenoiseService denoiseService,
            DynamicMemoryOptimizer memoryOptimizer) {
        this.imageObjectPool = imageObjectPool;
        this.denoiseService = denoiseService;
        this.memoryOptimizer = memoryOptimizer;
        this.processingSemaphore = new Semaphore(maxConcurrentProcessing);
    }
    
    @PostConstruct
    public void init() {
        if (lowResourceMode) {
            // Reduce cache size in low resource mode
            cacheSize = Math.min(cacheSize, 5);
            cacheExpireMs = Math.min(cacheExpireMs, 5000);
        }
        
        frameCache = new MemoryEfficientCache<>(cacheSize, cacheExpireMs, "frameCache");
        log.info("Memory-efficient frame processor initialized with cacheSize={}, expireMs={}, maxConcurrent={}",
                cacheSize, cacheExpireMs, maxConcurrentProcessing);
    }
    
    /**
     * Process a frame with memory efficiency
     * 
     * @param inputFrame The input frame as a Base64 string
     * @param options Processing options
     * @return The processed frame as a Base64 string
     */
    public String processFrame(String inputFrame, Map<String, Object> options) {
        // Skip processing if we're in critical memory mode
        if (memoryOptimizer.isCriticalMemory()) {
            droppedFrames.incrementAndGet();
            return inputFrame; // Return original frame
        }
        
        // Try to acquire a permit
        if (!processingSemaphore.tryAcquire()) {
            droppedFrames.incrementAndGet();
            return inputFrame; // Return original frame if we can't acquire a permit
        }
        
        try {
            // Generate cache key
            String cacheKey = generateCacheKey(inputFrame, options);
            
            // Check cache
            return frameCache.get(cacheKey, key -> {
                try {
                    // Process the frame
                    String processedFrame = doProcessFrame(inputFrame, options);
                    processedFrames.incrementAndGet();
                    
                    // Create compact byte array
                    return CompactByteArray.fromBase64(processedFrame);
                } catch (Exception e) {
                    log.warn("Error processing frame: {}", e.getMessage());
                    return CompactByteArray.fromBase64(inputFrame); // Return original frame on error
                }
            }).toBase64();
        } finally {
            processingSemaphore.release();
        }
    }
    
    /**
     * Process a frame with the given options
     * 
     * @param inputFrame The input frame as a Base64 string
     * @param options Processing options
     * @return The processed frame as a Base64 string
     * @throws IOException If an error occurs
     */
    private String doProcessFrame(String inputFrame, Map<String, Object> options) throws IOException {
        // Extract image format and base64 content
        String format = "jpeg"; // Default format
        String base64Content = inputFrame;
        
        if (inputFrame.startsWith("data:image/")) {
            int commaIndex = inputFrame.indexOf(',');
            if (commaIndex > 0) {
                String header = inputFrame.substring(0, commaIndex);
                if (header.contains("image/png")) {
                    format = "png";
                } else if (header.contains("image/webp")) {
                    format = "webp";
                }
                base64Content = inputFrame.substring(commaIndex + 1);
            }
        }
        
        // Decode base64
        byte[] imageBytes = Base64.getDecoder().decode(base64Content);
        
        // Read image
        BufferedImage image = null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
            image = ImageIO.read(bis);
        }
        
        if (image == null) {
            throw new IOException("Could not read image");
        }
        
        // Apply processing based on options
        BufferedImage processedImage = applyProcessing(image, options);
        
        // Encode processed image
        String processedBase64;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(processedImage, format, bos);
            processedBase64 = Base64.getEncoder().encodeToString(bos.toByteArray());
        }
        
        // Return processed image with data URL prefix
        return "data:image/" + format + ";base64," + processedBase64;
    }
    
    /**
     * Apply processing to an image based on options
     * 
     * @param image The input image
     * @param options Processing options
     * @return The processed image
     */
    private BufferedImage applyProcessing(BufferedImage image, Map<String, Object> options) {
        // Apply processing based on options
        BufferedImage processedImage = image;
        
        // Apply denoising if requested
        if (options.containsKey("denoise") && (Boolean) options.get("denoise")) {
            String method = options.containsKey("denoiseMethod") ? 
                    (String) options.get("denoiseMethod") : "fast-bilateral";
            double strength = options.containsKey("denoiseStrength") ? 
                    ((Number) options.get("denoiseStrength")).doubleValue() : 0.5;
            
            // Adjust strength based on memory pressure
            if (memoryOptimizer.isHighMemory()) {
                strength = Math.min(strength, 0.3); // Reduce strength when memory is high
            }
            
            try {
                // Apply denoising - convert to base64 string and back since that's what the service expects
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", bos);
                String base64Image = "data:image/png;base64," + 
                        Base64.getEncoder().encodeToString(bos.toByteArray());
                
                String denoisedBase64 = denoiseService.denoiseImage(base64Image, method, strength);
                
                // Convert back to BufferedImage
                String base64Content = denoisedBase64;
                if (denoisedBase64.startsWith("data:")) {
                    int commaIndex = denoisedBase64.indexOf(',');
                    if (commaIndex > 0) {
                        base64Content = denoisedBase64.substring(commaIndex + 1);
                    }
                }
                
                byte[] imageBytes = Base64.getDecoder().decode(base64Content);
                try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                    BufferedImage denoisedImage = ImageIO.read(bis);
                    if (denoisedImage != null) {
                        processedImage = denoisedImage;
                    }
                }
            } catch (Exception e) {
                log.warn("Error applying denoise: {}", e.getMessage());
            }
        }
        
        // Apply scaling if requested
        if (options.containsKey("scale") && options.containsKey("scaleFactor")) {
            double scaleFactor = ((Number) options.get("scaleFactor")).doubleValue();
            
            // Adjust scale factor based on memory pressure
            if (memoryOptimizer.isHighMemory()) {
                scaleFactor = Math.min(scaleFactor, 0.75); // Reduce scale factor when memory is high
            }
            
            try {
                // Apply scaling
                int newWidth = (int) (image.getWidth() * scaleFactor);
                int newHeight = (int) (image.getHeight() * scaleFactor);
                
                // Borrow image from pool
                BufferedImage scaledImage = imageObjectPool.borrowImage(newWidth, newHeight, image.getType());
                
                // Scale image
                java.awt.Graphics2D g = scaledImage.createGraphics();
                g.drawImage(image, 0, 0, newWidth, newHeight, null);
                g.dispose();
                
                // Return original image to pool if it came from there
                imageObjectPool.returnImage(processedImage);
                
                processedImage = scaledImage;
            } catch (Exception e) {
                log.warn("Error applying scaling: {}", e.getMessage());
            }
        }
        
        return processedImage;
    }
    
    /**
     * Generate a cache key for the given frame and options
     * 
     * @param inputFrame The input frame
     * @param options The processing options
     * @return The cache key
     */
    private String generateCacheKey(String inputFrame, Map<String, Object> options) {
        // Use a hash of the frame content and options as the cache key
        StringBuilder keyBuilder = new StringBuilder();
        
        // Add a hash of the frame content (first 100 chars)
        String frameHash = String.valueOf(inputFrame.substring(0, Math.min(100, inputFrame.length())).hashCode());
        keyBuilder.append(frameHash);
        
        // Add options
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            keyBuilder.append(":").append(entry.getKey()).append("=").append(entry.getValue());
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * Clean up the cache periodically
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanUp() {
        // Nothing to do here, the cache cleans itself up
        log.debug("Frame processor stats: processed={}, dropped={}, cacheSize={}",
                processedFrames.get(), droppedFrames.get(), frameCache.size());
    }
    
    /**
     * Get statistics about the frame processor
     * 
     * @return Statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("processedFrames", processedFrames.get());
        stats.put("droppedFrames", droppedFrames.get());
        stats.put("cacheStats", frameCache.getStats());
        stats.put("availablePermits", processingSemaphore.availablePermits());
        stats.put("maxConcurrentProcessing", maxConcurrentProcessing);
        return stats;
    }
} 