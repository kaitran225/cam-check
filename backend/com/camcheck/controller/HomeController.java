package com.camcheck.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.extern.slf4j.Slf4j;

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
     * @return The home view name
     */
    @GetMapping("/")
    public String home(Model model) {
        log.info("Home page requested");
        model.addAttribute("title", "CamCheck - Home");
        return "index";
    }
} 