package com.camcheck.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Device registration model
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceRegistration {
    private String deviceId;
    private String deviceName;
    private String deviceType; // "ANDROID", "IOS", "WEB"
    private String osVersion;
    private String appVersion;
    private String fcmToken;
    private boolean pushNotificationsEnabled;
    private String username;
    private Instant lastSeen;
    private String timezone;
    private Map<String, Object> deviceSettings;
    private String ipAddress;
} 