package com.camcheck.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.extern.slf4j.Slf4j;

/**
 * MVC Configuration for view controllers
 */
@Configuration
@Slf4j
public class MvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        // Map URL paths to view names
        log.info("Configuring view controllers");
        registry.addViewController("/login").setViewName("login");
        registry.addViewController("/dashboard").setViewName("dashboard");
        
        // Note: We don't map "/" here because it's handled by the HomeController
        // or served directly from the frontend/web directory
    }
} 