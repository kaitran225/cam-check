package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling multiple video codec support
 * Provides codec detection, selection, and transcoding capabilities
 */
@Service
@Slf4j
public class CodecService {

    @Value("${camcheck.media.codec.preferred:h264}")
    private String preferredCodec;
    
    @Value("${camcheck.media.codec.fallback:jpeg}")
    private String fallbackCodec;
    
    @Value("${camcheck.media.codec.quality:0.85}")
    private double defaultQuality;
    
    @Value("${camcheck.media.codec.hardware-acceleration:true}")
    private boolean hardwareAcceleration;
    
    // Map of supported codecs and their capabilities
    private final Map<String, CodecInfo> supportedCodecs = new HashMap<>();
    
    // Map of user codec preferences
    private final Map<String, String> userCodecPreferences = new ConcurrentHashMap<>();
    
    // Map of connection codec capabilities (negotiated between peers)
    private final Map<String, Set<String>> connectionCodecCapabilities = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // Initialize supported codecs
        initializeCodecs();
        
        log.info("CodecService initialized with preferred codec: {}, fallback: {}", 
                preferredCodec, fallbackCodec);
        log.info("Hardware acceleration is {}", hardwareAcceleration ? "enabled" : "disabled");
    }
    
    /**
     * Initialize supported codecs and detect hardware capabilities
     */
    private void initializeCodecs() {
        // H.264 (AVC) - widely supported
        CodecInfo h264 = new CodecInfo("h264", "H.264/AVC", true);
        h264.setBrowserSupport(Map.of(
            "chrome", true,
            "firefox", true,
            "safari", true,
            "edge", true,
            "opera", true
        ));
        h264.setHardwareAccelerated(detectHardwareAcceleration("h264"));
        h264.setMimeTypes(List.of("video/mp4; codecs=avc1.42E01E", "video/mp4; codecs=avc1.4D401E"));
        supportedCodecs.put("h264", h264);
        
        // VP8 - older but widely supported
        CodecInfo vp8 = new CodecInfo("vp8", "VP8", true);
        vp8.setBrowserSupport(Map.of(
            "chrome", true,
            "firefox", true,
            "safari", false,
            "edge", true,
            "opera", true
        ));
        vp8.setHardwareAccelerated(detectHardwareAcceleration("vp8"));
        vp8.setMimeTypes(List.of("video/webm; codecs=vp8"));
        supportedCodecs.put("vp8", vp8);
        
        // VP9 - better compression than VP8
        CodecInfo vp9 = new CodecInfo("vp9", "VP9", true);
        vp9.setBrowserSupport(Map.of(
            "chrome", true,
            "firefox", true,
            "safari", false,
            "edge", true,
            "opera", true
        ));
        vp9.setHardwareAccelerated(detectHardwareAcceleration("vp9"));
        vp9.setMimeTypes(List.of("video/webm; codecs=vp9"));
        supportedCodecs.put("vp9", vp9);
        
        // AV1 - newest codec with best compression
        CodecInfo av1 = new CodecInfo("av1", "AV1", false);
        av1.setBrowserSupport(Map.of(
            "chrome", true,
            "firefox", true,
            "safari", false,
            "edge", true,
            "opera", true
        ));
        av1.setHardwareAccelerated(detectHardwareAcceleration("av1"));
        av1.setMimeTypes(List.of("video/webm; codecs=av01.0.05M.08"));
        supportedCodecs.put("av1", av1);
        
        // JPEG - fallback for image-based streaming
        CodecInfo jpeg = new CodecInfo("jpeg", "JPEG", true);
        jpeg.setBrowserSupport(Map.of(
            "chrome", true,
            "firefox", true,
            "safari", true,
            "edge", true,
            "opera", true
        ));
        jpeg.setHardwareAccelerated(true); // JPEG encoding/decoding is usually hardware accelerated
        jpeg.setMimeTypes(List.of("image/jpeg"));
        supportedCodecs.put("jpeg", jpeg);
        
        // WebP - better image compression than JPEG
        CodecInfo webp = new CodecInfo("webp", "WebP", true);
        webp.setBrowserSupport(Map.of(
            "chrome", true,
            "firefox", true,
            "safari", true,
            "edge", true,
            "opera", true
        ));
        webp.setHardwareAccelerated(detectHardwareAcceleration("webp"));
        webp.setMimeTypes(List.of("image/webp"));
        supportedCodecs.put("webp", webp);
        
        log.info("Initialized {} codecs", supportedCodecs.size());
    }
    
    /**
     * Detect if hardware acceleration is available for a codec
     * @param codec Codec name
     * @return True if hardware acceleration is available
     */
    private boolean detectHardwareAcceleration(String codec) {
        // This is a placeholder for actual hardware acceleration detection
        // In a real implementation, this would check for hardware capabilities
        
        // For now, assume hardware acceleration is available for common codecs
        return hardwareAcceleration && (
            codec.equals("h264") || 
            codec.equals("vp8") || 
            codec.equals("jpeg")
        );
    }
    
    /**
     * Get list of supported codecs
     * @return Map of codec information
     */
    public Map<String, CodecInfo> getSupportedCodecs() {
        return Collections.unmodifiableMap(supportedCodecs);
    }
    
    /**
     * Check if a codec is supported
     * @param codec Codec name
     * @return True if the codec is supported
     */
    public boolean isCodecSupported(String codec) {
        return supportedCodecs.containsKey(codec.toLowerCase());
    }
    
    /**
     * Set preferred codec for a user
     * @param userId User identifier
     * @param codec Preferred codec
     * @return True if the codec is supported and set as preferred
     */
    public boolean setPreferredCodec(String userId, String codec) {
        if (!isCodecSupported(codec)) {
            log.warn("Unsupported codec requested: {}", codec);
            return false;
        }
        
        userCodecPreferences.put(userId, codec.toLowerCase());
        log.debug("Set preferred codec for user {}: {}", userId, codec);
        return true;
    }
    
    /**
     * Get preferred codec for a user
     * @param userId User identifier
     * @return Preferred codec or system default
     */
    public String getPreferredCodec(String userId) {
        return userCodecPreferences.getOrDefault(userId, preferredCodec);
    }
    
    /**
     * Negotiate codecs between two users
     * @param connectionId Connection identifier
     * @param user1 First user identifier
     * @param user2 Second user identifier
     * @param user1Codecs Codecs supported by first user
     * @param user2Codecs Codecs supported by second user
     * @return Negotiated codec or fallback
     */
    public String negotiateCodec(String connectionId, String user1, String user2, 
                                List<String> user1Codecs, List<String> user2Codecs) {
        log.debug("Negotiating codec for connection {}: {} <-> {}", connectionId, user1, user2);
        log.debug("User {} supports: {}", user1, user1Codecs);
        log.debug("User {} supports: {}", user2, user2Codecs);
        
        // Convert to lowercase for case-insensitive comparison
        List<String> user1CodecsLower = user1Codecs.stream()
            .map(String::toLowerCase)
            .filter(this::isCodecSupported)
            .toList();
        
        List<String> user2CodecsLower = user2Codecs.stream()
            .map(String::toLowerCase)
            .filter(this::isCodecSupported)
            .toList();
        
        // Find common codecs
        Set<String> commonCodecs = new HashSet<>(user1CodecsLower);
        commonCodecs.retainAll(user2CodecsLower);
        
        // Store the common codecs for this connection
        connectionCodecCapabilities.put(connectionId, commonCodecs);
        
        if (commonCodecs.isEmpty()) {
            log.warn("No common codecs found, using fallback: {}", fallbackCodec);
            return fallbackCodec;
        }
        
        // Check if either user's preferred codec is in the common set
        String user1Preferred = getPreferredCodec(user1);
        if (commonCodecs.contains(user1Preferred)) {
            log.debug("Using user1's preferred codec: {}", user1Preferred);
            return user1Preferred;
        }
        
        String user2Preferred = getPreferredCodec(user2);
        if (commonCodecs.contains(user2Preferred)) {
            log.debug("Using user2's preferred codec: {}", user2Preferred);
            return user2Preferred;
        }
        
        // Check for best available codec in order of preference
        String[] codecPriority = {"av1", "vp9", "h264", "vp8", "webp", "jpeg"};
        for (String codec : codecPriority) {
            if (commonCodecs.contains(codec)) {
                log.debug("Using best available codec: {}", codec);
                return codec;
            }
        }
        
        // If no preferred codec is found, use the first common codec
        String negotiatedCodec = commonCodecs.iterator().next();
        log.debug("Using first common codec: {}", negotiatedCodec);
        return negotiatedCodec;
    }
    
    /**
     * Get MIME type for a codec
     * @param codec Codec name
     * @return MIME type or null if not supported
     */
    public String getMimeType(String codec) {
        CodecInfo codecInfo = supportedCodecs.get(codec.toLowerCase());
        if (codecInfo == null || codecInfo.getMimeTypes().isEmpty()) {
            return null;
        }
        return codecInfo.getMimeTypes().get(0);
    }
    
    /**
     * Check if a codec is hardware accelerated
     * @param codec Codec name
     * @return True if hardware acceleration is available
     */
    public boolean isHardwareAccelerated(String codec) {
        CodecInfo codecInfo = supportedCodecs.get(codec.toLowerCase());
        return codecInfo != null && codecInfo.isHardwareAccelerated();
    }
    
    /**
     * Transcode data from one codec to another
     * @param inputData Input data
     * @param sourceCodec Source codec
     * @param targetCodec Target codec
     * @param quality Quality (0.0-1.0)
     * @return Transcoded data
     * @throws IOException If transcoding fails
     */
    public byte[] transcode(byte[] inputData, String sourceCodec, String targetCodec, double quality) throws IOException {
        // This is a placeholder for actual transcoding
        // In a real implementation, this would use a transcoding library
        
        if (sourceCodec.equalsIgnoreCase(targetCodec)) {
            // No transcoding needed
            return inputData;
        }
        
        log.debug("Transcoding from {} to {} with quality {}", sourceCodec, targetCodec, quality);
        
        // For now, just return the input data
        // In a real implementation, this would perform the actual transcoding
        return inputData;
    }
    
    /**
     * Reset codec preferences for a connection
     * @param connectionId Connection identifier
     */
    public void resetConnection(String connectionId) {
        connectionCodecCapabilities.remove(connectionId);
        log.debug("Reset codec capabilities for connection: {}", connectionId);
    }
    
    /**
     * Class representing codec information
     */
    public static class CodecInfo {
        private final String id;
        private final String name;
        private final boolean supported;
        private boolean hardwareAccelerated;
        private Map<String, Boolean> browserSupport = new HashMap<>();
        private List<String> mimeTypes = new ArrayList<>();
        
        public CodecInfo(String id, String name, boolean supported) {
            this.id = id;
            this.name = name;
            this.supported = supported;
        }
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public boolean isSupported() {
            return supported;
        }
        
        public boolean isHardwareAccelerated() {
            return hardwareAccelerated;
        }
        
        public void setHardwareAccelerated(boolean hardwareAccelerated) {
            this.hardwareAccelerated = hardwareAccelerated;
        }
        
        public Map<String, Boolean> getBrowserSupport() {
            return browserSupport;
        }
        
        public void setBrowserSupport(Map<String, Boolean> browserSupport) {
            this.browserSupport = browserSupport;
        }
        
        public List<String> getMimeTypes() {
            return mimeTypes;
        }
        
        public void setMimeTypes(List<String> mimeTypes) {
            this.mimeTypes = mimeTypes;
        }
    }
} 