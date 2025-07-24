package com.camcheck.controller.v1;

import com.camcheck.model.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import java.util.Map;

/**
 * Base controller providing common functionality for all API endpoints
 */
@Slf4j
public abstract class BaseController {
    
    /**
     * Create a success response
     *
     * @param message Success message
     * @param data Response data
     * @return ResponseEntity with success response
     */
    protected ResponseEntity<ApiResponse> success(String message, Map<String, Object> data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    /**
     * Create a success response without data
     *
     * @param message Success message
     * @return ResponseEntity with success response
     */
    protected ResponseEntity<ApiResponse> success(String message) {
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    /**
     * Create an error response
     *
     * @param message Error message
     * @return ResponseEntity with error response
     */
    protected ResponseEntity<ApiResponse> error(String message) {
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    /**
     * Create an error response with status code
     *
     * @param message Error message
     * @param statusCode HTTP status code
     * @return ResponseEntity with error response
     */
    protected ResponseEntity<ApiResponse> error(String message, int statusCode) {
        return ResponseEntity.status(statusCode).body(ApiResponse.error(message));
    }
} 