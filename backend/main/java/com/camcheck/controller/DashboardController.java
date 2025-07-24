package com.camcheck.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for serving the analytics dashboard page
 */
@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    @Value("${camcheck.analytics.dashboard.enabled:true}")
    private boolean dashboardEnabled;
    
    @Value("${camcheck.analytics.dashboard.refresh-interval:60}")
    private int refreshInterval;
    
    @Value("${camcheck.analytics.dashboard.default-time-range:60}")
    private int defaultTimeRange;
    
    /**
     * Show the analytics dashboard
     * @param model Model for the view
     * @return View name
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String showDashboard(Model model) {
        if (!dashboardEnabled) {
            return "redirect:/";
        }
        
        model.addAttribute("refreshInterval", refreshInterval);
        model.addAttribute("defaultTimeRange", defaultTimeRange);
        
        return "dashboard";
    }
} 