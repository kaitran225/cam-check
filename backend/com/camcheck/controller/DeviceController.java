package com.camcheck.controller;

import com.camcheck.model.ApiResponse;
import com.camcheck.model.DeviceRegistration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for device registration and management
 */
@RestController
@RequestMapping("/api/v2/devices")
@Tag(name = "Device Management", description = "API endpoints for device registration and management")
@Slf4j
public class DeviceController {

    // In a real implementation, this would be stored in a database
    // For this example, we'll use an in-memory map
    private static final Map<String, Map<String, DeviceRegistration>> userDevices = new ConcurrentHashMap<>();

    /**
     * Register a device
     *
     * @param request Device registration request
     * @param authentication Authentication object
     * @return API response
     */
    @PostMapping("/register")
    @Operation(summary = "Register device", description = "Register a device with user account")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Device registered successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> registerDevice(
            @RequestBody DeviceRegistration request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        String username = authentication.getName();
        log.info("Device registration request for user: {}", username);
        
        // Update device registration with current user and timestamp
        request.setUsername(username);
        request.setLastSeen(Instant.now());
        request.setIpAddress(httpRequest.getRemoteAddr());
        
        // Store device registration
        userDevices.computeIfAbsent(username, k -> new ConcurrentHashMap<>())
                .put(request.getDeviceId(), request);
        
        log.info("Device registered for user {}: {} ({})", username, request.getDeviceId(), request.getDeviceName());
        
        // Return success response
        return ResponseEntity.ok(ApiResponse.success("Device registered successfully"));
    }
    
    /**
     * Update device information
     *
     * @param deviceId Device ID
     * @param request Device registration request
     * @param authentication Authentication object
     * @return API response
     */
    @PutMapping("/{deviceId}")
    @Operation(summary = "Update device", description = "Update device information")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Device updated successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Device not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> updateDevice(
            @PathVariable String deviceId,
            @RequestBody DeviceRegistration request,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Device update request for user: {}, device: {}", username, deviceId);
        
        Map<String, DeviceRegistration> devices = userDevices.get(username);
        if (devices == null || !devices.containsKey(deviceId)) {
            log.warn("Device not found for user: {}, device: {}", username, deviceId);
            return ResponseEntity.notFound().build();
        }
        
        // Update existing device with new information
        DeviceRegistration existingDevice = devices.get(deviceId);
        request.setUsername(username);
        request.setLastSeen(Instant.now());
        request.setDeviceId(deviceId); // Ensure deviceId matches path variable
        
        // Merge updates but keep original registration data
        if (request.getDeviceName() != null) {
            existingDevice.setDeviceName(request.getDeviceName());
        }
        if (request.getDeviceType() != null) {
            existingDevice.setDeviceType(request.getDeviceType());
        }
        if (request.getOsVersion() != null) {
            existingDevice.setOsVersion(request.getOsVersion());
        }
        if (request.getAppVersion() != null) {
            existingDevice.setAppVersion(request.getAppVersion());
        }
        if (request.getFcmToken() != null) {
            existingDevice.setFcmToken(request.getFcmToken());
        }
        existingDevice.setPushNotificationsEnabled(request.isPushNotificationsEnabled());
        if (request.getTimezone() != null) {
            existingDevice.setTimezone(request.getTimezone());
        }
        if (request.getDeviceSettings() != null) {
            existingDevice.setDeviceSettings(request.getDeviceSettings());
        }
        
        // Save updated device
        devices.put(deviceId, existingDevice);
        
        log.debug("Device updated for user {}: {}", username, deviceId);
        
        return ResponseEntity.ok(ApiResponse.success("Device updated successfully"));
    }
    
    /**
     * Get registered devices
     *
     * @param authentication Authentication object
     * @return API response with registered devices
     */
    @GetMapping
    @Operation(summary = "Get registered devices", description = "Get list of registered devices for current user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Devices retrieved successfully", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getRegisteredDevices(Authentication authentication) {
        String username = authentication.getName();
        log.debug("Getting registered devices for user: {}", username);
        
        Map<String, DeviceRegistration> devices = userDevices.getOrDefault(username, new HashMap<>());
        List<DeviceRegistration> deviceList = new ArrayList<>(devices.values());
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("devices", deviceList);
        responseData.put("count", deviceList.size());
        
        return ResponseEntity.ok(ApiResponse.success("Devices retrieved", responseData));
    }
    
    /**
     * Get device information
     *
     * @param deviceId Device ID
     * @param authentication Authentication object
     * @return API response with device information
     */
    @GetMapping("/{deviceId}")
    @Operation(summary = "Get device", description = "Get device information by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Device retrieved successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Device not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> getDevice(
            @PathVariable String deviceId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Getting device information for user: {}, device: {}", username, deviceId);
        
        Map<String, DeviceRegistration> devices = userDevices.get(username);
        if (devices == null || !devices.containsKey(deviceId)) {
            log.warn("Device not found for user: {}, device: {}", username, deviceId);
            return ResponseEntity.notFound().build();
        }
        
        DeviceRegistration device = devices.get(deviceId);
        
        return ResponseEntity.ok(ApiResponse.success("Device retrieved", Map.of("device", device)));
    }
    
    /**
     * Unregister a device
     *
     * @param deviceId Device ID
     * @param authentication Authentication object
     * @return API response
     */
    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Unregister device", description = "Unregister a device by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Device unregistered successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Device not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> unregisterDevice(
            @PathVariable String deviceId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Device unregistration request for user: {}, device: {}", username, deviceId);
        
        Map<String, DeviceRegistration> devices = userDevices.get(username);
        if (devices == null || !devices.containsKey(deviceId)) {
            log.warn("Device not found for user: {}, device: {}", username, deviceId);
            return ResponseEntity.notFound().build();
        }
        
        // Remove device
        devices.remove(deviceId);
        
        // If no devices left, remove user entry
        if (devices.isEmpty()) {
            userDevices.remove(username);
        }
        
        log.info("Device unregistered for user {}: {}", username, deviceId);
        
        return ResponseEntity.ok(ApiResponse.success("Device unregistered successfully"));
    }
    
    /**
     * Update device FCM token
     *
     * @param deviceId Device ID
     * @param requestBody Request body containing FCM token
     * @param authentication Authentication object
     * @return API response
     */
    @PutMapping("/{deviceId}/fcm-token")
    @Operation(summary = "Update FCM token", description = "Update Firebase Cloud Messaging token for a device")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "FCM token updated successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Device not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> updateFcmToken(
            @PathVariable String deviceId,
            @RequestBody Map<String, String> requestBody,
            Authentication authentication) {
        
        String username = authentication.getName();
        String fcmToken = requestBody.get("fcmToken");
        
        if (fcmToken == null || fcmToken.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("FCM token is required"));
        }
        
        log.debug("FCM token update request for user: {}, device: {}", username, deviceId);
        
        Map<String, DeviceRegistration> devices = userDevices.get(username);
        if (devices == null || !devices.containsKey(deviceId)) {
            log.warn("Device not found for user: {}, device: {}", username, deviceId);
            return ResponseEntity.notFound().build();
        }
        
        // Update FCM token
        DeviceRegistration device = devices.get(deviceId);
        device.setFcmToken(fcmToken);
        device.setLastSeen(Instant.now());
        devices.put(deviceId, device);
        
        log.debug("FCM token updated for user {}, device: {}", username, deviceId);
        
        return ResponseEntity.ok(ApiResponse.success("FCM token updated successfully"));
    }
} 