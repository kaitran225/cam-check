package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for implementing delta encoding for video frames
 * Delta encoding only transmits the changed portions of frames to reduce bandwidth
 */
@Service
@Slf4j
public class DeltaEncodingService {

    @Value("${camcheck.media.delta-encoding.enabled:true}")
    private boolean deltaEncodingEnabled;
    
    @Value("${camcheck.media.delta-encoding.threshold:10}")
    private int changeThreshold;
    
    @Value("${camcheck.media.delta-encoding.block-size:16}")
    private int blockSize;
    
    @Value("${camcheck.media.delta-encoding.keyframe-interval:30}")
    private int keyframeInterval;
    
    // Store previous frames for each connection
    private final Map<String, FrameData> previousFrames = new ConcurrentHashMap<>();
    
    // Store frame counters for each connection
    private final Map<String, Integer> frameCounters = new ConcurrentHashMap<>();

    /**
     * Process a frame using delta encoding
     * @param base64Image Base64 encoded image data
     * @param connectionId Unique identifier for the connection
     * @return Processed frame data with delta encoding metadata
     * @throws IOException If processing fails
     */
    public DeltaFrame processFrame(String base64Image, String connectionId) throws IOException {
        if (!deltaEncodingEnabled) {
            return new DeltaFrame(base64Image, false, 0);
        }
        
        // Extract actual base64 data if it contains the data URL prefix
        String base64Data = base64Image;
        if (base64Image.contains(",")) {
            base64Data = base64Image.split(",")[1];
        }
        
        // Decode base64 to binary
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        
        // Read the image
        ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
        BufferedImage currentImage = ImageIO.read(bis);
        bis.close();
        
        if (currentImage == null) {
            throw new IOException("Could not read image data");
        }
        
        // Get or increment frame counter
        int frameCount = frameCounters.getOrDefault(connectionId, 0) + 1;
        frameCounters.put(connectionId, frameCount);
        
        // Check if we should send a keyframe
        boolean isKeyframe = frameCount % keyframeInterval == 0;
        
        if (isKeyframe || !previousFrames.containsKey(connectionId)) {
            // Store the current frame as the previous frame for next time
            storeFrame(connectionId, currentImage, base64Image);
            
            // Return the full frame as a keyframe
            return new DeltaFrame(base64Image, true, 100);
        }
        
        // Get the previous frame
        FrameData prevFrameData = previousFrames.get(connectionId);
        BufferedImage prevImage = prevFrameData.getImage();
        
        // Compare frames to find changed blocks
        DeltaResult deltaResult = calculateDelta(prevImage, currentImage);
        
        // If changes are minimal, return a special "no change" delta
        if (deltaResult.getChangedBlocksPercentage() < 5) {
            return new DeltaFrame("NO_CHANGE", false, 0);
        }
        
        // If changes are substantial (> 70% of blocks), send a full keyframe instead
        if (deltaResult.getChangedBlocksPercentage() > 70) {
            // Store the current frame as the previous frame for next time
            storeFrame(connectionId, currentImage, base64Image);
            
            // Return the full frame as a keyframe
            return new DeltaFrame(base64Image, true, 100);
        }
        
        // Create a delta frame containing only the changed blocks
        BufferedImage deltaImage = createDeltaImage(prevImage, currentImage, deltaResult);
        
        // Convert delta image to base64
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(deltaImage, "jpeg", bos);
        byte[] deltaBytes = bos.toByteArray();
        bos.close();
        
        String base64Delta = Base64.getEncoder().encodeToString(deltaBytes);
        
        // Store the current frame as the previous frame for next time
        storeFrame(connectionId, currentImage, base64Image);
        
        log.debug("Delta encoding for {}: changed blocks={}%, data reduction={}%", 
                connectionId, deltaResult.getChangedBlocksPercentage(),
                Math.round((1 - (double) deltaBytes.length / imageBytes.length) * 100));
        
        return new DeltaFrame(base64Delta, false, deltaResult.getChangedBlocksPercentage());
    }
    
    /**
     * Calculate delta between two frames
     * @param prevImage Previous frame
     * @param currentImage Current frame
     * @return Delta result containing changed blocks information
     */
    private DeltaResult calculateDelta(BufferedImage prevImage, BufferedImage currentImage) {
        int width = prevImage.getWidth();
        int height = prevImage.getHeight();
        
        // Calculate number of blocks
        int blocksX = (width + blockSize - 1) / blockSize;
        int blocksY = (height + blockSize - 1) / blockSize;
        int totalBlocks = blocksX * blocksY;
        
        // Create array to track changed blocks
        boolean[][] changedBlocks = new boolean[blocksX][blocksY];
        int changedBlocksCount = 0;
        
        // Compare blocks
        for (int blockY = 0; blockY < blocksY; blockY++) {
            for (int blockX = 0; blockX < blocksX; blockX++) {
                int startX = blockX * blockSize;
                int startY = blockY * blockSize;
                int endX = Math.min(startX + blockSize, width);
                int endY = Math.min(startY + blockSize, height);
                
                // Check if this block has changed
                if (hasBlockChanged(prevImage, currentImage, startX, startY, endX, endY)) {
                    changedBlocks[blockX][blockY] = true;
                    changedBlocksCount++;
                }
            }
        }
        
        // Calculate percentage of changed blocks
        int changedBlocksPercentage = (changedBlocksCount * 100) / totalBlocks;
        
        return new DeltaResult(changedBlocks, blocksX, blocksY, changedBlocksPercentage);
    }
    
