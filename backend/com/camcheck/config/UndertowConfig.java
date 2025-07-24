package com.camcheck.config;

import io.undertow.Undertow;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Custom configuration for Undertow web server
 * Optimizes performance and resource usage
 */
@Configuration
@Slf4j
public class UndertowConfig implements WebServerFactoryCustomizer<UndertowServletWebServerFactory> {

    @Value("${UNDERTOW_WORKER_THREADS:#{T(java.lang.Math).max(4, T(java.lang.Runtime).getRuntime().availableProcessors())}}")
    private int workerThreads;
    
    @Value("${UNDERTOW_IO_THREADS:#{T(java.lang.Math).max(2, T(java.lang.Runtime).getRuntime().availableProcessors() / 2)}}")
    private int ioThreads;
    
    @Value("${UNDERTOW_BUFFER_SIZE:16384}")
    private int bufferSize;
    
    @Value("${UNDERTOW_DIRECT_BUFFERS:true}")
    private boolean directBuffers;
    
    @Value("${UNDERTOW_MAX_ENTITY_SIZE:10485760}")
    private long maxHttpPostSize;
    
    @Value("${UNDERTOW_TCP_NODELAY:true}")
    private boolean tcpNoDelay;
    
    @Value("${UNDERTOW_REUSE_ADDRESSES:true}")
    private boolean reuseAddresses;
    
    @Value("${UNDERTOW_MAX_CONNECTIONS:8192}")
    private int maxConnections;
    
    @Value("${UNDERTOW_URL_CHARSET:UTF-8}")
    private String urlCharset;
    
    @Value("${UNDERTOW_BACKLOG:1024}")
    private int backlog;
    
    @Value("${UNDERTOW_MAX_HEADERS:200}")
    private int maxHeaders;
    
    @Value("${UNDERTOW_MAX_PARAMETERS:1000}")
    private int maxParameters;
    
    @Value("${UNDERTOW_MAX_COOKIES:200}")
    private int maxCookies;
    
    @Value("${UNDERTOW_ENABLE_HTTP2:true}")
    private boolean enableHttp2;
    
    @Value("${LOW_RESOURCE_MODE:false}")
    private boolean lowResourceMode;
    
    @Override
    public void customize(UndertowServletWebServerFactory factory) {
        log.info("Customizing Undertow configuration");
        
        // If in low resource mode, adjust settings
        if (lowResourceMode) {
            workerThreads = Math.min(workerThreads, 8);
            ioThreads = Math.min(ioThreads, 2);
            bufferSize = 8192;
            maxConnections = 1000;
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
            
            // Connection pool options
            builder.setSocketOption(Options.CONNECTION_HIGH_WATER, maxConnections);
            builder.setSocketOption(Options.CONNECTION_LOW_WATER, maxConnections / 2);
            
            // Server options
            builder.setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true);
            builder.setServerOption(UndertowOptions.ALWAYS_SET_DATE, true);
            builder.setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, true);
            builder.setServerOption(UndertowOptions.MAX_ENTITY_SIZE, maxHttpPostSize);
            builder.setServerOption(UndertowOptions.URL_CHARSET, urlCharset);
            builder.setServerOption(UndertowOptions.MAX_HEADERS, maxHeaders);
            builder.setServerOption(UndertowOptions.MAX_PARAMETERS, maxParameters);
            builder.setServerOption(UndertowOptions.MAX_COOKIES, maxCookies);
            builder.setServerOption(UndertowOptions.DECODE_URL, true);
            
            // HTTP/2 support
            if (enableHttp2) {
                builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
            }
            
            // Performance tuning
            builder.setServerOption(UndertowOptions.MAX_BUFFERED_REQUEST_SIZE, 16384);
            
            // Low memory usage optimizations if needed
            if (lowResourceMode) {
                builder.setServerOption(UndertowOptions.BUFFER_PIPELINED_DATA, false);
            }
            
            log.info("Undertow configured with: workerThreads={}, ioThreads={}, bufferSize={}, directBuffers={}, maxConnections={}", 
                    workerThreads, ioThreads, bufferSize, directBuffers, maxConnections);
        });
        
        // Configure deployment options
        factory.addDeploymentInfoCustomizers(deploymentInfo -> {
            // Configure static resource handling for better performance
            File staticResourcesLocation = new File("src/main/resources/static");
            if (staticResourcesLocation.exists()) {
                deploymentInfo.setResourceManager(
                        new FileResourceManager(staticResourcesLocation, 100));
            }
            
            // Performance tuning
            deploymentInfo.setEagerFilterInit(true);
            
            // Session configuration
            deploymentInfo.setDefaultSessionTimeout(30 * 60); // 30 minutes
            
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