package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for sending push notifications to mobile devices
 * This is a stub implementation that will need to be connected to a real push notification service
 * such as Firebase Cloud Messaging (FCM) for Android or Apple Push Notification Service (APNS) for iOS
 */
@Service
@Slf4j
public class PushNotificationService {

    @Value("${camcheck.mobile.push-notifications.enabled:true}")
    private boolean pushNotificationsEnabled;
    
    @Value("${camcheck.mobile.push-notifications.firebase-project-id:}")
    private String firebaseProjectId;
    
    @Value("${camcheck.mobile.push-notifications.firebase-credentials-file:}")
    private String firebaseCredentialsFile;
    
    @Value("${camcheck.mobile.push-notifications.daily-limit:50}")
    private int dailyNotificationLimit;
    
    @Value("${camcheck.mobile.android-package-name:com.camcheck.app}")
    private String androidPackageName;
    
    @Value("${camcheck.mobile.ios-bundle-id:com.camcheck.app}")
    private String iosBundleId;
    
    @Value("${camcheck.mobile.push-notifications.quiet-hours.enabled:false}")
    private boolean quietHoursEnabled;
    
    @Value("${camcheck.mobile.push-notifications.quiet-hours.start:22:00}")
    private String quietHoursStart;
    
    @Value("${camcheck.mobile.push-notifications.quiet-hours.end:07:00}")
    private String quietHoursEnd;
    
    // Store FCM tokens: userId -> { deviceId -> token }
    private final Map<String, Map<String, String>> userFcmTokens = new ConcurrentHashMap<>();
    
    // Daily notification counter: userId -> count
    private final Map<String, Integer> dailyNotificationCount = new ConcurrentHashMap<>();
    
    /**
     * Register FCM token for a user's device
     *
     * @param userId User ID
     * @param deviceId Device ID
     * @param fcmToken FCM token
     */
    public void registerFcmToken(String userId, String deviceId, String fcmToken) {
        if (!pushNotificationsEnabled) {
            log.warn("Push notifications are disabled, ignoring FCM token registration");
            return;
        }
        
        log.info("Registering FCM token for user {} on device {}", userId, deviceId);
        
        Map<String, String> userTokens = userFcmTokens.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        userTokens.put(deviceId, fcmToken);
    }
    
    /**
     * Unregister FCM token for a user's device
     *
     * @param userId User ID
     * @param deviceId Device ID
     */
    public void unregisterFcmToken(String userId, String deviceId) {
        log.info("Unregistering FCM token for user {} on device {}", userId, deviceId);
        
        Map<String, String> userTokens = userFcmTokens.get(userId);
        if (userTokens != null) {
            userTokens.remove(deviceId);
            
            // Remove the user entry if no devices left
            if (userTokens.isEmpty()) {
                userFcmTokens.remove(userId);
            }
        }
    }
    
    /**
     * Send a notification to a user's devices
     *
     * @param userId User ID
     * @param title Notification title
     * @param body Notification body
     * @param data Additional data
     * @return True if notification was sent
     */
    @SuppressWarnings("unused")
    @Async
    public boolean sendNotification(String userId, String title, String body, Map<String, String> data) {
        if (!pushNotificationsEnabled) {
            log.warn("Push notifications are disabled, notification not sent");
            return false;
        }
        
        // Get user's tokens
        Map<String, String> userTokens = userFcmTokens.get(userId);
        if (userTokens == null || userTokens.isEmpty()) {
            log.warn("No FCM tokens found for user {}", userId);
            return false;
        }
        
        // Check daily limit
        int count = dailyNotificationCount.getOrDefault(userId, 0);
        if (count >= dailyNotificationLimit) {
            log.warn("Daily notification limit reached for user {}: {}/{}", userId, count, dailyNotificationLimit);
            return false;
        }
        
        // Check quiet hours
        if (isQuietHours()) {
            log.info("Not sending notification during quiet hours to user {}", userId);
            return false;
        }
        
        // Send notification to each device
        boolean anySuccess = false;
        
        for (Map.Entry<String, String> entry : userTokens.entrySet()) {
            String deviceId = entry.getKey();
            String token = entry.getValue();
            
            try {
                // In a real implementation, this would use the FCM API to send the notification
                // For now, we'll just log it
                log.info("Sending notification to user {} on device {}: {} - {}", userId, deviceId, title, body);
                log.debug("Notification data: {}", data);
                
                // Increment daily notification count
                dailyNotificationCount.compute(userId, (k, v) -> v == null ? 1 : v + 1);
                
                anySuccess = true;
            } catch (Exception e) {
                log.error("Failed to send notification to user {} on device {}", userId, deviceId, e);
            }
        }
        
        return anySuccess;
    }
    
    /**
     * Notify about a new session invitation
     *
     * @param userId User to notify
     * @param inviterId User who sent the invitation
     * @param sessionCode Session code
     */
    public void notifySessionInvitation(String userId, String inviterId, String sessionCode) {
        String title = "New Session Invitation";
        String body = String.format("%s has invited you to a camera viewing session", inviterId);
        
        Map<String, String> data = new HashMap<>();
        data.put("type", "session_invitation");
        data.put("inviterId", inviterId);
        data.put("sessionCode", sessionCode);
        
        sendNotification(userId, title, body, data);
    }
    
    /**
     * Notify about a missed call
     *
     * @param userId User to notify
     * @param callerId User who called
     */
    public void notifyMissedCall(String userId, String callerId) {
        String title = "Missed Camera Call";
        String body = String.format("You missed a camera call from %s", callerId);
        
        Map<String, String> data = new HashMap<>();
        data.put("type", "missed_call");
        data.put("callerId", callerId);
        
        sendNotification(userId, title, body, data);
    }
    
    /**
     * Notify about motion detection
     *
     * @param userId User to notify
     * @param cameraId Camera ID
     */
    public void notifyMotionDetected(String userId, String cameraId) {
        String title = "Motion Detected";
        String body = "Motion has been detected on your camera";
        
        Map<String, String> data = new HashMap<>();
        data.put("type", "motion_detected");
        data.put("cameraId", cameraId);
        
        sendNotification(userId, title, body, data);
    }
    
    /**
     * Notify about system alert
     *
     * @param userId User to notify
     * @param alertLevel Alert level (info, warning, error, critical)
     * @param message Alert message
     */
    public void notifySystemAlert(String userId, String alertLevel, String message) {
        String title = String.format("System %s", alertLevel.toUpperCase());
        
        Map<String, String> data = new HashMap<>();
        data.put("type", "system_alert");
        data.put("alertLevel", alertLevel);
        
        sendNotification(userId, title, message, data);
    }
    
    /**
     * Reset daily notification counters
     * This should be called daily, e.g. via a scheduled job
     */
    public void resetDailyCounters() {
        log.info("Resetting daily notification counters");
        dailyNotificationCount.clear();
    }
    
    /**
     * Check if current time is within quiet hours
     *
     * @return True if within quiet hours
     */
    private boolean isQuietHours() {
        if (!quietHoursEnabled) {
            return false;
        }
        
        // In a real implementation, this would check the current time against quiet hours
        // For now, we'll just return false
        return false;
    }
} 