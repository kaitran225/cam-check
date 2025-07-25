package com.camcheck.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.extern.slf4j.Slf4j;
import java.io.File;

/**
 * Controller for the home page
 */
@Controller
@Slf4j
public class HomeController {

    /**
     * Display the home page
     * 
     * @param model Model for the view
     * @return The home view name or forward to the static index.html
     */
    @GetMapping("/")
    public String home(Model model) {
        log.info("Home page requested");
        
        // Check if frontend/web/index.html exists
        File frontendIndex = new File("frontend/web/index.html");
        if (frontendIndex.exists()) {
            log.info("Serving frontend/web/index.html");
            // Return "forward:" to serve the static file directly
            return "forward:/index.html";
        }
        
        // Fall back to the template if the static file doesn't exist
        log.info("Falling back to index template");
        model.addAttribute("title", "CamCheck - Home");
        return "index";
    }
} 