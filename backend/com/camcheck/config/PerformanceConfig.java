package com.camcheck.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for optimizing Spring Boot performance
 */
@Configuration
@EnableCaching
@EnableAsync
@Slf4j
public class PerformanceConfig implements WebMvcConfigurer {

    @Value("${performance.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${performance.async.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${performance.async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${performance.async.thread-name-prefix:async-}")
    private String threadNamePrefix;

    @Value("${performance.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${LOW_RESOURCE_MODE:false}")
    private boolean lowResourceMode;

    /**
     * Configure async support for MVC
     */
    @Override
    public void configureAsyncSupport(@NonNull AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(30000); // 30 seconds
        configurer.setTaskExecutor(asyncTaskExecutor());
    }

    /**
     * Configure task executor for async operations
     */
    @Bean
    @Primary
    public AsyncTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Adjust thread pool size based on resource mode
        if (lowResourceMode) {
            executor.setCorePoolSize(Math.min(2, corePoolSize));
            executor.setMaxPoolSize(Math.min(4, maxPoolSize));
            executor.setQueueCapacity(Math.min(50, queueCapacity));
        } else {
            executor.setCorePoolSize(corePoolSize);
            executor.setMaxPoolSize(maxPoolSize);
            executor.setQueueCapacity(queueCapacity);
        }
        
        executor.setThreadNamePrefix(threadNamePrefix);
        
        // Rejection policy: caller runs the task if queue is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Allow core threads to time out
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60); // 1 minute
        
        log.info("Configured task executor with corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * Configure cache manager
     */
    @Bean
    @ConditionalOnProperty(name = "performance.cache.enabled", havingValue = "true", matchIfMissing = true)
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager() {
            @SuppressWarnings("null")
            @Override
            protected ConcurrentMapCache createConcurrentMapCache(@NonNull String name) {
                return new ConcurrentMapCache(name, 
                    // Use a ConcurrentHashMap with initial capacity and load factor
                    new java.util.concurrent.ConcurrentHashMap<>(256, 0.75f, 16), 
                    false);
            }
        };
        
        // Configure caches
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "settings", 
            "userProfiles", 
            "cameraSettings",
            "staticResources"
        ));
        
        log.info("Configured cache manager with {} caches", cacheManager.getCacheNames().size());
        
        return cacheManager;
    }
} 