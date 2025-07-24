package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Service for applying efficient denoising to camera frames
 */
@Service
@Slf4j
public class DenoiseService {

    @Value("${camcheck.media.denoise.enabled:true}")
    private boolean denoiseEnabled;

    @Value("${camcheck.media.denoise.strength:0.5}")
    private double defaultStrength;

    @Value("${camcheck.media.denoise.method:fast-bilateral}")
    private String defaultMethod;
    
    @Value("${camcheck.media.denoise.optimize-performance:true}")
    private boolean optimizePerformance;
    
    private final ImageObjectPool imagePool;
    
    @Autowired
    public DenoiseService(ImageObjectPool imagePool) {
        this.imagePool = imagePool;
    }

    /**
     * Apply denoising to an image with default parameters
     * 
     * @param base64Image Base64 encoded image data
     * @return Denoised image as Base64 string
     * @throws IOException If denoising fails
     */
    public String denoiseImage(String base64Image) throws IOException {
        return denoiseImage(base64Image, defaultMethod, defaultStrength);
    }

    /**
     * Apply denoising to an image with specified parameters
     * 
     * @param base64Image Base64 encoded image data
     * @param method Denoising method (gaussian, median, bilateral, fast-bilateral)
     * @param strength Denoising strength (0.0-1.0)
     * @return Denoised image as Base64 string
     * @throws IOException If denoising fails
     */
    public String denoiseImage(String base64Image, String method, double strength) throws IOException {
        if (!denoiseEnabled) {
            return base64Image;
        }

        // Extract actual base64 data if it contains the data URL prefix
        String base64Data = base64Image;
        String prefix = "";
        if (base64Image.contains(",")) {
            String[] parts = base64Image.split(",", 2);
            prefix = parts[0] + ",";
            base64Data = parts[1];
        }

        // Decode base64 to binary
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);

        // Read the image
        ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
        BufferedImage image = ImageIO.read(bis);
        bis.close();

        if (image == null) {
            throw new IOException("Could not read image data");
        }

        // Apply denoising based on method
        BufferedImage denoisedImage;
        
        // If we're in low memory mode, use faster but less memory-intensive methods
        if (optimizePerformance && (method.equals("bilateral") || method.equals("median"))) {
            log.debug("Using fast-bilateral filter instead of {} for memory optimization", method);
            method = "fast-bilateral";
        }
        
        switch (method.toLowerCase()) {
            case "gaussian":
                denoisedImage = applyGaussianBlur(image, strength);
                break;
            case "median":
                denoisedImage = applyMedianFilter(image, strength);
                break;
            case "bilateral":
                denoisedImage = applyBilateralFilter(image, strength);
                break;
            case "fast-bilateral":
            default:
                denoisedImage = applyFastBilateralFilter(image, strength);
                break;
        }
        
        // We're done with the source image, return it to the pool if it's the right type
        if (image.getType() == BufferedImage.TYPE_INT_RGB || image.getType() == BufferedImage.TYPE_INT_ARGB) {
            imagePool.returnImage(image);
        }

        // Convert back to base64
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(denoisedImage, "jpeg", bos);
        byte[] denoisedBytes = bos.toByteArray();
        bos.close();
        
        // Return denoised image to the pool if it's the right type
        if (denoisedImage.getType() == BufferedImage.TYPE_INT_RGB || denoisedImage.getType() == BufferedImage.TYPE_INT_ARGB) {
            imagePool.returnImage(denoisedImage);
        }

        String base64Denoised = Base64.getEncoder().encodeToString(denoisedBytes);

        log.debug("Denoised image using method {}: original size={}, denoised size={}", 
                method, imageBytes.length, denoisedBytes.length);

