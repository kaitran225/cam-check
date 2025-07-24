package com.camcheck.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * Controller for handling login requests
 */
@Controller
@Slf4j
public class LoginController {

    /**
     * Display the login page
     * 
     * @return The login view name
     */
    @GetMapping("/login")
    public String login(Model model) {
        log.info("Login page requested");
        
        // Debug template locations
        checkTemplateExists("templates/login.html");
        checkTemplateExists("static/login.html");
        
        model.addAttribute("title", "CamCheck - Login");
        return "login";
    }
    
    private void checkTemplateExists(String path) {
        try {
            Resource resource = new ClassPathResource(path);
            boolean exists = resource.exists();
            log.info("Template at '{}' exists: {}", path, exists);
            if (exists) {
                log.info("Template file is readable: {}", resource.isReadable());
            }
        } catch (Exception e) {
            log.error("Error checking template at '{}': {}", path, e.getMessage());
        }
    }
} 