package com.camcheck.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.extern.slf4j.Slf4j;

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
    public String login() {
        log.info("Login page requested");
        return "login";
    }
} 