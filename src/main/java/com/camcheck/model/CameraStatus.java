package com.camcheck.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Camera status model
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Camera system status")
public class CameraStatus {
    
    @Schema(description = "Whether the camera is currently streaming", example = "true")
    private boolean streaming;
    
    @Schema(description = "Whether motion detection is enabled", example = "true")
    private boolean motionDetection;
    
    @Schema(description = "Whether the system is using fallback mode (no real camera available)", example = "false")
    private boolean fallbackMode;
} 