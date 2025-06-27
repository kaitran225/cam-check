package com.camcheck.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response model
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Generic API response")
public class ApiResponse<T> {
    
    @Schema(description = "Response status", example = "success")
    private String status;
    
    @Schema(description = "Optional message")
    private String message;
    
    @Schema(description = "Response data")
    private T data;
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", null, data);
    }
    
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, data);
    }
    
    public static ApiResponse<Object> error(String message) {
        return new ApiResponse<>("error", message, null);
    }
} 