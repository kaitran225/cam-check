package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling snapshots from client cameras
 * Snapshot saving functionality is disabled
 */
@Service
@Slf4j
public class RecordingService {

    @Value("${camcheck.storage.recordings-path:./recordings}")
    private String recordingsPath;
    
    @Value("${camcheck.storage.max-size-mb:1000}")
    private long maxSizeMb;
    
    @Value("${camcheck.storage.delete-oldest:true}")
    private boolean deleteOldest;
    
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

    /**
     * Get statistics about recordings
     * @return Map with statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            File recordingsDir = new File(recordingsPath);
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs();
            }
            
            // Count recordings
            File[] recordings = recordingsDir.listFiles((dir, name) -> name.endsWith(".mp4") || name.endsWith(".webm"));
            int recordingCount = recordings != null ? recordings.length : 0;
            
            // Calculate total size
            long totalSizeBytes = 0;
            if (recordings != null) {
                for (File recording : recordings) {
                    totalSizeBytes += recording.length();
                }
            }
            
            // Calculate disk usage
            long maxSizeBytes = maxSizeMb * 1024 * 1024;
            double diskUsagePercent = maxSizeBytes > 0 ? ((double) totalSizeBytes / maxSizeBytes) * 100 : 0;
            
            // Get disk space information
            Path path = Paths.get(recordingsPath);
            long totalSpace = Files.getFileStore(path).getTotalSpace();
            long usableSpace = Files.getFileStore(path).getUsableSpace();
            
            stats.put("recording_count", recordingCount);
            stats.put("total_size_bytes", totalSizeBytes);
            stats.put("total_size_mb", totalSizeBytes / (1024 * 1024));
            stats.put("max_size_mb", maxSizeMb);
            stats.put("disk_usage_percent", diskUsagePercent);
            stats.put("total_disk_space_mb", totalSpace / (1024 * 1024));
            stats.put("usable_disk_space_mb", usableSpace / (1024 * 1024));
            stats.put("delete_oldest_enabled", deleteOldest);
            
        } catch (Exception e) {
            log.error("Error getting recording statistics", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
} 