package com.camcheck.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service to measure VM capacity on startup to optimize resource usage
 * Specifically tuned for Render.com free tier environment
 */
@Service
@Slf4j
public class VMCapacityMeasurementService {

    @Value("${vm.capacity.measure:true}")
    private boolean measureCapacity;
    
    // Disable stress test by default for Render.com to avoid overloading
    @Value("${vm.capacity.stress-test:false}")
    private boolean performStressTest;
    
    // Limit measurement time to avoid startup delays
    @Value("${vm.capacity.timeout-ms:10000}")
    private long measurementTimeoutMs;
    
    // Flag to detect Render.com environment
    @Value("${vm.capacity.is-render:false}")
    private boolean isRenderEnvironment;
    
    private final ApplicationEventPublisher eventPublisher;
    private final MemoryMXBean memoryMXBean;
    private final OperatingSystemMXBean osMXBean;
    private final RuntimeMXBean runtimeMXBean;
    
    @Getter
    private Map<String, Object> vmCapacity = new HashMap<>();
    
    @Getter
    private boolean measurementComplete = false;
    
    @Autowired
    public VMCapacityMeasurementService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    }
    
    @PostConstruct
    public void init() {
        log.info("VM Capacity Measurement Service initialized for Render.com environment");
        vmCapacity.put("jvmArgs", runtimeMXBean.getInputArguments());
        vmCapacity.put("jvmName", runtimeMXBean.getVmName());
        vmCapacity.put("jvmVendor", runtimeMXBean.getVmVendor());
        vmCapacity.put("jvmVersion", runtimeMXBean.getVmVersion());
        
        // Check for Render.com environment
        detectRenderEnvironment();
    }
    
    /**
     * Detect if we're running in a Render.com environment
     */
    private void detectRenderEnvironment() {
        // Check for Render-specific environment variables
        boolean hasRenderEnvVars = System.getenv("RENDER") != null || 
                                  System.getenv("RENDER_SERVICE_ID") != null;
        
        if (hasRenderEnvVars) {
            log.info("Detected Render.com environment");
            isRenderEnvironment = true;
        }
        
        // Check for AWS environment (Render uses AWS)
        String osVersion = System.getProperty("os.version", "");
        if (osVersion.contains("aws")) {
            log.info("Detected AWS-based environment (likely Render.com)");
            isRenderEnvironment = true;
        }
        
        vmCapacity.put("isRenderEnvironment", isRenderEnvironment);
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationStartup() {
        if (measureCapacity) {
            log.info("Starting VM capacity measurement for Render.com environment");
            
            // Measure basic capacity with timeout
            try {
                CompletableFuture<Void> measurementTask = CompletableFuture.runAsync(this::measureBasicCapacity);
                measurementTask.get(measurementTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("VM capacity measurement timed out or failed: {}", e.getMessage());
                // Perform a minimal measurement to ensure we have some data
                performMinimalMeasurement();
            }
            
            // Only perform stress test if explicitly enabled and not in Render environment
            if (performStressTest && !isRenderEnvironment) {
                log.info("Starting stress test (not recommended for Render.com)");
                CompletableFuture.runAsync(this::performStressTest);
            }
        }
    }
    
    /**
     * Perform a minimal measurement in case the full measurement fails
     */
    private void performMinimalMeasurement() {
        try {
            // Get basic memory info
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> memoryStats = new HashMap<>();
            memoryStats.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
            memoryStats.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
            memoryStats.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
            memoryStats.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
            
            vmCapacity.put("memory", memoryStats);
            
            // Get CPU info
            Map<String, Object> cpuStats = new HashMap<>();
            cpuStats.put("availableProcessors", runtime.availableProcessors());
            vmCapacity.put("cpu", cpuStats);
            
            // Mark as complete
            measurementComplete = true;
            log.info("Minimal VM capacity measurement completed");
            
            // Log basic info
            logMinimalCapacityInfo();
        } catch (Exception e) {
            log.error("Failed to perform minimal measurement: {}", e.getMessage());
        }
    }
    
    /**
     * Log minimal capacity information
     */
    private void logMinimalCapacityInfo() {
        Runtime runtime = Runtime.getRuntime();
        log.info("VM Minimal Capacity: max={}MB, total={}MB, free={}MB, processors={}",
                runtime.maxMemory() / (1024 * 1024),
                runtime.totalMemory() / (1024 * 1024),
                runtime.freeMemory() / (1024 * 1024),
                runtime.availableProcessors());
    }
    
    /**
     * Measure basic VM capacity
     * Optimized for Render.com environment
     */
    private void measureBasicCapacity() {
        // Measure memory
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        Map<String, Object> memoryStats = new HashMap<>();
        memoryStats.put("maxMemoryMB", maxMemory / (1024 * 1024));
        memoryStats.put("totalMemoryMB", totalMemory / (1024 * 1024));
        memoryStats.put("freeMemoryMB", freeMemory / (1024 * 1024));
        memoryStats.put("usedMemoryMB", usedMemory / (1024 * 1024));
        
        // Measure heap memory
        memoryStats.put("heapInitMB", memoryMXBean.getHeapMemoryUsage().getInit() / (1024 * 1024));
        memoryStats.put("heapUsedMB", memoryMXBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        memoryStats.put("heapCommittedMB", memoryMXBean.getHeapMemoryUsage().getCommitted() / (1024 * 1024));
        memoryStats.put("heapMaxMB", memoryMXBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
        
        // Measure non-heap memory
        memoryStats.put("nonHeapInitMB", memoryMXBean.getNonHeapMemoryUsage().getInit() / (1024 * 1024));
        memoryStats.put("nonHeapUsedMB", memoryMXBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024));
        memoryStats.put("nonHeapCommittedMB", memoryMXBean.getNonHeapMemoryUsage().getCommitted() / (1024 * 1024));
        memoryStats.put("nonHeapMaxMB", memoryMXBean.getNonHeapMemoryUsage().getMax() / (1024 * 1024));
        
        vmCapacity.put("memory", memoryStats);
        
        // Measure CPU - lightweight for Render.com
        Map<String, Object> cpuStats = new HashMap<>();
        cpuStats.put("availableProcessors", runtime.availableProcessors());
        cpuStats.put("systemLoadAverage", osMXBean.getSystemLoadAverage());
        
        // Use reflection to access com.sun.management.OperatingSystemMXBean methods if available
        try {
            Class<?> sunOsClass = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (sunOsClass.isInstance(osMXBean)) {
                Object sunOsMBean = sunOsClass.cast(osMXBean);
                
                // CPU load - most important metrics for Render.com
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getProcessCpuLoad");
                    double processCpuLoad = (Double) method.invoke(sunOsMBean);
                    cpuStats.put("processCpuLoad", processCpuLoad);
                } catch (Exception e) {
                    log.debug("Could not get process CPU load: {}", e.getMessage());
                }
                
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getSystemCpuLoad");
                    double systemCpuLoad = (Double) method.invoke(sunOsMBean);
                    cpuStats.put("systemCpuLoad", systemCpuLoad);
                } catch (Exception e) {
                    log.debug("Could not get system CPU load: {}", e.getMessage());
                }
                
                // Physical memory - important for Render.com
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getTotalPhysicalMemorySize");
                    long totalPhysicalMemory = (Long) method.invoke(sunOsMBean);
                    cpuStats.put("totalPhysicalMemoryMB", totalPhysicalMemory / (1024 * 1024));
                } catch (Exception e) {
                    log.debug("Could not get total physical memory: {}", e.getMessage());
                }
                
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getFreePhysicalMemorySize");
                    long freePhysicalMemory = (Long) method.invoke(sunOsMBean);
                    cpuStats.put("freePhysicalMemoryMB", freePhysicalMemory / (1024 * 1024));
                } catch (Exception e) {
                    log.debug("Could not get free physical memory: {}", e.getMessage());
                }
            }
        } catch (ClassNotFoundException e) {
            log.debug("com.sun.management.OperatingSystemMXBean not available");
        }
        
        vmCapacity.put("cpu", cpuStats);
        
        // Measure disk - lightweight for Render.com
        Map<String, Object> diskStats = new HashMap<>();
        File root = new File("/");
        diskStats.put("totalSpaceMB", root.getTotalSpace() / (1024 * 1024));
        diskStats.put("freeSpaceMB", root.getFreeSpace() / (1024 * 1024));
        diskStats.put("usableSpaceMB", root.getUsableSpace() / (1024 * 1024));
        
        vmCapacity.put("disk", diskStats);
        
        // Measure environment
        Map<String, Object> envStats = new HashMap<>();
        envStats.put("osName", System.getProperty("os.name"));
        envStats.put("osVersion", System.getProperty("os.version"));
        envStats.put("osArch", System.getProperty("os.arch"));
        envStats.put("javaVersion", System.getProperty("java.version"));
        envStats.put("javaVendor", System.getProperty("java.vendor"));
        
        // Check for Render.com specific environment variables
        envStats.put("isRender", isRenderEnvironment);
        
        vmCapacity.put("environment", envStats);
        
        // Calculate recommended settings based on measurements
        calculateRecommendedSettings();
        
        // Log capacity information
        logCapacityInfo();
        
        measurementComplete = true;
    }
    
    /**
     * Calculate recommended settings based on measurements
     */
    private void calculateRecommendedSettings() {
        Map<String, Object> recommendations = new HashMap<>();
        
        // Get memory stats
        Map<String, Object> memoryStats = (Map<String, Object>) vmCapacity.get("memory");
        if (memoryStats != null) {
            long maxHeapMB = (long) memoryStats.get("heapMaxMB");
            long totalPhysicalMemoryMB = 0;
            
            // Get CPU stats for physical memory
            Map<String, Object> cpuStats = (Map<String, Object>) vmCapacity.get("cpu");
            if (cpuStats != null && cpuStats.containsKey("totalPhysicalMemoryMB")) {
                totalPhysicalMemoryMB = (long) cpuStats.get("totalPhysicalMemoryMB");
            }
            
            // Calculate recommended settings
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            
            // Undertow settings
            recommendations.put("undertowWorkerThreads", Math.min(availableProcessors * 2, 4));
            recommendations.put("undertowIoThreads", Math.min(availableProcessors, 2));
            recommendations.put("undertowBufferSize", maxHeapMB < 64 ? 4096 : 8192);
            recommendations.put("undertowDirectBuffers", maxHeapMB > 128);
            
            // Thread pool settings
            recommendations.put("asyncCorePoolSize", availableProcessors);
            recommendations.put("asyncMaxPoolSize", availableProcessors * 2);
            
            // Memory settings
            recommendations.put("memoryTargetMB", Math.max(20, maxHeapMB - 8));
            recommendations.put("memoryHighThreshold", maxHeapMB < 64 ? 70 : 80);
            recommendations.put("memoryCriticalThreshold", maxHeapMB < 64 ? 85 : 90);
            
            // Cache settings
            recommendations.put("maxCacheEntries", maxHeapMB < 64 ? 100 : 500);
            recommendations.put("maxPoolSize", maxHeapMB < 64 ? 5 : 10);
            
            // Throttling settings
            recommendations.put("maxConcurrentRequests", availableProcessors <= 1 ? 2 : availableProcessors);
        }
        
        vmCapacity.put("recommendations", recommendations);
    }
    
    /**
     * Perform a stress test to measure maximum capacity
     * This is optional and only runs if vm.capacity.stress-test=true
     * Not recommended for Render.com environment
     */
    private void performStressTest() {
        log.info("Starting VM stress test to measure capacity");
        
        Map<String, Object> stressTestResults = new HashMap<>();
        
        // Memory stress test - very lightweight for Render.com
        try {
            stressTestResults.put("memoryStressTest", performLightMemoryStressTest());
        } catch (Exception e) {
            log.warn("Memory stress test failed: {}", e.getMessage());
            stressTestResults.put("memoryStressTestError", e.getMessage());
        }
        
        // CPU stress test - very lightweight for Render.com
        try {
            stressTestResults.put("cpuStressTest", performLightCpuStressTest());
        } catch (Exception e) {
            log.warn("CPU stress test failed: {}", e.getMessage());
            stressTestResults.put("cpuStressTestError", e.getMessage());
        }
        
        vmCapacity.put("stressTest", stressTestResults);
        log.info("VM stress test completed");
        
        // Log updated capacity information
        logCapacityInfo();
    }
    
    /**
     * Perform lightweight memory stress test for Render.com
     * 
     * @return Memory stress test results
     */
    private Map<String, Object> performLightMemoryStressTest() {
        Map<String, Object> results = new HashMap<>();
        
        // Start with current free memory
        Runtime runtime = Runtime.getRuntime();
        long initialFreeMemory = runtime.freeMemory();
        results.put("initialFreeMemoryMB", initialFreeMemory / (1024 * 1024));
        
        // Try to allocate memory in smaller chunks
        long allocatedBytes = 0;
        int chunkSizeBytes = 512 * 1024; // 512KB chunks
        int maxChunks = 10; // Very limited for Render.com
        
        try {
            // Use a list to keep references to allocated memory
            java.util.List<byte[]> memoryChunks = new java.util.ArrayList<>();
            
            for (int i = 0; i < maxChunks; i++) {
                byte[] chunk = new byte[chunkSizeBytes];
                // Write to the array to ensure it's actually allocated
                for (int j = 0; j < chunk.length; j += 1024) {
                    chunk[j] = 1;
                }
                memoryChunks.add(chunk);
                allocatedBytes += chunkSizeBytes;
                
                // Check if we're getting close to OOM
                if (runtime.freeMemory() < chunkSizeBytes * 2) {
                    break;
                }
            }
            
            results.put("allocatedMemoryMB", allocatedBytes / (1024 * 1024));
            results.put("remainingFreeMemoryMB", runtime.freeMemory() / (1024 * 1024));
            
            // Clear the list to free memory
            memoryChunks.clear();
            System.gc();
            
        } catch (OutOfMemoryError e) {
            results.put("outOfMemoryAt", allocatedBytes / (1024 * 1024));
            results.put("error", "OutOfMemoryError");
            
            // Force GC to recover
            System.gc();
        }
        
        // Record final state
        results.put("finalFreeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        
        return results;
    }
    
    /**
     * Perform lightweight CPU stress test for Render.com
     * 
     * @return CPU stress test results
     */
    private Map<String, Object> performLightCpuStressTest() {
        Map<String, Object> results = new HashMap<>();
        
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        results.put("availableProcessors", availableProcessors);
        
        // Create a thread pool with one thread per processor
        ExecutorService executor = Executors.newFixedThreadPool(availableProcessors);
        
        // Record start time
        long startTime = System.currentTimeMillis();
        
        // Start CPU-intensive tasks - very short duration for Render.com
        for (int i = 0; i < availableProcessors; i++) {
            executor.submit(() -> {
                long count = 0;
                long endTime = System.currentTimeMillis() + 1000; // 1 second test
                
                while (System.currentTimeMillis() < endTime) {
                    // Lighter CPU calculation
                    for (int j = 2; j < 1000; j++) {
                        boolean isPrime = true;
                        for (int k = 2; k <= Math.sqrt(j); k++) {
                            if (j % k == 0) {
                                isPrime = false;
                                break;
                            }
                        }
                        if (isPrime) {
                            count++;
                        }
                    }
                }
                
                return count;
            });
        }
        
        // Shutdown the executor and wait for tasks to complete
        executor.shutdown();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Record end time
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        results.put("testDurationMs", duration);
        
        // Try to get CPU load after test
        try {
            Class<?> sunOsClass = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (sunOsClass.isInstance(osMXBean)) {
                Object sunOsMBean = sunOsClass.cast(osMXBean);
                java.lang.reflect.Method method = sunOsClass.getMethod("getSystemCpuLoad");
                double systemCpuLoad = (Double) method.invoke(sunOsMBean);
                results.put("systemCpuLoadAfterTest", systemCpuLoad);
                
                method = sunOsClass.getMethod("getProcessCpuLoad");
                double processCpuLoad = (Double) method.invoke(sunOsMBean);
                results.put("processCpuLoadAfterTest", processCpuLoad);
            }
        } catch (Exception e) {
            log.debug("Could not get CPU load after test: {}", e.getMessage());
        }
        
        return results;
    }
    
    /**
     * Log capacity information
     */
    private void logCapacityInfo() {
        log.info("VM Capacity Information:");
        
        // Memory info
        Map<String, Object> memoryStats = (Map<String, Object>) vmCapacity.get("memory");
        if (memoryStats != null) {
            log.info("Memory: max={}MB, total={}MB, free={}MB, used={}MB",
                    memoryStats.get("maxMemoryMB"),
                    memoryStats.get("totalMemoryMB"),
                    memoryStats.get("freeMemoryMB"),
                    memoryStats.get("usedMemoryMB"));
            
            log.info("Heap: init={}MB, used={}MB, committed={}MB, max={}MB",
                    memoryStats.get("heapInitMB"),
                    memoryStats.get("heapUsedMB"),
                    memoryStats.get("heapCommittedMB"),
                    memoryStats.get("heapMaxMB"));
            
            log.info("Non-Heap: init={}MB, used={}MB, committed={}MB, max={}MB",
                    memoryStats.get("nonHeapInitMB"),
                    memoryStats.get("nonHeapUsedMB"),
                    memoryStats.get("nonHeapCommittedMB"),
                    memoryStats.get("nonHeapMaxMB"));
        }
        
        // CPU info
        Map<String, Object> cpuStats = (Map<String, Object>) vmCapacity.get("cpu");
        if (cpuStats != null) {
            log.info("CPU: processors={}, systemLoadAverage={}",
                    cpuStats.get("availableProcessors"),
                    cpuStats.get("systemLoadAverage"));
            
            if (cpuStats.containsKey("processCpuLoad")) {
                log.info("CPU Load: process={}, system={}",
                        cpuStats.get("processCpuLoad"),
                        cpuStats.get("systemCpuLoad"));
            }
            
            if (cpuStats.containsKey("totalPhysicalMemoryMB")) {
                log.info("Physical Memory: total={}MB, free={}MB",
                        cpuStats.get("totalPhysicalMemoryMB"),
                        cpuStats.get("freePhysicalMemoryMB"));
            }
        }
        
        // Disk info
        Map<String, Object> diskStats = (Map<String, Object>) vmCapacity.get("disk");
        if (diskStats != null) {
            log.info("Disk: total={}MB, free={}MB, usable={}MB",
                    diskStats.get("totalSpaceMB"),
                    diskStats.get("freeSpaceMB"),
                    diskStats.get("usableSpaceMB"));
        }
        
        // Environment info
        Map<String, Object> envStats = (Map<String, Object>) vmCapacity.get("environment");
        if (envStats != null) {
            log.info("Environment: OS={} {} ({}), Java={} ({})",
                    envStats.get("osName"),
                    envStats.get("osVersion"),
                    envStats.get("osArch"),
                    envStats.get("javaVersion"),
                    envStats.get("javaVendor"));
        }
        
        // Recommendations
        Map<String, Object> recommendations = (Map<String, Object>) vmCapacity.get("recommendations");
        if (recommendations != null) {
            log.info("Recommended Settings: workerThreads={}, ioThreads={}, bufferSize={}, directBuffers={}",
                    recommendations.get("undertowWorkerThreads"),
                    recommendations.get("undertowIoThreads"),
                    recommendations.get("undertowBufferSize"),
                    recommendations.get("undertowDirectBuffers"));
            
            log.info("Memory Recommendations: targetMB={}, highThreshold={}%, criticalThreshold={}%",
                    recommendations.get("memoryTargetMB"),
                    recommendations.get("memoryHighThreshold"),
                    recommendations.get("memoryCriticalThreshold"));
        }
    }
    
    /**
     * Get VM capacity information
     * 
     * @return VM capacity information
     */
    public Map<String, Object> getCapacityInfo() {
        return vmCapacity;
    }
} 