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
 */
@Service
@Slf4j
public class VMCapacityMeasurementService {

    @Value("${vm.capacity.measure:true}")
    private boolean measureCapacity;
    
    @Value("${vm.capacity.stress-test:false}")
    private boolean performStressTest;
    
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
        log.info("VM Capacity Measurement Service initialized");
        vmCapacity.put("jvmArgs", runtimeMXBean.getInputArguments());
        vmCapacity.put("jvmName", runtimeMXBean.getVmName());
        vmCapacity.put("jvmVendor", runtimeMXBean.getVmVendor());
        vmCapacity.put("jvmVersion", runtimeMXBean.getVmVersion());
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationStartup() {
        if (measureCapacity) {
            log.info("Starting VM capacity measurement");
            measureBasicCapacity();
            
            if (performStressTest) {
                // Run stress test in a separate thread to not block startup
                CompletableFuture.runAsync(this::performStressTest);
            }
        }
    }
    
    /**
     * Measure basic VM capacity
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
        
        // Measure CPU
        Map<String, Object> cpuStats = new HashMap<>();
        cpuStats.put("availableProcessors", runtime.availableProcessors());
        cpuStats.put("systemLoadAverage", osMXBean.getSystemLoadAverage());
        
        // Use reflection to access com.sun.management.OperatingSystemMXBean methods if available
        try {
            Class<?> sunOsClass = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (sunOsClass.isInstance(osMXBean)) {
                Object sunOsMXBean = sunOsClass.cast(osMXBean);
                
                // CPU time
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getProcessCpuTime");
                    long processCpuTime = (Long) method.invoke(sunOsMXBean);
                    cpuStats.put("processCpuTimeNs", processCpuTime);
                    cpuStats.put("processCpuTimeMs", processCpuTime / 1_000_000);
                } catch (Exception e) {
                    log.debug("Could not get process CPU time: {}", e.getMessage());
                }
                
                // CPU load
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getProcessCpuLoad");
                    double processCpuLoad = (Double) method.invoke(sunOsMXBean);
                    cpuStats.put("processCpuLoad", processCpuLoad);
                } catch (Exception e) {
                    log.debug("Could not get process CPU load: {}", e.getMessage());
                }
                
                // System CPU load
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getSystemCpuLoad");
                    double systemCpuLoad = (Double) method.invoke(sunOsMXBean);
                    cpuStats.put("systemCpuLoad", systemCpuLoad);
                } catch (Exception e) {
                    log.debug("Could not get system CPU load: {}", e.getMessage());
                }
                
                // Physical memory
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getTotalPhysicalMemorySize");
                    long totalPhysicalMemory = (Long) method.invoke(sunOsMXBean);
                    cpuStats.put("totalPhysicalMemoryMB", totalPhysicalMemory / (1024 * 1024));
                } catch (Exception e) {
                    log.debug("Could not get total physical memory: {}", e.getMessage());
                }
                
                // Free physical memory
                try {
                    java.lang.reflect.Method method = sunOsClass.getMethod("getFreePhysicalMemorySize");
                    long freePhysicalMemory = (Long) method.invoke(sunOsMXBean);
                    cpuStats.put("freePhysicalMemoryMB", freePhysicalMemory / (1024 * 1024));
                } catch (Exception e) {
                    log.debug("Could not get free physical memory: {}", e.getMessage());
                }
            }
        } catch (ClassNotFoundException e) {
            log.debug("com.sun.management.OperatingSystemMXBean not available");
        }
        
        vmCapacity.put("cpu", cpuStats);
        
        // Measure disk
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
        
        vmCapacity.put("environment", envStats);
        
        // Log capacity information
        logCapacityInfo();
        
        measurementComplete = true;
    }
    
    /**
     * Perform a stress test to measure maximum capacity
     * This is optional and only runs if vm.capacity.stress-test=true
     */
    private void performStressTest() {
        log.info("Starting VM stress test to measure capacity");
        
        Map<String, Object> stressTestResults = new HashMap<>();
        
        // Memory stress test
        try {
            stressTestResults.put("memoryStressTest", performMemoryStressTest());
        } catch (Exception e) {
            log.warn("Memory stress test failed: {}", e.getMessage());
            stressTestResults.put("memoryStressTestError", e.getMessage());
        }
        
        // CPU stress test
        try {
            stressTestResults.put("cpuStressTest", performCpuStressTest());
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
     * Perform memory stress test
     * 
     * @return Memory stress test results
     */
    private Map<String, Object> performMemoryStressTest() {
        Map<String, Object> results = new HashMap<>();
        
        // Start with current free memory
        Runtime runtime = Runtime.getRuntime();
        long initialFreeMemory = runtime.freeMemory();
        results.put("initialFreeMemoryMB", initialFreeMemory / (1024 * 1024));
        
        // Try to allocate memory in chunks until OutOfMemoryError
        long allocatedBytes = 0;
        int chunkSizeBytes = 1024 * 1024; // 1MB chunks
        int maxChunks = 100; // Safety limit
        
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
     * Perform CPU stress test
     * 
     * @return CPU stress test results
     */
    private Map<String, Object> performCpuStressTest() {
        Map<String, Object> results = new HashMap<>();
        
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        results.put("availableProcessors", availableProcessors);
        
        // Create a thread pool with one thread per processor
        ExecutorService executor = Executors.newFixedThreadPool(availableProcessors);
        
        // Record start time
        long startTime = System.currentTimeMillis();
        
        // Start CPU-intensive tasks
        for (int i = 0; i < availableProcessors; i++) {
            executor.submit(() -> {
                long count = 0;
                long endTime = System.currentTimeMillis() + 5000; // 5 second test
                
                while (System.currentTimeMillis() < endTime) {
                    // CPU-intensive calculation (calculate prime numbers)
                    for (int j = 2; j < 10000; j++) {
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
            executor.awaitTermination(10, TimeUnit.SECONDS);
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
                Object sunOsMXBean = sunOsClass.cast(osMXBean);
                java.lang.reflect.Method method = sunOsClass.getMethod("getSystemCpuLoad");
                double systemCpuLoad = (Double) method.invoke(sunOsMXBean);
                results.put("systemCpuLoadAfterTest", systemCpuLoad);
                
                method = sunOsClass.getMethod("getProcessCpuLoad");
                double processCpuLoad = (Double) method.invoke(sunOsMXBean);
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
        
        // Stress test results if available
        Map<String, Object> stressTest = (Map<String, Object>) vmCapacity.get("stressTest");
        if (stressTest != null) {
            Map<String, Object> memoryStressTest = (Map<String, Object>) stressTest.get("memoryStressTest");
            if (memoryStressTest != null) {
                log.info("Memory Stress Test: allocated={}MB, remaining={}MB",
                        memoryStressTest.get("allocatedMemoryMB"),
                        memoryStressTest.get("remainingFreeMemoryMB"));
            }
            
            Map<String, Object> cpuStressTest = (Map<String, Object>) stressTest.get("cpuStressTest");
            if (cpuStressTest != null) {
                log.info("CPU Stress Test: duration={}ms, processors={}",
                        cpuStressTest.get("testDurationMs"),
                        cpuStressTest.get("availableProcessors"));
                
                if (cpuStressTest.containsKey("systemCpuLoadAfterTest")) {
                    log.info("CPU Load After Test: process={}, system={}",
                            cpuStressTest.get("processCpuLoadAfterTest"),
                            cpuStressTest.get("systemCpuLoadAfterTest"));
                }
            }
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