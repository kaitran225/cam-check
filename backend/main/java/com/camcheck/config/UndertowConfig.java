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

    @Value("${server.undertow.worker-threads}")
    private int workerThreads;
    
    @Value("${server.undertow.io-threads}")
    private int ioThreads;
    
    @Value("${server.undertow.buffer-size}")
    private int bufferSize;
    
    @Value("${server.undertow.direct-buffers}")
    private boolean directBuffers;
    
    @Value("${server.undertow.max-http-post-size}")
    private long maxHttpPostSize;
    
    @Value("${server.undertow.options.socket.TCP_NODELAY}")
    private boolean tcpNoDelay;
    
    @Value("${server.undertow.options.socket.REUSE_ADDRESSES}")
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