        return prefix + base64Denoised;
    }

    /**
     * Apply Gaussian blur for denoising
     * 
     * @param image Input image
     * @param strength Blur strength (0.0-1.0)
     * @return Blurred image
     */
    private BufferedImage applyGaussianBlur(BufferedImage image, double strength) {
        // Map strength to kernel size (3, 5, 7, 9)
        int kernelSize = (int) (3 + Math.round(strength * 3) * 2);
        
        // Create Gaussian kernel
        float[] kernel = createGaussianKernel(kernelSize, 0.5f + (float)strength);
        
        // Get a new image from the pool
        BufferedImage result = imagePool.borrowImage(image.getWidth(), image.getHeight(), image.getType());
        
        // Apply convolution
        ConvolveOp op = new ConvolveOp(new Kernel(kernelSize, kernelSize, kernel), 
                ConvolveOp.EDGE_NO_OP, null);
        return op.filter(image, result);
    }

    /**
     * Create a Gaussian kernel for blurring
     * 
     * @param size Kernel size (must be odd)
     * @param sigma Standard deviation
     * @return Kernel values
     */
    private float[] createGaussianKernel(int size, float sigma) {
        float[] kernel = new float[size * size];
        float sum = 0.0f;
        int center = size / 2;
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int dx = x - center;
                int dy = y - center;
                float value = (float) Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma));
                kernel[y * size + x] = value;
                sum += value;
            }
        }
        
        // Normalize
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        
        return kernel;
    }

    /**
     * Apply median filter for denoising
     * 
     * @param image Input image
     * @param strength Filter strength (0.0-1.0)
     * @return Filtered image
     */
    private BufferedImage applyMedianFilter(BufferedImage image, double strength) {
        // Map strength to window size (3, 5, 7)
        int windowSize = (int) (3 + Math.round(strength * 2) * 2);
        int halfWindow = windowSize / 2;
        
        // Get a new image from the pool
        BufferedImage result = imagePool.borrowImage(image.getWidth(), image.getHeight(), image.getType());
        
        // Process only every Nth pixel for performance when strength is low
        int skipFactor = strength < 0.7 ? 2 : 1;
        
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                // For pixels we're skipping, just copy the original
                if (skipFactor > 1 && (x % skipFactor != 0 || y % skipFactor != 0)) {
                    result.setRGB(x, y, image.getRGB(x, y));
                    continue;
                }
                
                int[] r = new int[windowSize * windowSize];
                int[] g = new int[windowSize * windowSize];
                int[] b = new int[windowSize * windowSize];
                int count = 0;
                
                // Gather pixel values in the window
                for (int wy = -halfWindow; wy <= halfWindow; wy++) {
                    for (int wx = -halfWindow; wx <= halfWindow; wx++) {
                        int px = Math.min(Math.max(x + wx, 0), image.getWidth() - 1);
                        int py = Math.min(Math.max(y + wy, 0), image.getHeight() - 1);
                        
                        int rgb = image.getRGB(px, py);
                        r[count] = (rgb >> 16) & 0xFF;
                        g[count] = (rgb >> 8) & 0xFF;
                        b[count] = rgb & 0xFF;
                        count++;
                    }
                }
                
                // Sort and find median
                java.util.Arrays.sort(r, 0, count);
                java.util.Arrays.sort(g, 0, count);
                java.util.Arrays.sort(b, 0, count);
                
                int medianR = r[count / 2];
                int medianG = g[count / 2];
                int medianB = b[count / 2];
                
                // Set result pixel
                int resultRGB = (medianR << 16) | (medianG << 8) | medianB;
                result.setRGB(x, y, resultRGB);
                
                // If we're skipping pixels, fill in the neighborhood with interpolated values
                if (skipFactor > 1) {
                    for (int fy = 0; fy < skipFactor && y + fy < image.getHeight(); fy++) {
                        for (int fx = 0; fx < skipFactor && x + fx < image.getWidth(); fx++) {
                            if (fx == 0 && fy == 0) continue; // Skip the pixel we just processed
                            result.setRGB(x + fx, y + fy, resultRGB);
                        }
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * Apply bilateral filter for edge-preserving denoising
     * 
     * @param image Input image
     * @param strength Filter strength (0.0-1.0)
     * @return Filtered image
     */
    private BufferedImage applyBilateralFilter(BufferedImage image, double strength) {
        // Map strength to parameters
        int radius = (int) (3 + Math.round(strength * 2));
        double sigmaSpace = 2.0 + strength * 3.0;
        double sigmaColor = 15.0 + strength * 30.0;
        
        // Get a new image from the pool
        BufferedImage result = imagePool.borrowImage(image.getWidth(), image.getHeight(), image.getType());
        
        // Process image
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                double sumR = 0, sumG = 0, sumB = 0;
                double weightSum = 0;
                
                int centerRGB = image.getRGB(x, y);
                int centerR = (centerRGB >> 16) & 0xFF;
                int centerG = (centerRGB >> 8) & 0xFF;
                int centerB = centerRGB & 0xFF;
                
                for (int wy = -radius; wy <= radius; wy++) {
                    for (int wx = -radius; wx <= radius; wx++) {
                        int px = Math.min(Math.max(x + wx, 0), image.getWidth() - 1);
                        int py = Math.min(Math.max(y + wy, 0), image.getHeight() - 1);
                        
                        int rgb = image.getRGB(px, py);
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        
                        // Spatial weight
                        double spatialDist = (wx * wx + wy * wy) / (2 * sigmaSpace * sigmaSpace);
                        double spatialWeight = Math.exp(-spatialDist);
                        
                        // Color weight
                        double colorDist = 
                            ((r - centerR) * (r - centerR) + 
                             (g - centerG) * (g - centerG) + 
                             (b - centerB) * (b - centerB)) / (2 * sigmaColor * sigmaColor);
                        double colorWeight = Math.exp(-colorDist);
                        
                        // Combined weight
                        double weight = spatialWeight * colorWeight;
                        
                        sumR += r * weight;
                        sumG += g * weight;
                        sumB += b * weight;
                        weightSum += weight;
                    }
                }
                
                // Normalize
                int resultR = (int) Math.round(sumR / weightSum);
                int resultG = (int) Math.round(sumG / weightSum);
                int resultB = (int) Math.round(sumB / weightSum);
                
                // Clamp values
                resultR = Math.min(255, Math.max(0, resultR));
                resultG = Math.min(255, Math.max(0, resultG));
                resultB = Math.min(255, Math.max(0, resultB));
                
                int resultRGB = (resultR << 16) | (resultG << 8) | resultB;
                result.setRGB(x, y, resultRGB);
            }
        }
        
        return result;
    }

    /**
     * Apply fast approximation of bilateral filter
     * 
     * @param image Input image
     * @param strength Filter strength (0.0-1.0)
     * @return Filtered image
     */
    private BufferedImage applyFastBilateralFilter(BufferedImage image, double strength) {
        // For performance, we'll use a hybrid approach:
        // 1. Downsample the image
        // 2. Apply a simplified bilateral filter
        // 3. Upsample back to original size
        
        // Map strength to parameters
        int downsampleFactor = strength < 0.5 ? 2 : 3;
        int radius = (int) (2 + Math.round(strength * 2));
        double sigmaColor = 15.0 + strength * 30.0;
        
        // Downsample
        int smallWidth = image.getWidth() / downsampleFactor;
        int smallHeight = image.getHeight() / downsampleFactor;
        
        // Get a small image from the pool
        BufferedImage small = imagePool.borrowImage(smallWidth, smallHeight, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = small.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(image, 0, 0, smallWidth, smallHeight, null);
        g2d.dispose();
        
        // Apply simplified bilateral filter on small image
        BufferedImage filtered = imagePool.borrowImage(smallWidth, smallHeight, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < smallHeight; y++) {
            for (int x = 0; x < smallWidth; x++) {
                double sumR = 0, sumG = 0, sumB = 0;
                double weightSum = 0;
                
                int centerRGB = small.getRGB(x, y);
                int centerR = (centerRGB >> 16) & 0xFF;
                int centerG = (centerRGB >> 8) & 0xFF;
                int centerB = centerRGB & 0xFF;
                
                for (int wy = -radius; wy <= radius; wy++) {
                    for (int wx = -radius; wx <= radius; wx++) {
                        int px = Math.min(Math.max(x + wx, 0), smallWidth - 1);
                        int py = Math.min(Math.max(y + wy, 0), smallHeight - 1);
                        
                        int rgb = small.getRGB(px, py);
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        
                        // Simplified weight calculation (no spatial component)
                        double colorDist = 
                            ((r - centerR) * (r - centerR) + 
                             (g - centerG) * (g - centerG) + 
                             (b - centerB) * (b - centerB)) / (2 * sigmaColor * sigmaColor);
                        double weight = Math.exp(-colorDist);
                        
                        sumR += r * weight;
                        sumG += g * weight;
                        sumB += b * weight;
                        weightSum += weight;
                    }
                }
                
                // Normalize
                int resultR = (int) Math.round(sumR / weightSum);
                int resultG = (int) Math.round(sumG / weightSum);
                int resultB = (int) Math.round(sumB / weightSum);
                
                // Clamp values
                resultR = Math.min(255, Math.max(0, resultR));
                resultG = Math.min(255, Math.max(0, resultG));
                resultB = Math.min(255, Math.max(0, resultB));
                
                int resultRGB = (resultR << 16) | (resultG << 8) | resultB;
                filtered.setRGB(x, y, resultRGB);
            }
        }
        
        // Upsample back to original size
        BufferedImage result = imagePool.borrowImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics2D g2dResult = result.createGraphics();
        g2dResult.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2dResult.drawImage(filtered, 0, 0, image.getWidth(), image.getHeight(), null);
        g2dResult.dispose();
        
        // Return the small and filtered images to the pool
        imagePool.returnImage(small);
        imagePool.returnImage(filtered);
        
        return result;
    }
    
    /**
     * Clean up the image pool periodically
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanUpImagePool() {
        imagePool.cleanUp();
    }
} 