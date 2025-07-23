package com.camcheck.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication request model
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthRequest {
    private String username;
    private String password;
    private String deviceId;
    private String deviceName;
    private String deviceType; // "ANDROID", "IOS", "WEB"
    private String fcmToken; // Firebase Cloud Messaging token for push notifications
} 