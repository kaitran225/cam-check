package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Service for handling video recording
 */
@Service
@Slf4j
public class RecordingService {

    private final SimpMessagingTemplate messagingTemplate;
    
    @Value("${camcheck.storage.recordings-path}")
    private String recordingsPath;
    
    @Value("${camcheck.storage.recording-length}")
    private int recordingLength;
    
    @Value("${camcheck.storage.max-size-mb}")
    private long maxSizeMb;
    
    @Value("${camcheck.storage.delete-oldest}")
    private boolean deleteOldest;
    
    private boolean isRecording = false;
    private LocalDateTime recordingStartTime;
    private List<BufferedImage> currentRecordingFrames;
    private ExecutorService saveExecutor;
    
    public RecordingService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.saveExecutor = Executors.newSingleThreadExecutor();
    }
    
    @PostConstruct
    public void init() {
        // Create recordings directory if it doesn't exist
        try {
            Path path = Paths.get(recordingsPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created recordings directory: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create recordings directory: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Start recording
     */
    public void startRecording() {
        if (isRecording) {
            return;
        }
        
        isRecording = true;
        recordingStartTime = LocalDateTime.now();
        currentRecordingFrames = new ArrayList<>();
        
        log.info("Started recording at {}", recordingStartTime);
        messagingTemplate.convertAndSend("/topic/recording", 
                                         Map.of("status", "started", 
                                                "time", recordingStartTime.toString()));
    }
    
    /**
     * Stop recording and save the video
     */
    public void stopRecording() {
        if (!isRecording) {
            return;
        }
        
        isRecording = false;
        LocalDateTime endTime = LocalDateTime.now();
        
        log.info("Stopped recording at {}", endTime);
        messagingTemplate.convertAndSend("/topic/recording", 
                                         Map.of("status", "stopped", 
                                                "time", endTime.toString()));
        
        // Save the recording in a separate thread
        final List<BufferedImage> framesToSave = new ArrayList<>(currentRecordingFrames);
        final LocalDateTime startTime = recordingStartTime;
        
        saveExecutor.submit(() -> {
            saveRecording(framesToSave, startTime);
        });
        
        currentRecordingFrames = null;
    }
    
    /**
     * Add a frame to the current recording
     */
    public void addFrame(BufferedImage frame) {
        if (!isRecording || frame == null) {
            return;
        }
        
        currentRecordingFrames.add(deepCopy(frame));
        
        // Auto-stop recording after specified duration
        if (recordingStartTime != null && 
            LocalDateTime.now().minusSeconds(recordingLength).isAfter(recordingStartTime)) {
            stopRecording();
        }
    }
    
    /**
     * Save the recording to disk
     */
    private void saveRecording(List<BufferedImage> frames, LocalDateTime startTime) {
        if (frames == null || frames.isEmpty()) {
            return;
        }
        
        try {
            // Create directory for this recording
            String timestamp = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path recordingDir = Paths.get(recordingsPath, timestamp);
            Files.createDirectories(recordingDir);
            
            // Save frames as images
            for (int i = 0; i < frames.size(); i++) {
                File outputFile = new File(recordingDir.toFile(), String.format("frame_%04d.jpg", i));
                ImageIO.write(frames.get(i), "jpg", outputFile);
            }
            
            // Create a simple HTML viewer
            createHtmlViewer(recordingDir, frames.size());
            
            log.info("Saved recording with {} frames to {}", frames.size(), recordingDir);
            messagingTemplate.convertAndSend("/topic/recording", 
                                           Map.of("status", "saved", 
                                                  "path", recordingDir.toString(),
                                                  "frames", frames.size()));
        } catch (IOException e) {
            log.error("Failed to save recording: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Create a simple HTML viewer for the recording
     */
    private void createHtmlViewer(Path recordingDir, int frameCount) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html><head><title>Recording Viewer</title>\n")
            .append("<style>body{font-family:Arial;margin:20px;text-align:center;}")
            .append("img{max-width:100%;border:1px solid #ddd;}")
            .append("button{margin:10px;padding:5px 15px;}</style>\n")
            .append("</head><body>\n")
            .append("<h1>Recording Viewer</h1>\n")
            .append("<div><button onclick=\"prevFrame()\">Previous</button>")
            .append("<span id=\"counter\">1/").append(frameCount).append("</span>")
            .append("<button onclick=\"nextFrame()\">Next</button></div>\n")
            .append("<div><button onclick=\"play()\">Play</button>")
            .append("<button onclick=\"pause()\">Pause</button></div>\n")
            .append("<img id=\"frame\" src=\"frame_0000.jpg\">\n")
            .append("<script>\n")
            .append("let currentFrame = 0;\n")
            .append("const totalFrames = ").append(frameCount).append(";\n")
            .append("let playing = false;\n")
            .append("let interval;\n")
            .append("function updateFrame() {\n")
            .append("  document.getElementById('frame').src = `frame_${String(currentFrame).padStart(4, '0')}.jpg`;\n")
            .append("  document.getElementById('counter').textContent = `${currentFrame+1}/${totalFrames}`;\n")
            .append("}\n")
            .append("function nextFrame() {\n")
            .append("  currentFrame = (currentFrame + 1) % totalFrames;\n")
            .append("  updateFrame();\n")
            .append("}\n")
            .append("function prevFrame() {\n")
            .append("  currentFrame = (currentFrame - 1 + totalFrames) % totalFrames;\n")
            .append("  updateFrame();\n")
            .append("}\n")
            .append("function play() {\n")
            .append("  if (!playing) {\n")
            .append("    playing = true;\n")
            .append("    interval = setInterval(nextFrame, 100);\n")
            .append("  }\n")
            .append("}\n")
            .append("function pause() {\n")
            .append("  playing = false;\n")
            .append("  clearInterval(interval);\n")
            .append("}\n")
            .append("</script>\n")
            .append("</body></html>");
        
        Files.writeString(recordingDir.resolve("viewer.html"), html.toString());
    }
    
    /**
     * Create a deep copy of a BufferedImage
     */
    private BufferedImage deepCopy(BufferedImage bi) {
        BufferedImage copy = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB);
        copy.createGraphics().drawImage(bi, 0, 0, null);
        return copy;
    }
    
    /**
     * Check if currently recording
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * Clean up old recordings to stay under storage limit
     * Runs once per hour
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupOldRecordings() {
        if (!deleteOldest || maxSizeMb <= 0) {
            return;
        }
        
        try {
            Path recordingsDir = Paths.get(recordingsPath);
            if (!Files.exists(recordingsDir)) {
                return;
            }
            
            // Calculate current size
            long currentSizeBytes = FileUtils.sizeOfDirectory(recordingsDir.toFile());
            long maxSizeBytes = maxSizeMb * 1024 * 1024;
            
            if (currentSizeBytes <= maxSizeBytes) {
                return;
            }
            
            log.info("Storage limit exceeded. Current size: {}MB, Max: {}MB", 
                    currentSizeBytes / (1024 * 1024), maxSizeMb);
            
            // Get all recording directories sorted by creation time (oldest first)
            try (Stream<Path> paths = Files.list(recordingsDir)) {
                List<Path> sortedDirs = paths
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .toList();
                
                // Delete oldest recordings until under limit
                for (Path dir : sortedDirs) {
                    if (currentSizeBytes <= maxSizeBytes) {
                        break;
                    }
                    
                    long dirSize = FileUtils.sizeOfDirectory(dir.toFile());
                    try {
                        FileUtils.deleteDirectory(dir.toFile());
                        currentSizeBytes -= dirSize;
                        log.info("Deleted old recording: {} ({}MB)", 
                                dir.getFileName(), dirSize / (1024 * 1024));
                    } catch (IOException e) {
                        log.error("Failed to delete old recording: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error during cleanup: {}", e.getMessage(), e);
        }
    }
} 