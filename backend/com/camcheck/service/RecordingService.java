package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RecordingService {

    // Store active recordings: recordingId -> RecordingInfo
    private final Map<String, RecordingInfo> activeRecordings = new ConcurrentHashMap<>();
    
    // Store user recordings: username -> Map<recordingId, RecordingInfo>
    private final Map<String, Map<String, RecordingInfo>> userRecordings = new ConcurrentHashMap<>();
    
    @Value("${camcheck.storage.recordings-path:./recordings}")
    private String recordingsPath;
    
    @Value("${camcheck.storage.max-size-mb:1000}")
    private long maxStorageSizeMb;
    
    @Value("${camcheck.storage.delete-oldest:true}")
    private boolean deleteOldest;
    
    /**
     * Save a snapshot
     *
     * @param imageData Image data
     * @return Filename of saved snapshot
     * @throws IOException if error saving snapshot
     */
    public String saveSnapshot(byte[] imageData) throws IOException {
        // Create recordings directory if it doesn't exist
        Path recordingsDir = Paths.get(recordingsPath);
        if (!Files.exists(recordingsDir)) {
            Files.createDirectories(recordingsDir);
        }
        
        // Generate filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("snapshot_%s.jpg", timestamp);
        Path filePath = recordingsDir.resolve(filename);
        
        // Save image
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(imageData);
        }
        
        log.debug("Saved snapshot: {}", filename);
        return filename;
    }
    
    /**
     * Start recording
     *
     * @param username User identifier
     * @param options Recording options
     * @return Recording ID
     */
    public String startRecording(String username, Map<String, Object> options) {
        // Check storage limits
        if (!checkStorageLimit(username)) {
            throw new IllegalStateException("Storage limit reached");
        }
        
        // Generate recording ID
        String recordingId = UUID.randomUUID().toString();
        
        // Create recording info
        RecordingInfo info = new RecordingInfo();
        info.recordingId = recordingId;
        info.username = username;
        info.startTime = Instant.now();
        info.options.putAll(options);
        
        // Store recording info
        activeRecordings.put(recordingId, info);
        userRecordings.computeIfAbsent(username, k -> new ConcurrentHashMap<>())
                .put(recordingId, info);
        
        log.info("Started recording {} for user {}", recordingId, username);
        return recordingId;
    }
    
    /**
     * Stop recording
     *
     * @param username User identifier
     * @param recordingId Recording ID
     * @return Recording information
     */
    public Map<String, Object> stopRecording(String username, String recordingId) {
        // Get recording info
        RecordingInfo info = activeRecordings.get(recordingId);
        if (info == null || !info.username.equals(username)) {
            return null;
        }
        
        // Update recording info
        info.endTime = Instant.now();
        info.status = "completed";
        
        // Remove from active recordings
        activeRecordings.remove(recordingId);
        
        // Create response data
        Map<String, Object> recordingData = new HashMap<>();
        recordingData.put("recordingId", recordingId);
        recordingData.put("startTime", info.startTime);
        recordingData.put("endTime", info.endTime);
        recordingData.put("duration", info.endTime.toEpochMilli() - info.startTime.toEpochMilli());
        recordingData.put("status", info.status);
        recordingData.put("options", info.options);
        
        log.info("Stopped recording {} for user {}", recordingId, username);
        return recordingData;
    }
    
    /**
     * Get recording status
     *
     * @param username User identifier
     * @param recordingId Recording ID
     * @return Recording status
     */
    public Map<String, Object> getRecordingStatus(String username, String recordingId) {
        // Check active recordings first
        RecordingInfo info = activeRecordings.get(recordingId);
        if (info == null) {
            // Check completed recordings
            Map<String, RecordingInfo> userRecordingMap = userRecordings.get(username);
            if (userRecordingMap != null) {
                info = userRecordingMap.get(recordingId);
            }
        }
        
        if (info == null || !info.username.equals(username)) {
            return null;
        }
        
        // Create status data
        Map<String, Object> status = new HashMap<>();
        status.put("recordingId", recordingId);
        status.put("startTime", info.startTime);
        status.put("status", info.status);
        status.put("options", info.options);
        
        if (info.endTime != null) {
            status.put("endTime", info.endTime);
            status.put("duration", info.endTime.toEpochMilli() - info.startTime.toEpochMilli());
        }
        
        return status;
    }
    
    /**
     * List recordings
     *
     * @param username User identifier
     * @return Map of recordings
     */
    public Map<String, Object> listRecordings(String username) {
        Map<String, RecordingInfo> userRecordingMap = userRecordings.get(username);
        if (userRecordingMap == null) {
            return Map.of("recordings", List.of());
        }
        
        // Convert recording info to response format
        List<Map<String, Object>> recordings = userRecordingMap.values().stream()
                .map(info -> {
                    Map<String, Object> recording = new HashMap<>();
                    recording.put("recordingId", info.recordingId);
                    recording.put("startTime", info.startTime);
                    recording.put("status", info.status);
                    if (info.endTime != null) {
                        recording.put("endTime", info.endTime);
                        recording.put("duration", info.endTime.toEpochMilli() - info.startTime.toEpochMilli());
                    }
                    return recording;
                })
                .collect(Collectors.toList());
        
        return Map.of("recordings", recordings);
    }
    
    /**
     * Delete recording
     *
     * @param username User identifier
     * @param recordingId Recording ID
     * @return true if deleted, false if not found
     */
    public boolean deleteRecording(String username, String recordingId) {
        // Remove from active recordings if present
        RecordingInfo info = activeRecordings.remove(recordingId);
        
        // Remove from user recordings
        Map<String, RecordingInfo> userRecordingMap = userRecordings.get(username);
        if (userRecordingMap != null) {
            info = userRecordingMap.remove(recordingId);
        }
        
        if (info == null || !info.username.equals(username)) {
            return false;
        }
        
        // Delete recording file if it exists
        try {
            String filename = String.format("recording_%s.mp4", recordingId);
            Path filePath = Paths.get(recordingsPath, filename);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Error deleting recording file", e);
        }
        
        log.info("Deleted recording {} for user {}", recordingId, username);
        return true;
    }
    
    /**
     * Check storage limit
     *
     * @param username User identifier
     * @return true if under limit, false if limit reached
     */
    private boolean checkStorageLimit(String username) {
        try {
            Path recordingsDir = Paths.get(recordingsPath);
            if (!Files.exists(recordingsDir)) {
                return true;
            }
            
            // Calculate total size
            long totalSize = Files.walk(recordingsDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
            
            // Convert to MB
            long totalSizeMb = totalSize / (1024 * 1024);
            
            if (totalSizeMb >= maxStorageSizeMb) {
                if (deleteOldest) {
                    // Delete oldest recordings until under limit
                    List<Path> files = Files.walk(recordingsDir)
                            .filter(Files::isRegularFile)
                            .sorted((a, b) -> {
                                try {
                                    return Files.getLastModifiedTime(a)
                                            .compareTo(Files.getLastModifiedTime(b));
                                } catch (IOException e) {
                                    return 0;
                                }
                            })
                            .collect(Collectors.toList());
                    
                    for (Path file : files) {
                        Files.delete(file);
                        totalSizeMb -= Files.size(file) / (1024 * 1024);
                        if (totalSizeMb < maxStorageSizeMb) {
                            break;
                        }
                    }
                    
                    return true;
                }
                return false;
            }
            
            return true;
        } catch (IOException e) {
            log.error("Error checking storage limit", e);
            return false;
        }
    }
    
    /**
     * Recording information
     */
    private static class RecordingInfo {
        String recordingId;
        String username;
        Instant startTime;
        Instant endTime;
        String status = "recording";
        Map<String, Object> options = new HashMap<>();
    }
} 