package com.camcheck.util;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Memory-efficient byte array implementation that uses compression
 * for large arrays to reduce memory usage
 */
public class CompactByteArray {

    private static final int COMPRESSION_THRESHOLD = 8192; // 8KB
    private static final int BUFFER_SIZE = 4096;
    
    private byte[] data;
    private byte[] compressedData;
    private int length;
    private boolean compressed;
    
    /**
     * Create a new compact byte array
     * 
     * @param data The data to store
     */
    public CompactByteArray(byte[] data) {
        this(data, data.length > COMPRESSION_THRESHOLD);
    }
    
    /**
     * Create a new compact byte array
     * 
     * @param data The data to store
     * @param compress Whether to compress the data
     */
    public CompactByteArray(byte[] data, boolean compress) {
        if (compress && data.length > COMPRESSION_THRESHOLD) {
            this.compressedData = compress(data);
            this.compressed = true;
        } else {
            this.data = Arrays.copyOf(data, data.length);
            this.compressed = false;
        }
        this.length = data.length;
    }
    
    /**
     * Get the data as a byte array
     * 
     * @return The data
     */
    public byte[] getData() {
        if (compressed) {
            return decompress(compressedData, length);
        } else {
            return data;
        }
    }
    
    /**
     * Get the length of the original data
     * 
     * @return The length
     */
    public int getLength() {
        return length;
    }
    
    /**
     * Get the actual memory usage of this object
     * 
     * @return The memory usage in bytes
     */
    public int getMemoryUsage() {
        if (compressed) {
            return compressedData.length;
        } else {
            return data.length;
        }
    }
    
    /**
     * Get the compression ratio
     * 
     * @return The compression ratio (original size / compressed size)
     */
    public double getCompressionRatio() {
        if (!compressed) {
            return 1.0;
        }
        return (double) length / compressedData.length;
    }
    
    /**
     * Check if the data is compressed
     * 
     * @return True if the data is compressed
     */
    public boolean isCompressed() {
        return compressed;
    }
    
    /**
     * Compress a byte array
     * 
     * @param input The input data
     * @return The compressed data
     */
    private static byte[] compress(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(input);
        deflater.finish();
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(input.length);
        byte[] buffer = new byte[BUFFER_SIZE];
        
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        
        deflater.end();
        
        return outputStream.toByteArray();
    }
    
    /**
     * Decompress a byte array
     * 
     * @param input The compressed data
     * @param originalLength The original length of the data
     * @return The decompressed data
     */
    private static byte[] decompress(byte[] input, int originalLength) {
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        
        byte[] result = new byte[originalLength];
        try {
            inflater.inflate(result);
        } catch (Exception e) {
            // If decompression fails, return an empty array
            return new byte[0];
        } finally {
            inflater.end();
        }
        
        return result;
    }
    
    /**
     * Create a compact byte array from a Base64 string
     * 
     * @param base64 The Base64 string
     * @return A new CompactByteArray
     */
    public static CompactByteArray fromBase64(String base64) {
        // Remove data URL prefix if present
        if (base64.startsWith("data:")) {
            int commaIndex = base64.indexOf(',');
            if (commaIndex > 0) {
                base64 = base64.substring(commaIndex + 1);
            }
        }
        
        byte[] data = java.util.Base64.getDecoder().decode(base64);
        return new CompactByteArray(data);
    }
    
    /**
     * Convert the data to a Base64 string
     * 
     * @return The Base64 string
     */
    public String toBase64() {
        return java.util.Base64.getEncoder().encodeToString(getData());
    }
    
    /**
     * Convert the data to a Base64 string with a data URL prefix
     * 
     * @param mimeType The MIME type
     * @return The Base64 data URL
     */
    public String toDataUrl(String mimeType) {
        return "data:" + mimeType + ";base64," + toBase64();
    }
} 