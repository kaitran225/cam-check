package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Service for mobile device detection and optimization
 */
@Service
@Slf4j
public class MobileOptimizationService {

    @Value("${camcheck.media.mobile.enabled:true}")
    private boolean mobileOptimizationEnabled;
    
    @Value("${camcheck.media.mobile.auto-detect:true}")
    private boolean autoDetectMobile;
    
    @Value("${camcheck.media.mobile.max-width:480}")
    private int mobileMaxWidth;
    
    @Value("${camcheck.media.mobile.max-height:360}")
    private int mobileMaxHeight;
    
    @Value("${camcheck.media.mobile.max-frame-rate:15}")
    private int mobileMaxFrameRate;
    
    @Value("${camcheck.media.mobile.compression-quality:0.7}")
    private double mobileCompressionQuality;
    
    @Value("${camcheck.media.mobile.power-saving:false}")
    private boolean mobilePowerSaving;
    
    @Value("${camcheck.media.mobile.battery-threshold:20}")
    private int mobileBatteryThreshold;
    
    @Value("${camcheck.media.mobile.network-optimization:auto}")
    private String mobileNetworkOptimization;
    
    @Value("${camcheck.media.mobile.cellular.allow-streaming:true}")
    private boolean allowCellularStreaming;
    
    @Value("${camcheck.media.mobile.cellular.max-bitrate:500000}")
    private int cellularMaxBitrate;
    
    @Value("${camcheck.media.mobile.cellular.data-saving:true}")
    private boolean cellularDataSaving;
    
    // Store device type for each user
    private final Map<String, DeviceInfo> userDevices = new ConcurrentHashMap<>();
    
    // Regular expressions for mobile device detection
    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            ".*(android|avantgo|blackberry|bolt|boost|cricket|docomo"
            + "|fone|hiptop|mini|mobi|palm|phone|pie|tablet|up\\.browser"
            + "|up\\.link|webos|wos).*", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern TABLET_PATTERN = Pattern.compile(
            ".*(ipad|tablet|(android(?!.*mobile))|(windows(?!.*phone)(.*touch))"
            + "|kindle|playbook|silk|(puffin(?!.*(IP|AP|WP)))).*", 
            Pattern.CASE_INSENSITIVE);
    
