package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.service.VMCapacityMeasurementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for system-level operations and information
 */
@RestController
@RequestMapping("/api/v1/system")
@Slf4j
public class SystemController {

    private final VMCapacityMeasurementService vmCapacityService;
    private final MemoryMXBean memoryMXBean;
    private final OperatingSystemMXBean osMXBean;
    private final RuntimeMXBean runtimeMXBean;
    
    @Autowired
    public SystemController(VMCapacityMeasurementService vmCapacityService) {
        this.vmCapacityService = vmCapacityService;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    }
    
    /**
     * Get VM capacity information
     * 
     * @return VM capacity information
     */
    @GetMapping("/capacity")
    public ResponseEntity<ApiResponse> getVMCapacity() {
        Map<String, Object> capacity = vmCapacityService.getCapacityInfo();
        
        // Add current memory usage
        Map<String, Object> currentMemory = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        currentMemory.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        currentMemory.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        currentMemory.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        currentMemory.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        
        // Add to capacity info
        Map<String, Object> result = new HashMap<>(capacity);
        result.put("currentMemory", currentMemory);
        
        return ResponseEntity.ok(new ApiResponse("success", "VM capacity information retrieved", result));
    }
    
    /**
     * Get current system status
     * 
     * @return Current system status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Memory status
        Map<String, Object> memoryStatus = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        memoryStatus.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        memoryStatus.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        memoryStatus.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        memoryStatus.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        memoryStatus.put("usagePercent", (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() * 100);
        
        status.put("memory", memoryStatus);
        
        // Uptime
        status.put("uptimeMs", runtimeMXBean.getUptime());
        status.put("uptimeMinutes", runtimeMXBean.getUptime() / (1000 * 60));
        status.put("uptimeHours", runtimeMXBean.getUptime() / (1000 * 60 * 60));
        
        // CPU status
        Map<String, Object> cpuStatus = new HashMap<>();
        cpuStatus.put("availableProcessors", runtime.availableProcessors());
        cpuStatus.put("systemLoadAverage", osMXBean.getSystemLoadAverage());
        
        // Try to get more detailed CPU info using reflection
        try {
            Class<?> sunOsClass = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (sunOsClass.isInstance(osMXBean)) {
                Object sunOsMXBean = sunOsClass.cast(osMXBean);
                
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getProcessCpuLoad");
                    double processCpuLoad = (Double) method.invoke(sunOsMXBean);
                    cpuStatus.put("processCpuLoad", processCpuLoad);
                } catch (Exception e) {
                    log.debug("Could not get process CPU load: {}", e.getMessage());
                }
                
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getSystemCpuLoad");
                    double systemCpuLoad = (Double) method.invoke(sunOsMXBean);
                    cpuStatus.put("systemCpuLoad", systemCpuLoad);
                } catch (Exception e) {
                    log.debug("Could not get system CPU load: {}", e.getMessage());
                }
            }
        } catch (ClassNotFoundException e) {
            log.debug("com.sun.management.OperatingSystemMXBean not available");
        }
        
        status.put("cpu", cpuStatus);
        
        // Thread status
        Map<String, Object> threadStatus = new HashMap<>();
        threadStatus.put("threadCount", Thread.activeCount());
        threadStatus.put("daemonThreadCount", ManagementFactory.getThreadMXBean().getDaemonThreadCount());
        threadStatus.put("peakThreadCount", ManagementFactory.getThreadMXBean().getPeakThreadCount());
        threadStatus.put("totalStartedThreadCount", ManagementFactory.getThreadMXBean().getTotalStartedThreadCount());
        
        status.put("threads", threadStatus);
        
        return ResponseEntity.ok(new ApiResponse("success", "System status retrieved", status));
    }
    
    /**
     * Force garbage collection (admin only)
     * 
     * @return Result of garbage collection
     */
    @GetMapping("/gc")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> forceGarbageCollection() {
        log.info("Manual garbage collection requested");
        
        // Record memory before GC
        long beforeUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        
        // Run garbage collection
        System.gc();
        System.runFinalization();
        
        // Record memory after GC
        long afterUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        
        Map<String, Object> result = new HashMap<>();
        result.put("beforeUsedMB", beforeUsed);
        result.put("afterUsedMB", afterUsed);
        result.put("freedMB", beforeUsed - afterUsed);
        
        return ResponseEntity.ok(new ApiResponse("success", "Garbage collection performed", result));
    }
} 