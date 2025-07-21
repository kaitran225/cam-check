package com.camcheck.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

/**
 * Configuration for environment variables
 * Loads variables from .env file and system environment
 */
@Configuration
@Slf4j
public class EnvConfig {

    private final Environment env;

    public EnvConfig(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void logEnvironmentInfo() {
        String[] activeProfiles = env.getActiveProfiles();
        log.info("Active profiles: {}", String.join(", ", activeProfiles.length > 0 ? activeProfiles : new String[]{"default"}));
        
        // Log server configuration
        log.info("Server running on {}:{}", 
                env.getProperty("SERVER_ADDRESS"), 
                env.getProperty("SERVER_PORT"));
        
        // Log Undertow configuration
        log.info("Undertow configured with worker threads: {}, IO threads: {}", 
                env.getProperty("UNDERTOW_WORKER_THREADS"),
                env.getProperty("UNDERTOW_IO_THREADS"));
    }
    
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
} 