    /**
     * Check if a block has changed between frames
     * @param prevImage Previous frame
     * @param currentImage Current frame
     * @param startX Start X coordinate
     * @param startY Start Y coordinate
     * @param endX End X coordinate
     * @param endY End Y coordinate
     * @return True if the block has changed significantly
     */
    private boolean hasBlockChanged(BufferedImage prevImage, BufferedImage currentImage, 
                                   int startX, int startY, int endX, int endY) {
        int diffCount = 0;
        int pixelsChecked = 0;
        
        // Sample the block (check every 2nd pixel for performance)
        for (int y = startY; y < endY; y += 2) {
            for (int x = startX; x < endX; x += 2) {
                int prevRGB = prevImage.getRGB(x, y);
                int currRGB = currentImage.getRGB(x, y);
                
                // Extract RGB components
                int prevR = (prevRGB >> 16) & 0xFF;
                int prevG = (prevRGB >> 8) & 0xFF;
                int prevB = prevRGB & 0xFF;
                
                int currR = (currRGB >> 16) & 0xFF;
                int currG = (currRGB >> 8) & 0xFF;
                int currB = currRGB & 0xFF;
                
                // Calculate difference
                int diffR = Math.abs(prevR - currR);
                int diffG = Math.abs(prevG - currG);
                int diffB = Math.abs(prevB - currB);
                
                // If any channel has changed significantly, count as changed pixel
                if (diffR > changeThreshold || diffG > changeThreshold || diffB > changeThreshold) {
                    diffCount++;
                }
                
                pixelsChecked++;
            }
        }
        
        // Block has changed if more than 10% of sampled pixels have changed
        return (diffCount * 100 / pixelsChecked) > 10;
    }
    
    /**
     * Create a delta image containing only the changed blocks
     * @param prevImage Previous frame
     * @param currentImage Current frame
     * @param deltaResult Delta result with changed blocks information
     * @return Delta image
     */
    private BufferedImage createDeltaImage(BufferedImage prevImage, BufferedImage currentImage, DeltaResult deltaResult) {
        int width = prevImage.getWidth();
        int height = prevImage.getHeight();
        
        // Create a new image with the same dimensions
        BufferedImage deltaImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = deltaImage.createGraphics();
        
        // Start with the previous image
        g.drawImage(prevImage, 0, 0, null);
        
        // Draw only the changed blocks from the current image
        for (int blockY = 0; blockY < deltaResult.getBlocksY(); blockY++) {
            for (int blockX = 0; blockX < deltaResult.getBlocksX(); blockX++) {
                if (deltaResult.getChangedBlocks()[blockX][blockY]) {
                    int startX = blockX * blockSize;
                    int startY = blockY * blockSize;
                    int endX = Math.min(startX + blockSize, width);
                    int endY = Math.min(startY + blockSize, height);
                    
                    // Copy the block from current image to delta image
                    BufferedImage blockImage = currentImage.getSubimage(startX, startY, endX - startX, endY - startY);
                    g.drawImage(blockImage, startX, startY, null);
                }
            }
        }
        
        g.dispose();
        return deltaImage;
    }
    
    /**
     * Store a frame for future delta calculations
     * @param connectionId Connection identifier
     * @param image Frame image
     * @param base64Data Base64 encoded frame data
     */
    private void storeFrame(String connectionId, BufferedImage image, String base64Data) {
        previousFrames.put(connectionId, new FrameData(image, base64Data));
    }
    
    /**
     * Reset delta encoding for a connection
     * @param connectionId Connection identifier
     */
    public void resetDeltaEncoding(String connectionId) {
        previousFrames.remove(connectionId);
        frameCounters.remove(connectionId);
        log.debug("Reset delta encoding for {}", connectionId);
    }
    
    /**
     * Get the previous frame for a connection
     * @param connectionId Connection identifier
     * @return Previous frame data or null if not available
     */
    public String getPreviousFrame(String connectionId) {
        FrameData frameData = previousFrames.get(connectionId);
        return frameData != null ? frameData.getBase64Data() : null;
    }
    
    /**
     * Inner class to store frame data
     */
    private static class FrameData {
        private final BufferedImage image;
        private final String base64Data;
        
        public FrameData(BufferedImage image, String base64Data) {
            this.image = image;
            this.base64Data = base64Data;
        }
        
        public BufferedImage getImage() {
            return image;
        }
        
        public String getBase64Data() {
            return base64Data;
        }
    }
    
    /**
     * Inner class to store delta calculation results
     */
    private static class DeltaResult {
        private final boolean[][] changedBlocks;
        private final int blocksX;
        private final int blocksY;
        private final int changedBlocksPercentage;
        
        public DeltaResult(boolean[][] changedBlocks, int blocksX, int blocksY, int changedBlocksPercentage) {
            this.changedBlocks = changedBlocks;
            this.blocksX = blocksX;
            this.blocksY = blocksY;
            this.changedBlocksPercentage = changedBlocksPercentage;
        }
        
        public boolean[][] getChangedBlocks() {
            return changedBlocks;
        }
        
        public int getBlocksX() {
            return blocksX;
        }
        
        public int getBlocksY() {
            return blocksY;
        }
        
        public int getChangedBlocksPercentage() {
            return changedBlocksPercentage;
        }
    }
    
    /**
     * Class representing a delta-encoded frame
     */
    public static class DeltaFrame {
        private final String base64Data;
        private final boolean isKeyframe;
        private final int changedPercentage;
        
        public DeltaFrame(String base64Data, boolean isKeyframe, int changedPercentage) {
            this.base64Data = base64Data;
            this.isKeyframe = isKeyframe;
            this.changedPercentage = changedPercentage;
        }
        
        public String getBase64Data() {
            return base64Data;
        }
        
        public boolean isKeyframe() {
            return isKeyframe;
        }
        
        public int getChangedPercentage() {
            return changedPercentage;
        }
        
        public boolean isNoChangeFrame() {
            return "NO_CHANGE".equals(base64Data);
        }
    }
} 