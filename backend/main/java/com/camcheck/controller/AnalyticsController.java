package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.service.AnalyticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for analytics endpoints
 */
@RestController
@RequestMapping("/api/analytics")
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    
    @Autowired
    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }
    
    /**
     * Get system statistics
     * @return System statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> getStatistics() {
        Map<String, Object> stats = analyticsService.getStatistics();
        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved", stats));
    }
    
    /**
     * Get system metrics
     * @param metricName Metric name (optional)
     * @param timeRangeMinutes Time range in minutes (optional, default: 60)
     * @return System metrics
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> getMetrics(
            @RequestParam(required = false) String metricName,
            @RequestParam(defaultValue = "60") int timeRangeMinutes) {
        
        Map<String, Object> result = new HashMap<>();
        
        if (metricName != null) {
            // Get specific metric
            List<Map<String, Object>> points = analyticsService.getMetrics(metricName, timeRangeMinutes).stream()
                    .map(point -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("timestamp", point.getTimestamp().toEpochMilli());
                        map.put("value", point.getValue());
                        return map;
                    })
                    .collect(Collectors.toList());
            
            result.put(metricName, points);
        } else {
            // Get all metrics
            Map<String, List<Map<String, Object>>> allMetrics = new HashMap<>();
            
            // Get list of available metrics from the statistics
            Map<String, Object> stats = analyticsService.getStatistics();
            @SuppressWarnings("unchecked")
            Map<String, Object> currentMetrics = (Map<String, Object>) stats.getOrDefault("current_metrics", new HashMap<>());
            
            for (String metric : currentMetrics.keySet()) {
                List<Map<String, Object>> points = analyticsService.getMetrics(metric, timeRangeMinutes).stream()
                        .map(point -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("timestamp", point.getTimestamp().toEpochMilli());
                            map.put("value", point.getValue());
                            return map;
                        })
                        .collect(Collectors.toList());
                
                allMetrics.put(metric, points);
            }
            
            result.put("metrics", allMetrics);
        }
        
        return ResponseEntity.ok(ApiResponse.success("Metrics retrieved", result));
    }
    
    /**
     * Get alerts
     * @param count Maximum number of alerts to return (optional, default: 10)
     * @param includeResolved Whether to include resolved alerts (optional, default: false)
     * @return Alerts
     */
    @GetMapping("/alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> getAlerts(
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "false") boolean includeResolved) {
        
        List<Map<String, Object>> alerts = analyticsService.getAlerts(count, includeResolved).stream()
                .map(alert -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", alert.getId());
                    map.put("type", alert.getType());
                    map.put("message", alert.getMessage());
                    map.put("level", alert.getLevel().name());
                    map.put("creationTime", alert.getCreationTime().toEpochMilli());
                    map.put("resolved", alert.isResolved());
                    
                    if (alert.isResolved()) {
                        map.put("resolutionTime", alert.getResolutionTime().toEpochMilli());
                        map.put("resolution", alert.getResolution());
                    }
                    
                    return map;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Alerts retrieved", Map.of("alerts", alerts)));
    }
    
    /**
     * Resolve an alert
     * @param alertId Alert identifier
     * @param resolution Resolution message
     * @return Response
     */
    @PostMapping("/alerts/{alertId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> resolveAlert(
            @PathVariable String alertId,
            @RequestParam String resolution) {
        
        boolean resolved = analyticsService.resolveAlert(alertId, resolution);
        
        if (resolved) {
            return ResponseEntity.ok(ApiResponse.success("Alert resolved"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Record a user login event
     * @param userId User identifier
     * @return Response
     */
    @PostMapping("/events/login")
    public ResponseEntity<ApiResponse> recordLogin(@RequestParam String userId) {
        analyticsService.recordUserLogin(userId);
        return ResponseEntity.ok(ApiResponse.success("Login recorded"));
    }
    
    /**
     * Record a user logout event
     * @param userId User identifier
     * @return Response
     */
    @PostMapping("/events/logout")
    public ResponseEntity<ApiResponse> recordLogout(@RequestParam String userId) {
        analyticsService.recordUserLogout(userId);
        return ResponseEntity.ok(ApiResponse.success("Logout recorded"));
    }
    
    /**
     * Record a stream start event
     * @param streamId Stream identifier
     * @return Response
     */
    @PostMapping("/events/stream-start")
    public ResponseEntity<ApiResponse> recordStreamStart(@RequestParam String streamId) {
        analyticsService.recordStreamStart(streamId);
        return ResponseEntity.ok(ApiResponse.success("Stream start recorded"));
    }
    
    /**
     * Record a stream end event
     * @param streamId Stream identifier
     * @return Response
     */
    @PostMapping("/events/stream-end")
    public ResponseEntity<ApiResponse> recordStreamEnd(@RequestParam String streamId) {
        analyticsService.recordStreamEnd(streamId);
        return ResponseEntity.ok(ApiResponse.success("Stream end recorded"));
    }
    
    /**
     * Record bytes transferred
     * @param bytes Number of bytes
     * @param isAudio Whether the bytes are audio data
     * @return Response
     */
    @PostMapping("/events/bytes-transferred")
    public ResponseEntity<ApiResponse> recordBytesTransferred(
            @RequestParam long bytes,
            @RequestParam(defaultValue = "false") boolean isAudio) {
        
        analyticsService.recordBytesTransferred(bytes, isAudio);
        return ResponseEntity.ok(ApiResponse.success("Bytes transferred recorded"));
    }
    
    /**
     * Record network latency
     * @param latencyMs Latency in milliseconds
     * @return Response
     */
    @PostMapping("/events/latency")
    public ResponseEntity<ApiResponse> recordLatency(@RequestParam double latencyMs) {
        analyticsService.recordLatency(latencyMs);
        return ResponseEntity.ok(ApiResponse.success("Latency recorded"));
    }
} 