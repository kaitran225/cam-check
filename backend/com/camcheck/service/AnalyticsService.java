package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for collecting and managing system metrics and statistics
 */
@Service
@Slf4j
public class AnalyticsService {

    @Value("${camcheck.analytics.enabled:true}")
    private boolean analyticsEnabled;
    
    @Value("${camcheck.analytics.metrics-retention-days:30}")
    private int metricsRetentionDays;
    
    @Value("${camcheck.analytics.alert-threshold-cpu:80}")
    private int alertThresholdCpu;
    
    @Value("${camcheck.analytics.alert-threshold-memory:80}")
    private int alertThresholdMemory;
    
    @Value("${camcheck.analytics.alert-threshold-disk:90}")
    private int alertThresholdDisk;
    
    private final WebRTCService webRTCService;
    private final CameraService cameraService;
    @SuppressWarnings("unused")
    private final RecordingService recordingService;
    
    // System metrics
    private final Map<String, Queue<MetricPoint>> metrics = new ConcurrentHashMap<>();
    
    // Active users count
    private final AtomicInteger activeUsers = new AtomicInteger(0);
    
    // Active streams count
    private final AtomicInteger activeStreams = new AtomicInteger(0);
    
    // Total bytes transferred (video)
    private final AtomicLong totalBytesTransferred = new AtomicLong(0);
    
    // Total bytes transferred (audio)
    private final AtomicLong totalAudioBytesTransferred = new AtomicLong(0);
    
    // Total recordings count
    private final AtomicInteger totalRecordings = new AtomicInteger(0);
    
    // Total recording duration (seconds)
    private final AtomicLong totalRecordingDuration = new AtomicLong(0);
    
    // System start time
    private final Instant startTime = Instant.now();
    
    // Alerts
    private final List<Alert> alerts = Collections.synchronizedList(new ArrayList<>());
    
    // Alert listeners
    private final List<AlertListener> alertListeners = new ArrayList<>();
    
    @Autowired
    public AnalyticsService(WebRTCService webRTCService, CameraService cameraService, RecordingService recordingService) {
        this.webRTCService = webRTCService;
        this.cameraService = cameraService;
        this.recordingService = recordingService;
        
        // Initialize metrics
        metrics.put("cpu_usage", new ConcurrentLinkedQueue<>());
        metrics.put("memory_usage", new ConcurrentLinkedQueue<>());
        metrics.put("disk_usage", new ConcurrentLinkedQueue<>());
        metrics.put("network_in", new ConcurrentLinkedQueue<>());
        metrics.put("network_out", new ConcurrentLinkedQueue<>());
        metrics.put("active_users", new ConcurrentLinkedQueue<>());
        metrics.put("active_streams", new ConcurrentLinkedQueue<>());
        metrics.put("latency", new ConcurrentLinkedQueue<>());
        
        log.info("AnalyticsService initialized with retention period of {} days", metricsRetentionDays);
    }
    
    /**
     * Record a user login event
     * @param userId User identifier
     */
    public void recordUserLogin(String userId) {
        if (!analyticsEnabled) return;
        
        activeUsers.incrementAndGet();
        recordMetric("active_users", activeUsers.get());
        log.debug("User logged in: {}, active users: {}", userId, activeUsers.get());
    }
    
    /**
     * Record a user logout event
     * @param userId User identifier
     */
    public void recordUserLogout(String userId) {
        if (!analyticsEnabled) return;
        
        int count = activeUsers.decrementAndGet();
        if (count < 0) {
            activeUsers.set(0);
            count = 0;
        }
        recordMetric("active_users", count);
        log.debug("User logged out: {}, active users: {}", userId, count);
    }
    
    /**
     * Record a stream start event
     * @param streamId Stream identifier
     */
    public void recordStreamStart(String streamId) {
        if (!analyticsEnabled) return;
        
        activeStreams.incrementAndGet();
        recordMetric("active_streams", activeStreams.get());
        log.debug("Stream started: {}, active streams: {}", streamId, activeStreams.get());
    }
    
    /**
     * Record a stream end event
     * @param streamId Stream identifier
     */
    public void recordStreamEnd(String streamId) {
        if (!analyticsEnabled) return;
        
        int count = activeStreams.decrementAndGet();
        if (count < 0) {
            activeStreams.set(0);
            count = 0;
        }
        recordMetric("active_streams", count);
        log.debug("Stream ended: {}, active streams: {}", streamId, count);
    }
    
    /**
     * Record bytes transferred
     * @param bytes Number of bytes
     * @param isAudio Whether the bytes are audio data
     */
    public void recordBytesTransferred(long bytes, boolean isAudio) {
        if (!analyticsEnabled) return;
        
        if (isAudio) {
            totalAudioBytesTransferred.addAndGet(bytes);
        } else {
            totalBytesTransferred.addAndGet(bytes);
        }
    }
    
