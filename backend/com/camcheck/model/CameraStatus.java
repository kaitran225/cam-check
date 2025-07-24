package com.camcheck.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Camera status model
 * Simplified version for client-only camera usage
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CameraStatus {
    private boolean streaming;
    private boolean motionDetectionEnabled;
    private boolean fallbackMode;
    
    /**
     * Create a default status with all features disabled
     * @return CameraStatus with default values
     */
    public static CameraStatus getDefault() {
        return new CameraStatus(false, false, false);
    }
} 