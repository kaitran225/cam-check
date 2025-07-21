package com.camcheck.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for client camera functionality
 * Now redirects to main page since functionality is consolidated
 */
@Controller
public class ClientCameraController {

    /**
     * Redirect client camera page to main page
     */
    @GetMapping("/client-camera")
    public String clientCamera() {
        return "client-camera"; // This now contains a redirect to the main page
    }
} 