    /**
     * Record a new recording
     * @param durationSeconds Duration in seconds
     */
    public void recordNewRecording(long durationSeconds) {
        if (!analyticsEnabled) return;
        
        totalRecordings.incrementAndGet();
        totalRecordingDuration.addAndGet(durationSeconds);
    }
    
    /**
     * Record network latency
     * @param latencyMs Latency in milliseconds
     */
    public void recordLatency(double latencyMs) {
        if (!analyticsEnabled) return;
        
        recordMetric("latency", latencyMs);
    }
    
    /**
     * Record a system metric
     * @param metricName Metric name
     * @param value Metric value
     */
    public void recordMetric(String metricName, double value) {
        if (!analyticsEnabled) return;
        
        Queue<MetricPoint> metricPoints = metrics.computeIfAbsent(metricName, k -> new ConcurrentLinkedQueue<>());
        metricPoints.add(new MetricPoint(Instant.now(), value));
    }
    
    /**
     * Get system metrics
     * @param metricName Metric name
     * @param timeRangeMinutes Time range in minutes (0 for all available data)
     * @return List of metric points
     */
    public List<MetricPoint> getMetrics(String metricName, int timeRangeMinutes) {
        Queue<MetricPoint> metricPoints = metrics.get(metricName);
        if (metricPoints == null) {
            return Collections.emptyList();
        }
        
        List<MetricPoint> result = new ArrayList<>();
        Instant cutoff = timeRangeMinutes > 0 ? Instant.now().minus(Duration.ofMinutes(timeRangeMinutes)) : Instant.MIN;
        
        for (MetricPoint point : metricPoints) {
            if (point.getTimestamp().isAfter(cutoff)) {
                result.add(point);
            }
        }
        
        return result;
    }
    
