package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for handling snapshots from client cameras
 * Simplified version with no server-side camera functionality
 */
@Service
@Slf4j
public class RecordingService {
    
    @Value("${camcheck.storage.recordings-path}")
    private String recordingsPath;
    
    @PostConstruct
    public void init() {
        // Create recordings directory if it doesn't exist
        try {
            Path path = Paths.get(recordingsPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created recordings directory: {}", path.toAbsolutePath());
            }
            
            // Create snapshots subdirectory
            Path snapshotsDir = Paths.get(recordingsPath, "snapshots");
            if (!Files.exists(snapshotsDir)) {
                Files.createDirectories(snapshotsDir);
                log.info("Created snapshots directory: {}", snapshotsDir.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create recordings directory: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Save a snapshot from client camera
     * @param imageData Raw image data bytes
     * @return Filename of the saved snapshot
     * @throws IOException If there's an error saving the file
     */
    public String saveSnapshot(byte[] imageData) throws IOException {
        // Create snapshots directory if it doesn't exist
        Path snapshotsDir = Paths.get(recordingsPath, "snapshots");
        if (!Files.exists(snapshotsDir)) {
            Files.createDirectories(snapshotsDir);
        }
        
        // Generate filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String filename = "snapshot_" + timestamp + ".jpg";
        Path filePath = snapshotsDir.resolve(filename);
        
        // Save the image file
        Files.write(filePath, imageData);
        log.info("Saved snapshot: {}", filePath);
        
        return filename;
    }
} 