package com.camcheck.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;

/**
 * Web MVC configuration for static resources
 */
@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Get the absolute path to the frontend/web directory
        Path frontendWebPath = Paths.get("frontend/web").toAbsolutePath();
        String frontendWebLocation = frontendWebPath.toString().replace("\\", "/");
        
        // Log the path for debugging
        log.info("Serving static resources from: {}", frontendWebLocation);
        
        // Configure the resource handler to serve files from frontend/web
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + frontendWebLocation + "/")
                .setCachePeriod(0)
                .resourceChain(true)
                .addResolver(new PathResourceResolver());
                
        // Add classpath resources as a fallback
        registry.addResourceHandler("/resources/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(0)
                .resourceChain(true)
                .addResolver(new PathResourceResolver());
    }
} 