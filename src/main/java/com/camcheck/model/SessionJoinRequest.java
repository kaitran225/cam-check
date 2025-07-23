package com.camcheck.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session join request model
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionJoinRequest {
    private String sessionCode;
    private String deviceId;
    private boolean audioEnabled = true;
    private boolean videoEnabled = true;
    private String quality = "medium"; // low, medium, high
} 