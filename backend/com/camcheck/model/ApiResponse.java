package com.camcheck.model;

import java.util.Collections;
import java.util.Map;

/**
 * Standardized API response format
 */
public class ApiResponse {
    private String status;
    private String message;
    private Map<String, Object> data;
    
    public ApiResponse() {
        // Default constructor
    }
    
    public ApiResponse(String status, String message, Map<String, Object> data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }
    
    /**
     * Create a success response
     * @param message Success message
     * @return ApiResponse
     */
    public static ApiResponse success(String message) {
        return new ApiResponse("success", message, Collections.emptyMap());
    }
    
    /**
     * Create a success response with data
     * @param message Success message
     * @param data Response data
     * @return ApiResponse
     */
    public static ApiResponse success(String message, Map<String, Object> data) {
        return new ApiResponse("success", message, data);
    }
    
    /**
     * Create an error response
     * @param message Error message
     * @return ApiResponse
     */
    public static ApiResponse error(String message) {
        return new ApiResponse("error", message, Collections.emptyMap());
    }
    
    /**
     * Create an error response with data
     * @param message Error message
     * @param data Response data
     * @return ApiResponse
     */
    public static ApiResponse error(String message, Map<String, Object> data) {
        return new ApiResponse("error", message, data);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
} 