package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

/**
 * Service for handling image and audio compression
 */
@Service
@Slf4j
public class CompressionService {

    @Value("${camcheck.media.min-compression-quality:0.5}")
    private float minCompressionQuality;

    @Value("${camcheck.media.max-compression-quality:0.95}")
    private float maxCompressionQuality;
    
    @Value("${camcheck.media.default-format:jpeg}")
    private String defaultFormat;
    
    private final List<String> supportedImageFormats = Arrays.asList("jpeg", "jpg", "png", "webp");
    private final List<String> supportedAudioFormats = Arrays.asList("mp3", "opus", "wav");

    /**
     * Compress an image with specified quality
     * @param base64Image Base64 encoded image data
     * @param quality Compression quality (0.0-1.0)
     * @return Compressed image as Base64 string
     * @throws IOException If compression fails
     */
    public String compressImage(String base64Image, double quality) throws IOException {
        // Validate quality parameter
        float validatedQuality = validateQuality(quality);
        
        // Extract actual base64 data if it contains the data URL prefix
        String base64Data = base64Image;
        if (base64Image.contains(",")) {
            base64Data = base64Image.split(",")[1];
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
        
        // Compress the image
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        // Get a writer for JPEG format
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(defaultFormat);
        if (!writers.hasNext()) {
            throw new IOException("No image writer found for " + defaultFormat);
        }
        
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        
        // Set compression quality if the format supports it
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(validatedQuality);
        }
        
        // Write the image
        ImageOutputStream ios = new MemoryCacheImageOutputStream(bos);
        writer.setOutput(ios);
        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();
        ios.close();
        
        // Convert back to base64
        byte[] compressedBytes = bos.toByteArray();
        bos.close();
        
        String base64Compressed = Base64.getEncoder().encodeToString(compressedBytes);
        
        log.debug("Compressed image: original size={}, compressed size={}, ratio={}%", 
                imageBytes.length, compressedBytes.length, 
                Math.round((float) compressedBytes.length / imageBytes.length * 100));
        
        return base64Compressed;
    }
    
    /**
     * Compress audio data (placeholder for future implementation)
     * @param base64Audio Base64 encoded audio data
     * @param format Target audio format
     * @param quality Compression quality (0.0-1.0)
     * @return Compressed audio as Base64 string
     * @throws IOException If compression fails
     */
    public String compressAudio(String base64Audio, String format, double quality) throws IOException {
        // This is a placeholder for future audio compression implementation
        // Currently just validates the parameters and returns the original data
        
        if (!supportedAudioFormats.contains(format.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported audio format: " + format);
        }
        
        // Validate quality
        validateQuality(quality);
        
        log.info("Audio compression requested but not yet implemented for format: {}", format);
        
        // For now, just return the original data
        return base64Audio;
    }
    
    /**
     * Get list of supported image formats
     * @return List of supported formats
     */
    public List<String> getSupportedFormats() {
        return supportedImageFormats;
    }
    
    /**
     * Get list of supported audio formats
     * @return List of supported formats
     */
    public List<String> getSupportedAudioFormats() {
        return supportedAudioFormats;
    }
    
    /**
     * Validate and constrain quality parameter
     * @param quality Requested quality (0.0-1.0)
     * @return Validated quality within allowed range
     */
    private float validateQuality(double quality) {
        float validatedQuality = (float) quality;
        
        if (validatedQuality < minCompressionQuality) {
            log.warn("Requested quality {} is below minimum {}. Using minimum.", 
                    validatedQuality, minCompressionQuality);
            validatedQuality = minCompressionQuality;
        } else if (validatedQuality > maxCompressionQuality) {
            log.warn("Requested quality {} is above maximum {}. Using maximum.", 
                    validatedQuality, maxCompressionQuality);
            validatedQuality = maxCompressionQuality;
        }
        
        return validatedQuality;
    }
} 