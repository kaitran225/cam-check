package com.camcheck.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session creation request model
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionCreateRequest {
    private int expirationMinutes = 10; // Default 10 minutes
    private String deviceId;
    private boolean audioEnabled = true;
    private boolean videoEnabled = true;
    private String quality = "medium"; // low, medium, high
    private String recordingMode = "none"; // none, motion, continuous
} 