    /**
     * Get system statistics
     * @return Map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // System uptime
        Duration uptime = Duration.between(startTime, Instant.now());
        stats.put("uptime_seconds", uptime.getSeconds());
        
        // User statistics
        stats.put("active_users", activeUsers.get());
        
        // Stream statistics
        stats.put("active_streams", activeStreams.get());
        
        // Data transfer statistics
        stats.put("total_bytes_transferred", totalBytesTransferred.get());
        stats.put("total_audio_bytes_transferred", totalAudioBytesTransferred.get());
        
        // Recording statistics
        stats.put("total_recordings", totalRecordings.get());
        stats.put("total_recording_duration", totalRecordingDuration.get());
        
        // WebRTC statistics
        stats.put("webrtc", webRTCService.getStatistics());
        
        // Camera statistics
        stats.put("camera", cameraService.getStatistics());
        
        // Current system metrics
        Map<String, Object> currentMetrics = new HashMap<>();
        for (Map.Entry<String, Queue<MetricPoint>> entry : metrics.entrySet()) {
            Queue<MetricPoint> points = entry.getValue();
            if (!points.isEmpty()) {
                currentMetrics.put(entry.getKey(), points.peek().getValue());
            }
        }
        stats.put("current_metrics", currentMetrics);
        
        return stats;
    }
    
    /**
     * Get alerts
     * @param count Maximum number of alerts to return (0 for all)
     * @param includeResolved Whether to include resolved alerts
     * @return List of alerts
     */
    public List<Alert> getAlerts(int count, boolean includeResolved) {
        List<Alert> result = new ArrayList<>();
        
        synchronized (alerts) {
            for (Alert alert : alerts) {
                if (includeResolved || !alert.isResolved()) {
                    result.add(alert);
                    if (count > 0 && result.size() >= count) {
                        break;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Create a new alert
     * @param type Alert type
     * @param message Alert message
     * @param level Alert level
     * @return Created alert
     */
    public Alert createAlert(String type, String message, AlertLevel level) {
        Alert alert = new Alert(UUID.randomUUID().toString(), type, message, level, Instant.now());
        
        synchronized (alerts) {
            alerts.add(alert);
        }
        
        // Notify listeners
        for (AlertListener listener : alertListeners) {
            try {
                listener.onAlert(alert);
            } catch (Exception e) {
                log.error("Error notifying alert listener", e);
            }
        }
        
        log.info("Alert created: {} - {}", type, message);
        return alert;
    }
    
    /**
     * Resolve an alert
     * @param alertId Alert identifier
     * @param resolution Resolution message
     * @return True if the alert was resolved
     */
    public boolean resolveAlert(String alertId, String resolution) {
        synchronized (alerts) {
            for (Alert alert : alerts) {
                if (alert.getId().equals(alertId) && !alert.isResolved()) {
                    alert.setResolved(true);
                    alert.setResolutionTime(Instant.now());
                    alert.setResolution(resolution);
                    
                    // Notify listeners
                    for (AlertListener listener : alertListeners) {
                        try {
                            listener.onAlertResolved(alert);
                        } catch (Exception e) {
                            log.error("Error notifying alert listener", e);
                        }
                    }
                    
                    log.info("Alert resolved: {} - {}", alert.getType(), resolution);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Add an alert listener
     * @param listener Alert listener
     */
    public void addAlertListener(AlertListener listener) {
        alertListeners.add(listener);
    }
    
    /**
     * Remove an alert listener
     * @param listener Alert listener
     */
    public void removeAlertListener(AlertListener listener) {
        alertListeners.remove(listener);
    }
    
    /**
     * Scheduled task to collect system metrics
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void collectSystemMetrics() {
        if (!analyticsEnabled) return;
        
        try {
            // Get CPU usage
            double cpuUsage = getCpuUsage();
            recordMetric("cpu_usage", cpuUsage);
            
            // Get memory usage
            double memoryUsage = getMemoryUsage();
            recordMetric("memory_usage", memoryUsage);
            
            // Get disk usage
            double diskUsage = getDiskUsage();
            recordMetric("disk_usage", diskUsage);
            
            // Get network usage
            double networkIn = getNetworkInUsage();
            double networkOut = getNetworkOutUsage();
            recordMetric("network_in", networkIn);
            recordMetric("network_out", networkOut);
            
            // Check for alerts
            checkAlerts(cpuUsage, memoryUsage, diskUsage);
            
            log.debug("Collected system metrics: CPU={}%, Memory={}%, Disk={}%", 
                    String.format("%.1f", cpuUsage), 
                    String.format("%.1f", memoryUsage), 
                    String.format("%.1f", diskUsage));
        } catch (Exception e) {
            log.error("Error collecting system metrics", e);
        }
    }
    
    /**
     * Scheduled task to clean up old metrics
     */
    @Scheduled(fixedRate = 86400000) // Every day
    public void cleanupOldMetrics() {
        if (!analyticsEnabled) return;
        
        try {
            Instant cutoff = Instant.now().minus(Duration.ofDays(metricsRetentionDays));
            int removed = 0;
            
            for (Queue<MetricPoint> points : metrics.values()) {
                Iterator<MetricPoint> iterator = points.iterator();
                while (iterator.hasNext()) {
                    MetricPoint point = iterator.next();
                    if (point.getTimestamp().isBefore(cutoff)) {
                        iterator.remove();
                        removed++;
                    } else {
                        // Points are ordered by time, so we can stop once we find a point after the cutoff
                        break;
                    }
                }
            }
            
            log.info("Cleaned up {} old metric points", removed);
        } catch (Exception e) {
            log.error("Error cleaning up old metrics", e);
        }
    }
    
    /**
     * Check for alert conditions
     * @param cpuUsage CPU usage percentage
     * @param memoryUsage Memory usage percentage
     * @param diskUsage Disk usage percentage
     */
    private void checkAlerts(double cpuUsage, double memoryUsage, double diskUsage) {
        // Check CPU usage
        if (cpuUsage > alertThresholdCpu) {
            createAlert("cpu_usage", "CPU usage is high: " + String.format("%.1f", cpuUsage) + "%", AlertLevel.WARNING);
        }
        
        // Print precise memory usage instead of creating alerts
        try {
            com.sun.management.OperatingSystemMXBean osBean = 
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long totalMemoryBytes = osBean.getTotalMemorySize();
            long freeMemoryBytes = osBean.getFreeMemorySize();
            long usedMemoryBytes = totalMemoryBytes - freeMemoryBytes;
            
            // Convert to MB for more readable output
            double totalMemoryMB = totalMemoryBytes / (1024.0 * 1024.0);
            double usedMemoryMB = usedMemoryBytes / (1024.0 * 1024.0);
            double freeMemoryMB = freeMemoryBytes / (1024.0 * 1024.0);
            
            log.info("Memory usage: {}/{} MB ({} MB free, {:.2f}%)",
                    String.format("%.2f", usedMemoryMB),
                    String.format("%.2f", totalMemoryMB),
                    String.format("%.2f", freeMemoryMB),
                    memoryUsage);
                    
            // Get JVM-specific memory information
            Runtime runtime = Runtime.getRuntime();
            double maxMemoryMB = runtime.maxMemory() / (1024.0 * 1024.0);
            double allocatedMemoryMB = runtime.totalMemory() / (1024.0 * 1024.0);
            double freeJvmMemoryMB = runtime.freeMemory() / (1024.0 * 1024.0);
            double usedJvmMemoryMB = allocatedMemoryMB - freeJvmMemoryMB;
            
            log.info("JVM memory usage: {}/{} MB ({} MB free, {:.2f}%)",
                    String.format("%.2f", usedJvmMemoryMB),
                    String.format("%.2f", maxMemoryMB),
                    String.format("%.2f", freeJvmMemoryMB),
                    (usedJvmMemoryMB / maxMemoryMB) * 100);
        } catch (Exception e) {
            log.error("Error getting detailed memory information", e);
        }
        
        // Check disk usage
        if (diskUsage > alertThresholdDisk) {
            createAlert("disk_usage", "Disk usage is high: " + String.format("%.1f", diskUsage) + "%", AlertLevel.WARNING);
        }
    }
    
    /**
     * Get CPU usage percentage
     * @return CPU usage percentage
     */
    private double getCpuUsage() {
        try {
            // This is a simple implementation that uses the JVM's OperatingSystemMXBean
            // In a production environment, you might want to use a more sophisticated approach
            com.sun.management.OperatingSystemMXBean osBean = 
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return osBean.getCpuLoad() * 100;
        } catch (Exception e) {
            log.error("Error getting CPU usage", e);
            return 0;
        }
    }
    
    /**
     * Get memory usage percentage
     * @return Memory usage percentage
     */
    private double getMemoryUsage() {
        try {
            // This is a simple implementation that uses the JVM's OperatingSystemMXBean
            // In a production environment, you might want to use a more sophisticated approach
            com.sun.management.OperatingSystemMXBean osBean = 
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long totalMemory = osBean.getTotalMemorySize();
            long freeMemory = osBean.getFreeMemorySize();
            return (double) (totalMemory - freeMemory) / totalMemory * 100;
        } catch (Exception e) {
            log.error("Error getting memory usage", e);
            return 0;
        }
    }
    
    /**
     * Get disk usage percentage
     * @return Disk usage percentage
     */
    private double getDiskUsage() {
        try {
            // This is a simple implementation that checks the disk where the JVM is running
            // In a production environment, you might want to check specific disks
            java.io.File file = new java.io.File(".");
            long totalSpace = file.getTotalSpace();
            long usableSpace = file.getUsableSpace();
            return (double) (totalSpace - usableSpace) / totalSpace * 100;
        } catch (Exception e) {
            log.error("Error getting disk usage", e);
            return 0;
        }
    }
    
    /**
     * Get network in usage (bytes per second)
     * @return Network in usage
     */
    private double getNetworkInUsage() {
        // This is a placeholder implementation
        // In a production environment, you would need to use platform-specific tools or libraries
        return 0;
    }
    
    /**
     * Get network out usage (bytes per second)
     * @return Network out usage
     */
    private double getNetworkOutUsage() {
        // This is a placeholder implementation
        // In a production environment, you would need to use platform-specific tools or libraries
        return 0;
    }
    
    /**
     * Class representing a metric point
     */
    public static class MetricPoint {
        private final Instant timestamp;
        private final double value;
        
        public MetricPoint(Instant timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
        
        public double getValue() {
            return value;
        }
    }
    
    /**
     * Class representing an alert
     */
    public static class Alert {
        private final String id;
        private final String type;
        private final String message;
        private final AlertLevel level;
        private final Instant creationTime;
        private boolean resolved;
        private Instant resolutionTime;
        private String resolution;
        
        public Alert(String id, String type, String message, AlertLevel level, Instant creationTime) {
            this.id = id;
            this.type = type;
            this.message = message;
            this.level = level;
            this.creationTime = creationTime;
            this.resolved = false;
        }
        
        public String getId() {
            return id;
        }
        
        public String getType() {
            return type;
        }
        
        public String getMessage() {
            return message;
        }
        
        public AlertLevel getLevel() {
            return level;
        }
        
        public Instant getCreationTime() {
            return creationTime;
        }
        
        public boolean isResolved() {
            return resolved;
        }
        
        public void setResolved(boolean resolved) {
            this.resolved = resolved;
        }
        
        public Instant getResolutionTime() {
            return resolutionTime;
        }
        
        public void setResolutionTime(Instant resolutionTime) {
            this.resolutionTime = resolutionTime;
        }
        
        public String getResolution() {
            return resolution;
        }
        
        public void setResolution(String resolution) {
            this.resolution = resolution;
        }
    }
    
    /**
     * Enum representing alert levels
     */
    public enum AlertLevel {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
    
    /**
     * Interface for alert listeners
     */
    public interface AlertListener {
        void onAlert(Alert alert);
        void onAlertResolved(Alert alert);
    }
} 