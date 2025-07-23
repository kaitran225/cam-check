/// App configuration constants and settings
class AppConfig {
  // API Configuration
  static const String apiBaseUrl = 'https://api.camcheck.com'; // Replace with actual API URL
  static const String apiVersion = 'v2';
  static const String apiEndpoint = '$apiBaseUrl/api/$apiVersion';
  
  // Feature Flags
  static const bool enablePushNotifications = true;
  static const bool enableBackgroundMode = true;
  static const bool enableOfflineMode = true;
  
  // Default Settings
  static const String defaultVideoQuality = 'medium';
  static const String defaultAudioQuality = 'medium';
  static const bool defaultAudioEnabled = true;
  static const bool defaultVideoEnabled = true;
  
  // Camera Settings
  static const int maxCameraResolutionWidth = 1280;
  static const int maxCameraResolutionHeight = 720;
  static const int defaultFrameRate = 30;
  
  // WebRTC Configuration
  static const Map<String, dynamic> defaultIceServers = {
    'iceServers': [
      {'urls': 'stun:stun.l.google.com:19302'},
    ]
  };
  
  // Session Settings
  static const int defaultSessionExpirationMinutes = 10;
  static const int maxActiveSessions = 1;
  
  // Storage Keys
  static const String tokenKey = 'access_token';
  static const String refreshTokenKey = 'refresh_token';
  static const String userIdKey = 'user_id';
  static const String deviceIdKey = 'device_id';
  static const String userPrefsKey = 'user_preferences';
  
  // UI Settings
  static const double appBarHeight = 56.0;
  static const double bottomNavBarHeight = 56.0;
  static const double defaultPadding = 16.0;
  static const double defaultBorderRadius = 4.0;
  static const double defaultIconSize = 24.0;
  
  // Animation Durations
  static const Duration shortAnimationDuration = Duration(milliseconds: 200);
  static const Duration mediumAnimationDuration = Duration(milliseconds: 300);
  static const Duration longAnimationDuration = Duration(milliseconds: 500);
  
  // Network Timeouts
  static const Duration connectionTimeout = Duration(seconds: 30);
  static const Duration receiveTimeout = Duration(seconds: 30);
  
  // Retry Configuration
  static const int maxRetryAttempts = 3;
  static const Duration retryDelay = Duration(seconds: 2);
  
  // Cache Configuration
  static const Duration defaultCacheDuration = Duration(hours: 24);
  static const int maxCacheSizeInMB = 50;
  
  // Video Quality Options
  static const Map<String, Map<String, dynamic>> videoQualityOptions = {
    'low': {
      'resolution': '320x240',
      'frameRate': 15,
      'bitrate': 250000,
      'codec': 'h264'
    },
    'medium': {
      'resolution': '640x480',
      'frameRate': 25,
      'bitrate': 800000,
      'codec': 'h264'
    },
    'high': {
      'resolution': '1280x720',
      'frameRate': 30,
      'bitrate': 1500000,
      'codec': 'h264'
    }
  };
  
  // Audio Quality Options
  static const Map<String, Map<String, dynamic>> audioQualityOptions = {
    'low': {
      'sampleRate': 8000,
      'channels': 1,
      'bitrate': 16000,
      'codec': 'opus'
    },
    'medium': {
      'sampleRate': 22050,
      'channels': 1,
      'bitrate': 32000,
      'codec': 'opus'
    },
    'high': {
      'sampleRate': 44100,
      'channels': 2,
      'bitrate': 64000,
      'codec': 'opus'
    }
  };
}
