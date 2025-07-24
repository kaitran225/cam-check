package com.camcheck.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Custom error controller to handle error pages
 */
@Controller
@Slf4j
public class CustomErrorController implements ErrorController {

    /**
     * Handle error requests
     *
     * @param request HTTP request
     * @param model   Model for the view
     * @return Error view name
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String errorMessage = "An unexpected error occurred";
        String errorTitle = "Error";
        
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            
            log.error("Error occurred with status code: {}", statusCode);
            
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                errorTitle = "Page Not Found";
                errorMessage = "The page you are looking for does not exist.";
                model.addAttribute("showHomeLink", true);
            } else if (statusCode == HttpStatus.FORBIDDEN.value()) {
                errorTitle = "Access Denied";
                errorMessage = "You don't have permission to access this page.";
                model.addAttribute("showLoginLink", true);
            } else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                errorTitle = "Server Error";
                errorMessage = "Something went wrong on our end. Please try again later.";
            }
            
            model.addAttribute("statusCode", statusCode);
        }
        
        model.addAttribute("errorTitle", errorTitle);
        model.addAttribute("errorMessage", errorMessage);
        
        return "error";
    }
} 