    /**
     * Detect if the current request is from a mobile device
     * @return True if the request is from a mobile device
     */
    public boolean isMobileDevice() {
        if (!autoDetectMobile) {
            return false;
        }
        
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest();
            String userAgent = request.getHeader("User-Agent");
            return detectMobileDevice(userAgent);
        } catch (Exception e) {
            log.debug("Error detecting mobile device", e);
            return false;
        }
    }
    
    /**
     * Detect if a user agent string is from a mobile device
     * @param userAgent User agent string
     * @return True if the user agent is from a mobile device
     */
    public boolean detectMobileDevice(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }
        
        // Check if it's a mobile device
        return MOBILE_PATTERN.matcher(userAgent).matches() || TABLET_PATTERN.matcher(userAgent).matches();
    }
    
    /**
     * Register a device for a user
     * @param userId User identifier
     * @param userAgent User agent string
     * @param isMobile Whether the device is mobile (override auto-detection)
     * @param batteryLevel Battery level (0-100)
     * @param networkType Network type (wifi, cellular, etc.)
     * @return Device information
     */
    public DeviceInfo registerDevice(String userId, String userAgent, 
                                    Boolean isMobile, Integer batteryLevel, String networkType) {
        boolean isMobileDevice = isMobile != null ? isMobile : detectMobileDevice(userAgent);
        
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setMobile(isMobileDevice);
        deviceInfo.setUserAgent(userAgent);
        deviceInfo.setBatteryLevel(batteryLevel != null ? batteryLevel : 100);
        deviceInfo.setNetworkType(networkType != null ? networkType : "unknown");
        
        // Calculate optimization settings
        calculateOptimizationSettings(deviceInfo);
        
        // Store device info
        userDevices.put(userId, deviceInfo);
        
        log.debug("Registered device for user {}: mobile={}, network={}, battery={}%", 
                userId, deviceInfo.isMobile(), deviceInfo.getNetworkType(), deviceInfo.getBatteryLevel());
        
        return deviceInfo;
    }
    
    /**
     * Update device information for a user
     * @param userId User identifier
     * @param batteryLevel Battery level (0-100)
     * @param networkType Network type (wifi, cellular, etc.)
     * @return Updated device information
     */
    public DeviceInfo updateDeviceInfo(String userId, Integer batteryLevel, String networkType) {
        DeviceInfo deviceInfo = userDevices.get(userId);
        
        if (deviceInfo == null) {
            // Create a new device info if not exists
            deviceInfo = new DeviceInfo();
            deviceInfo.setMobile(false);
            deviceInfo.setUserAgent("unknown");
        }
        
        // Update device info
        if (batteryLevel != null) {
            deviceInfo.setBatteryLevel(batteryLevel);
        }
        
        if (networkType != null) {
            deviceInfo.setNetworkType(networkType);
        }
        
        // Recalculate optimization settings
        calculateOptimizationSettings(deviceInfo);
        
        // Update stored device info
        userDevices.put(userId, deviceInfo);
        
        log.debug("Updated device info for user {}: network={}, battery={}%", 
                userId, deviceInfo.getNetworkType(), deviceInfo.getBatteryLevel());
        
        return deviceInfo;
    }
    
    /**
     * Get device information for a user
     * @param userId User identifier
     * @return Device information or null if not registered
     */
    public DeviceInfo getDeviceInfo(String userId) {
        return userDevices.get(userId);
    }
    
    /**
     * Calculate optimization settings based on device information
     * @param deviceInfo Device information
     */
    private void calculateOptimizationSettings(DeviceInfo deviceInfo) {
        if (!mobileOptimizationEnabled || !deviceInfo.isMobile()) {
            // No optimization for non-mobile devices
            deviceInfo.setOptimizationLevel(OptimizationLevel.NONE);
            return;
        }
        
        // Check battery level for power saving
        boolean lowBattery = deviceInfo.getBatteryLevel() <= mobileBatteryThreshold;
        
        // Check network type
        boolean isCellular = "cellular".equalsIgnoreCase(deviceInfo.getNetworkType()) || 
                            "mobile".equalsIgnoreCase(deviceInfo.getNetworkType()) ||
                            "lte".equalsIgnoreCase(deviceInfo.getNetworkType()) ||
                            "3g".equalsIgnoreCase(deviceInfo.getNetworkType()) ||
                            "4g".equalsIgnoreCase(deviceInfo.getNetworkType()) ||
                            "5g".equalsIgnoreCase(deviceInfo.getNetworkType());
        
        // Determine optimization level
        OptimizationLevel level;
        
        if (lowBattery && mobilePowerSaving) {
            // Low battery - use aggressive optimization
            level = OptimizationLevel.AGGRESSIVE;
        } else if (isCellular && cellularDataSaving) {
            // Cellular connection - use moderate optimization
            level = OptimizationLevel.MODERATE;
        } else {
            // Default - use light optimization
            level = OptimizationLevel.LIGHT;
        }
        
        // Set optimization level
        deviceInfo.setOptimizationLevel(level);
        
        // Calculate optimal resolution
        int maxWidth, maxHeight;
        double quality;
        int frameRate;
        
        switch (level) {
            case AGGRESSIVE:
                maxWidth = Math.min(360, mobileMaxWidth);
                maxHeight = Math.min(240, mobileMaxHeight);
                quality = Math.min(0.6, mobileCompressionQuality);
                frameRate = Math.min(10, mobileMaxFrameRate);
                break;
            case MODERATE:
                maxWidth = Math.min(480, mobileMaxWidth);
                maxHeight = Math.min(320, mobileMaxHeight);
                quality = Math.min(0.7, mobileCompressionQuality);
                frameRate = Math.min(15, mobileMaxFrameRate);
                break;
            case LIGHT:
                maxWidth = mobileMaxWidth;
                maxHeight = mobileMaxHeight;
                quality = mobileCompressionQuality;
                frameRate = mobileMaxFrameRate;
                break;
            default:
                maxWidth = 0;
                maxHeight = 0;
                quality = 1.0;
                frameRate = 30;
        }
        
        // Set optimization parameters
        deviceInfo.setMaxWidth(maxWidth);
        deviceInfo.setMaxHeight(maxHeight);
        deviceInfo.setQuality(quality);
        deviceInfo.setFrameRate(frameRate);
        
        // Set bitrate limit for cellular
        if (isCellular) {
            deviceInfo.setMaxBitrate(cellularMaxBitrate);
            deviceInfo.setAllowStreaming(allowCellularStreaming);
        } else {
            deviceInfo.setMaxBitrate(0); // No limit
            deviceInfo.setAllowStreaming(true);
        }
    }
    
    /**
     * Get optimization parameters for a user
     * @param userId User identifier
     * @return Map of optimization parameters
     */
    public Map<String, Object> getOptimizationParameters(String userId) {
        DeviceInfo deviceInfo = userDevices.get(userId);
        
        if (deviceInfo == null || !mobileOptimizationEnabled) {
            // No optimization
            Map<String, Object> params = new HashMap<>();
            params.put("optimizationEnabled", false);
            return params;
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("optimizationEnabled", true);
        params.put("optimizationLevel", deviceInfo.getOptimizationLevel().name());
        params.put("maxWidth", deviceInfo.getMaxWidth());
        params.put("maxHeight", deviceInfo.getMaxHeight());
        params.put("quality", deviceInfo.getQuality());
        params.put("frameRate", deviceInfo.getFrameRate());
        params.put("maxBitrate", deviceInfo.getMaxBitrate());
        params.put("allowStreaming", deviceInfo.isAllowStreaming());
        params.put("isMobile", deviceInfo.isMobile());
        params.put("batteryLevel", deviceInfo.getBatteryLevel());
        params.put("networkType", deviceInfo.getNetworkType());
        
        return params;
    }
    
    /**
     * Reset device information for a user
     * @param userId User identifier
     */
    public void resetDeviceInfo(String userId) {
        userDevices.remove(userId);
        log.debug("Reset device info for user {}", userId);
    }
    
    /**
     * Enum for optimization levels
     */
    public enum OptimizationLevel {
        NONE,       // No optimization
        LIGHT,      // Light optimization
        MODERATE,   // Moderate optimization
        AGGRESSIVE  // Aggressive optimization
    }
    
    /**
     * Class representing device information
     */
    public static class DeviceInfo {
        private boolean mobile;
        private String userAgent;
        private int batteryLevel = 100;
        private String networkType = "unknown";
        private OptimizationLevel optimizationLevel = OptimizationLevel.NONE;
        private int maxWidth;
        private int maxHeight;
        private double quality = 1.0;
        private int frameRate = 30;
        private int maxBitrate = 0;
        private boolean allowStreaming = true;
        
        public boolean isMobile() {
            return mobile;
        }
        
        public void setMobile(boolean mobile) {
            this.mobile = mobile;
        }
        
        public String getUserAgent() {
            return userAgent;
        }
        
        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
        
        public int getBatteryLevel() {
            return batteryLevel;
        }
        
        public void setBatteryLevel(int batteryLevel) {
            this.batteryLevel = batteryLevel;
        }
        
        public String getNetworkType() {
            return networkType;
        }
        
        public void setNetworkType(String networkType) {
            this.networkType = networkType;
        }
        
        public OptimizationLevel getOptimizationLevel() {
            return optimizationLevel;
        }
        
        public void setOptimizationLevel(OptimizationLevel optimizationLevel) {
            this.optimizationLevel = optimizationLevel;
        }
        
        public int getMaxWidth() {
            return maxWidth;
        }
        
        public void setMaxWidth(int maxWidth) {
            this.maxWidth = maxWidth;
        }
        
        public int getMaxHeight() {
            return maxHeight;
        }
        
        public void setMaxHeight(int maxHeight) {
            this.maxHeight = maxHeight;
        }
        
        public double getQuality() {
            return quality;
        }
        
        public void setQuality(double quality) {
            this.quality = quality;
        }
        
        public int getFrameRate() {
            return frameRate;
        }
        
        public void setFrameRate(int frameRate) {
            this.frameRate = frameRate;
        }
        
        public int getMaxBitrate() {
            return maxBitrate;
        }
        
        public void setMaxBitrate(int maxBitrate) {
            this.maxBitrate = maxBitrate;
        }
        
        public boolean isAllowStreaming() {
            return allowStreaming;
        }
        
        public void setAllowStreaming(boolean allowStreaming) {
            this.allowStreaming = allowStreaming;
        }
    }
} 