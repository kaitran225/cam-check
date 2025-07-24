package com.camcheck.config;

import io.undertow.UndertowOptions;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.MimeMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.xnio.Options;

import java.io.File;

/**
 * Custom configuration for Undertow web server
 * Optimized specifically for Render.com free tier (1 CPU, limited memory)
 */
@Configuration
@Slf4j
public class UndertowConfig implements WebServerFactoryCustomizer<UndertowServletWebServerFactory> {

    // Worker threads - default to 2 for single CPU environment
    @Value("${UNDERTOW_WORKER_THREADS:2}")
    private int workerThreads;
    
    // IO threads - default to 1 for single CPU environment
    @Value("${UNDERTOW_IO_THREADS:1}")
    private int ioThreads;
    
    // Smaller buffer size to reduce memory usage
    @Value("${UNDERTOW_BUFFER_SIZE:4096}")
    private int bufferSize;
    
    // Direct buffers can be more efficient but use off-heap memory
    @Value("${UNDERTOW_DIRECT_BUFFERS:false}")
    private boolean directBuffers;
    
    // Reduce max entity size for memory conservation
    @Value("${UNDERTOW_MAX_ENTITY_SIZE:1048576}")
    private long maxHttpPostSize;
    
    @Value("${UNDERTOW_TCP_NODELAY:true}")
    private boolean tcpNoDelay;
    
    @Value("${UNDERTOW_REUSE_ADDRESSES:true}")
    private boolean reuseAddresses;
    
    // Reduce max connections for memory conservation
    @Value("${UNDERTOW_MAX_CONNECTIONS:100}")
    private int maxConnections;
    
    @Value("${UNDERTOW_URL_CHARSET:UTF-8}")
    private String urlCharset;
    
    // Reduce backlog for memory conservation
    @Value("${UNDERTOW_BACKLOG:50}")
    private int backlog;
    
    // Reduce max headers for memory conservation
    @Value("${UNDERTOW_MAX_HEADERS:50}")
    private int maxHeaders;
    
    // Reduce max parameters for memory conservation
    @Value("${UNDERTOW_MAX_PARAMETERS:50}")
    private int maxParameters;
    
    // Reduce max cookies for memory conservation
    @Value("${UNDERTOW_MAX_COOKIES:20}")
    private int maxCookies;
    
    // Disable HTTP/2 to save memory
    @Value("${UNDERTOW_ENABLE_HTTP2:false}")
    private boolean enableHttp2;
    
    // Always enable low resource mode for Render.com
    @Value("${LOW_RESOURCE_MODE:true}")
    private boolean lowResourceMode;
    
    // Add idle timeout to release resources when inactive
    @Value("${UNDERTOW_IDLE_TIMEOUT:30000}")
    private int idleTimeout;
    
    // Add no request timeout to release resources when inactive
    @Value("${UNDERTOW_NO_REQUEST_TIMEOUT:60000}")
    private int noRequestTimeout;
    
