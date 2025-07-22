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
 * Snapshot saving functionality is disabled
 */
@Service
@Slf4j
public class RecordingService {
    
    @Value("${camcheck.storage.recordings-path}")
    private String recordingsPath;
    
    @PostConstruct
    public void init() {
        // Log that recording is disabled
        log.info("Recording service initialized with snapshot saving disabled");
    }
    
    /**
     * Disabled snapshot saving
     * @param imageData Raw image data bytes
     * @return Filename of the would-be snapshot (not actually saved)
     */
    public String saveSnapshot(byte[] imageData) throws IOException {
        // Generate mock filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String filename = "snapshot_" + timestamp + ".jpg";
        
        log.info("Snapshot saving is disabled. Would have saved: {}", filename);
        
        return filename;
    }
} 