package com.camcheck.config;

import io.undertow.UndertowOptions;
import io.undertow.server.handlers.resource.FileResourceManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.xnio.Options;

import java.io.File;

/**
 * Custom configuration for Undertow web server
 * Optimizes performance and resource usage
 */
@Configuration
@Slf4j
public class UndertowConfig implements WebServerFactoryCustomizer<UndertowServletWebServerFactory> {

    @Value("${UNDERTOW_WORKER_THREADS}")
    private int workerThreads;
    
    @Value("${UNDERTOW_IO_THREADS}")
    private int ioThreads;
    
    @Value("${UNDERTOW_BUFFER_SIZE}")
    private int bufferSize;
    
    @Value("${UNDERTOW_DIRECT_BUFFERS}")
    private boolean directBuffers;
    
    @Value("${UNDERTOW_MAX_HTTP_POST_SIZE}")
    private long maxHttpPostSize;
    
    @Value("${UNDERTOW_TCP_NODELAY:true}")
    private boolean tcpNoDelay;
    
    @Value("${UNDERTOW_REUSE_ADDRESSES:true}")
    private boolean reuseAddresses;
    
    @Override
    public void customize(UndertowServletWebServerFactory factory) {
        log.info("Customizing Undertow configuration");
        
        // Configure worker and I/O threads
        factory.setWorkerThreads(workerThreads);
        factory.setIoThreads(ioThreads);
        
        // Configure buffer settings
        factory.setBufferSize(bufferSize);
        factory.setUseDirectBuffers(directBuffers);
        
        // Configure socket options
        factory.addBuilderCustomizers(builder -> {
            builder.setSocketOption(Options.TCP_NODELAY, tcpNoDelay);
            builder.setSocketOption(Options.REUSE_ADDRESSES, reuseAddresses);
            
            // Additional performance optimizations
            builder.setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true);
            builder.setServerOption(UndertowOptions.ALWAYS_SET_DATE, true);
            builder.setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, true);
            builder.setServerOption(UndertowOptions.MAX_ENTITY_SIZE, maxHttpPostSize);
            
            // Enable HTTP/2 if available
            builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
            
            log.info("Undertow configured with: workerThreads={}, ioThreads={}, bufferSize={}, directBuffers={}", 
                    workerThreads, ioThreads, bufferSize, directBuffers);
        });
        
        // Configure static resource handling for better performance
        factory.addDeploymentInfoCustomizers(deploymentInfo -> {
            File staticResourcesLocation = new File("src/main/resources/static");
            if (staticResourcesLocation.exists()) {
                deploymentInfo.setResourceManager(
                        new FileResourceManager(staticResourcesLocation, 100));
            }
        });
    }
} 