    @Override
    public void customize(UndertowServletWebServerFactory factory) {
        log.info("Customizing Undertow configuration for Render.com environment");
        
        // Always apply low resource mode for Render.com
        if (lowResourceMode) {
            workerThreads = Math.min(workerThreads, 2); // Max 2 worker threads
            ioThreads = 1; // Always use 1 IO thread
            bufferSize = 4096; // Use smaller buffers
            maxConnections = 50; // Limit concurrent connections
            log.info("Applied low resource mode constraints to Undertow");
        }
        
        // Configure worker and I/O threads
        factory.setWorkerThreads(workerThreads);
        factory.setIoThreads(ioThreads);
        
        // Configure buffer settings
        factory.setBufferSize(bufferSize);
        factory.setUseDirectBuffers(directBuffers);
        
        // Configure socket options
        factory.addBuilderCustomizers(builder -> {
            // Socket options
            builder.setSocketOption(Options.TCP_NODELAY, tcpNoDelay);
            builder.setSocketOption(Options.REUSE_ADDRESSES, reuseAddresses);
            builder.setSocketOption(Options.BACKLOG, backlog);
            builder.setSocketOption(Options.KEEP_ALIVE, true);
            
            // Add timeout options
            builder.setSocketOption(Options.READ_TIMEOUT, idleTimeout);
            builder.setSocketOption(Options.WRITE_TIMEOUT, idleTimeout);
            
            // Connection pool options - very conservative
            builder.setSocketOption(Options.CONNECTION_HIGH_WATER, maxConnections);
            builder.setSocketOption(Options.CONNECTION_LOW_WATER, maxConnections / 2);
            
            // Server options
            builder.setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true);
            builder.setServerOption(UndertowOptions.ALWAYS_SET_DATE, true);
            builder.setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, false); // Disable for performance
            builder.setServerOption(UndertowOptions.MAX_ENTITY_SIZE, maxHttpPostSize);
            builder.setServerOption(UndertowOptions.URL_CHARSET, urlCharset);
            builder.setServerOption(UndertowOptions.MAX_HEADERS, maxHeaders);
            builder.setServerOption(UndertowOptions.MAX_PARAMETERS, maxParameters);
            builder.setServerOption(UndertowOptions.MAX_COOKIES, maxCookies);
            builder.setServerOption(UndertowOptions.DECODE_URL, true);
            
            // Timeouts
            builder.setServerOption(UndertowOptions.IDLE_TIMEOUT, idleTimeout);
            builder.setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, noRequestTimeout);
            
            // HTTP/2 support - disabled for memory conservation
            if (enableHttp2) {
                builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
            }
            
            // Performance tuning for low memory
            builder.setServerOption(UndertowOptions.MAX_BUFFERED_REQUEST_SIZE, 4096);
            builder.setServerOption(UndertowOptions.BUFFER_PIPELINED_DATA, false);
            builder.setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, false); // Don't always set keep-alive
            builder.setServerOption(UndertowOptions.ALWAYS_SET_DATE, false); // Don't always set date
            builder.setServerOption(UndertowOptions.MAX_CONCURRENT_REQUESTS_PER_CONNECTION, 1); // Limit concurrent requests
            
            log.info("Undertow configured with: workerThreads={}, ioThreads={}, bufferSize={}, directBuffers={}, maxConnections={}", 
                    workerThreads, ioThreads, bufferSize, directBuffers, maxConnections);
        });
        
        // Configure deployment options
        factory.addDeploymentInfoCustomizers(deploymentInfo -> {
            // Configure static resource handling for better performance
            File staticResourcesLocation = new File("src/main/resources/static");
            if (staticResourcesLocation.exists()) {
                deploymentInfo.setResourceManager(
                        new FileResourceManager(staticResourcesLocation, 50)); // Reduce file cache size
            }
            
            // Disable eager filter init to save startup memory
            deploymentInfo.setEagerFilterInit(false);
            
            // Session configuration - shorter timeout
            deploymentInfo.setDefaultSessionTimeout(15 * 60); // 15 minutes
            
            // Configure MIME types for better content handling
            addMimeMappings(deploymentInfo);
        });
    }
    
    /**
     * Add custom MIME mappings for modern file formats
     * 
     * @param deploymentInfo Deployment info
     */
    private void addMimeMappings(DeploymentInfo deploymentInfo) {
        // WebP image format
        deploymentInfo.addMimeMapping(new MimeMapping("webp", "image/webp"));
        
        // AVIF image format
        deploymentInfo.addMimeMapping(new MimeMapping("avif", "image/avif"));
        
        // Modern font formats
        deploymentInfo.addMimeMapping(new MimeMapping("woff2", "font/woff2"));
        
        // Modern audio formats
        deploymentInfo.addMimeMapping(new MimeMapping("opus", "audio/opus"));
    }
} 