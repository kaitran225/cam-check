package com.camcheck.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

/**
 * Configuration for environment variables and profiles
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
        if (activeProfiles.length == 0) {
            activeProfiles = new String[]{"default"};
        }
        
        log.info("Active profiles: {}", String.join(", ", activeProfiles));
        log.info("Available profiles: {}", String.join(", ", env.getDefaultProfiles()));
        
        // Log server configuration
        log.info("Server running on {}:{}", 
                env.getProperty("server.address", "0.0.0.0"), 
                env.getProperty("server.port", "8080"));
        
        // Log database configuration
        log.info("Database URL: {}", env.getProperty("spring.datasource.url"));
        
        // Log chat enabled status
        log.info("Chat feature enabled");
    }
    
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
} 