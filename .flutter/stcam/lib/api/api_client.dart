import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:battery_plus/battery_plus.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import '../config/app_config.dart';

/// CamCheck API Client for Flutter mobile app
class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  factory ApiClient() => _instance;
  ApiClient._internal();
  
  final http.Client _client = http.Client();
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();
  final DeviceInfoPlugin _deviceInfo = DeviceInfoPlugin();
  final Battery _battery = Battery();
  final FirebaseMessaging _fcm = FirebaseMessaging.instance;
  
  String? _accessToken;
  String? _refreshToken;
  String? _userId;
  String? _deviceId;
  Map<String, dynamic>? _userConfig;
  
  /// Initialize the API client
  Future<void> initialize() async {
    // Load stored tokens
    await _loadTokens();
    
    // Generate device ID if not exists
    await _ensureDeviceId();
    
    // Request FCM permissions and token
    if (AppConfig.enablePushNotifications) {
      await _setupPushNotifications();
    }
  }
  
  /// Load tokens from secure storage
  Future<void> _loadTokens() async {
    _accessToken = await _secureStorage.read(key: AppConfig.tokenKey);
    _refreshToken = await _secureStorage.read(key: AppConfig.refreshTokenKey);
    _userId = await _secureStorage.read(key: AppConfig.userIdKey);
  }
  
  /// Save tokens to secure storage
  Future<void> _saveTokens(String accessToken, String refreshToken, String userId) async {
    await _secureStorage.write(key: AppConfig.tokenKey, value: accessToken);
    await _secureStorage.write(key: AppConfig.refreshTokenKey, value: refreshToken);
    await _secureStorage.write(key: AppConfig.userIdKey, value: userId);
    
    _accessToken = accessToken;
    _refreshToken = refreshToken;
    _userId = userId;
  }
  
  /// Clear stored tokens on logout
  Future<void> _clearTokens() async {
    await _secureStorage.delete(key: AppConfig.tokenKey);
    await _secureStorage.delete(key: AppConfig.refreshTokenKey);
    await _secureStorage.delete(key: AppConfig.userIdKey);
    
    _accessToken = null;
    _refreshToken = null;
    _userId = null;
    _userConfig = null;
  }
  
  /// Generate or retrieve device ID
  Future<void> _ensureDeviceId() async {
    _deviceId = await _secureStorage.read(key: AppConfig.deviceIdKey);
    
    if (_deviceId == null) {
      // Generate a unique device ID
      String uuid = DateTime.now().millisecondsSinceEpoch.toString() + 
                   '-' + 
                   (DateTime.now().microsecondsSinceEpoch % 10000).toString();
      
      await _secureStorage.write(key: AppConfig.deviceIdKey, value: uuid);
      _deviceId = uuid;
    }
  }
  
  /// Setup push notifications
  Future<void> _setupPushNotifications() async {
    // Request permission on iOS
    if (Platform.isIOS) {
      NotificationSettings settings = await _fcm.requestPermission();
      if (settings.authorizationStatus != AuthorizationStatus.authorized) {
        debugPrint('User declined push notifications');
      }
    }
    
    // Get FCM token
    String? fcmToken = await _fcm.getToken();
    if (fcmToken != null && _userId != null) {
      await updateFcmToken(fcmToken);
    }
    
    // Handle token refresh
    _fcm.onTokenRefresh.listen((token) {
      if (_userId != null) {
        updateFcmToken(token);
      }
    });
    
    // Handle incoming messages
    FirebaseMessaging.onMessage.listen(_handlePushMessage);
    FirebaseMessaging.onMessageOpenedApp.listen(_handlePushMessage);
  }
  
  /// Handle push notification message
  void _handlePushMessage(RemoteMessage message) {
    debugPrint('Received push notification: ${message.notification?.title}');
    
    // Handle different notification types
    if (message.data.containsKey('type')) {
      switch (message.data['type']) {
        case 'session_invitation':
          // Handle session invitation
          break;
        case 'missed_call':
          // Handle missed call
          break;
        case 'motion_detected':
          // Handle motion detection
          break;
        case 'system_alert':
          // Handle system alert
          break;
      }
    }
  }
  
  /// Check if user is logged in
  bool get isLoggedIn => _accessToken != null;
  
  /// Get user ID
  String? get userId => _userId;
  
  /// Get device ID
  String? get deviceId => _deviceId;
  
  /// Get network type
  Future<String> _getNetworkType() async {
    final connectivityResult = await (Connectivity().checkConnectivity());
    if (connectivityResult == ConnectivityResult.mobile) {
      return 'cellular';
    } else if (connectivityResult == ConnectivityResult.wifi) {
      return 'wifi';
    } else if (connectivityResult == ConnectivityResult.ethernet) {
      return 'ethernet';
    }
    return 'unknown';
  }
  
  /// Get battery level
  Future<int> _getBatteryLevel() async {
    final level = await _battery.batteryLevel;
    return level;
  }
  
  /// Get device type
  Future<String> _getDeviceType() async {
    if (Platform.isAndroid) {
      return 'android';
    } else if (Platform.isIOS) {
      return 'ios';
    }
    return 'other';
  }
  
  /// Get device details
  Future<Map<String, String>> _getDeviceDetails() async {
    Map<String, String> details = {};
    
    if (Platform.isAndroid) {
      final androidInfo = await _deviceInfo.androidInfo;
      details['deviceName'] = androidInfo.model;
      details['osVersion'] = androidInfo.version.release;
    } else if (Platform.isIOS) {
      final iosInfo = await _deviceInfo.iosInfo;
      details['deviceName'] = iosInfo.name;
      details['osVersion'] = iosInfo.systemVersion;
    } else {
      details['deviceName'] = 'Unknown';
      details['osVersion'] = 'Unknown';
    }
    
    details['deviceType'] = Platform.isAndroid ? 'ANDROID' : (Platform.isIOS ? 'IOS' : 'WEB');
    
    // App version - in a real app, this would be dynamic
    details['appVersion'] = '1.0.0';
    
    return details;
  }
  
  /// Create authorization header
  Map<String, String> _createAuthHeaders() {
    if (_accessToken == null) {
      return {'Content-Type': 'application/json'};
    }
    
    return {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer $_accessToken',
    };
  }
  
  /// Handle API response
  dynamic _handleResponse(http.Response response) {
    final statusCode = response.statusCode;
    final responseBody = json.decode(response.body);
    
    if (statusCode >= 200 && statusCode < 300) {
      return responseBody;
    } else if (statusCode == 401) {
      throw UnauthorizedException(responseBody['message'] ?? 'Unauthorized');
    } else {
      throw ApiException(
        statusCode, 
        responseBody['message'] ?? 'Unknown error',
        responseBody['data']
      );
    }
  }
  
  /// Refresh token if expired
  Future<bool> _refreshTokenIfNeeded() async {
    if (_refreshToken == null) return false;
    
    try {
      final response = await _client.post(
        Uri.parse('${AppConfig.apiEndpoint}/auth/refresh'),
        headers: {'Content-Type': 'application/json'},
        body: json.encode({
          'refreshToken': _refreshToken,
          'deviceId': _deviceId,
        }),
      );
      
      final responseBody = json.decode(response.body);
      
      if (response.statusCode == 200 && responseBody['status'] == 'success') {
        final data = responseBody['data'];
        await _saveTokens(data['accessToken'], data['refreshToken'], _userId!);
        return true;
      }
      
      // If refresh failed, clear tokens
      await _clearTokens();
      return false;
    } catch (e) {
      debugPrint('Token refresh error: $e');
      await _clearTokens();
      return false;
    }
  }
  
  /// Make authenticated API request with token refresh
  Future<dynamic> _authenticatedRequest(
    Future<http.Response> Function() requestFunc
  ) async {
    try {
      final response = await requestFunc();
      return _handleResponse(response);
    } on UnauthorizedException {
      // Try to refresh token and retry
      if (await _refreshTokenIfNeeded()) {
        final response = await requestFunc();
        return _handleResponse(response);
      }
      rethrow;
    }
  }
  
  /// Login with username and password
  Future<bool> login(String username, String password) async {
    try {
      final deviceDetails = await _getDeviceDetails();
      String? fcmToken = await _fcm.getToken();
      
      final response = await _client.post(
        Uri.parse('${AppConfig.apiEndpoint}/auth/login'),
        headers: {'Content-Type': 'application/json'},
        body: json.encode({
          'username': username,
          'password': password,
          'deviceId': _deviceId,
          'deviceName': deviceDetails['deviceName'],
          'deviceType': deviceDetails['deviceType'],
          'fcmToken': fcmToken,
        }),
      );
      
      final responseBody = json.decode(response.body);
      
      if (response.statusCode == 200 && responseBody['status'] == 'success') {
        final authData = responseBody['data']['auth'];
        await _saveTokens(
          authData['accessToken'], 
          authData['refreshToken'], 
          authData['username']
        );
        
        // Save user configuration
        _userConfig = authData['appConfig'];
        
        // Register device
        await _registerDevice();
        
        return true;
      }
      
      return false;
    } catch (e) {
      debugPrint('Login error: $e');
      return false;
    }
  }
  
  /// Logout
  Future<bool> logout() async {
    if (_accessToken == null) return true;
    
    try {
      await _client.post(
        Uri.parse('${AppConfig.apiEndpoint}/auth/logout'),
        headers: _createAuthHeaders(),
      );
      
      // Unregister device
      if (_deviceId != null) {
        try {
          await _client.delete(
            Uri.parse('${AppConfig.apiEndpoint}/devices/$_deviceId'),
            headers: _createAuthHeaders(),
          );
        } catch (e) {
          debugPrint('Error unregistering device: $e');
        }
      }
      
      // Clear tokens
      await _clearTokens();
      return true;
    } catch (e) {
      debugPrint('Logout error: $e');
      await _clearTokens();
      return true; // Return true anyway, as we've cleared local tokens
    }
  }
  
  /// Register device
  Future<bool> _registerDevice() async {
    if (_accessToken == null) return false;
    
    try {
      final deviceDetails = await _getDeviceDetails();
      final fcmToken = await _fcm.getToken();
      
      await _authenticatedRequest(() => _client.post(
        Uri.parse('${AppConfig.apiEndpoint}/devices/register'),
        headers: _createAuthHeaders(),
        body: json.encode({
          'deviceId': _deviceId,
          'deviceName': deviceDetails['deviceName'],
          'deviceType': deviceDetails['deviceType'],
          'osVersion': deviceDetails['osVersion'],
          'appVersion': deviceDetails['appVersion'],
          'fcmToken': fcmToken,
          'pushNotificationsEnabled': AppConfig.enablePushNotifications,
          'timezone': DateTime.now().timeZoneName,
          'deviceSettings': {},
        }),
      ));
      
      return true;
    } catch (e) {
      debugPrint('Device registration error: $e');
      return false;
    }
  }
  
  /// Update FCM token
  Future<bool> updateFcmToken(String fcmToken) async {
    if (_accessToken == null || _deviceId == null) return false;
    
    try {
      await _authenticatedRequest(() => _client.put(
        Uri.parse('${AppConfig.apiEndpoint}/devices/$_deviceId/fcm-token'),
        headers: _createAuthHeaders(),
        body: json.encode({
          'fcmToken': fcmToken,
        }),
      ));
      
      return true;
    } catch (e) {
      debugPrint('FCM token update error: $e');
      return false;
    }
  }
  
  /// Get mobile configuration
  Future<Map<String, dynamic>> getMobileConfig() async {
    try {
      final responseBody = await _authenticatedRequest(() => _client.get(
        Uri.parse('${AppConfig.apiEndpoint}/config'),
        headers: _createAuthHeaders(),
      ));
      
      if (responseBody['status'] == 'success') {
        _userConfig = responseBody['data'];
        return _userConfig!;
      }
      
      throw ApiException(500, 'Failed to get mobile config', null);
    } catch (e) {
      debugPrint('Get mobile config error: $e');
      rethrow;
    }
  }
  
  /// Update user preferences
  Future<Map<String, dynamic>> updatePreferences(Map<String, dynamic> preferences) async {
    try {
      final responseBody = await _authenticatedRequest(() => _client.post(
        Uri.parse('${AppConfig.apiEndpoint}/config/preferences'),
        headers: _createAuthHeaders(),
        body: json.encode(preferences),
      ));
      
      if (responseBody['status'] == 'success') {
        _userConfig = responseBody['data'];
        return _userConfig!;
      }
      
      throw ApiException(500, 'Failed to update preferences', null);
    } catch (e) {
      debugPrint('Update preferences error: $e');
      rethrow;
    }
  }
  
  /// Get quality options for video and audio
  Future<Map<String, dynamic>> getQualityOptions() async {
    try {
      final responseBody = await _authenticatedRequest(() => _client.get(
        Uri.parse('${AppConfig.apiEndpoint}/config/quality-options'),
        headers: _createAuthHeaders(),
      ));
      
      if (responseBody['status'] == 'success') {
        return responseBody['data'];
      }
      
      throw ApiException(500, 'Failed to get quality options', null);
    } catch (e) {
      debugPrint('Get quality options error: $e');
      rethrow;
    }
  }
  
  /// Create a new session
  Future<Map<String, dynamic>> createSession({
    int expirationMinutes = 10,
    bool audioEnabled = true,
    bool videoEnabled = true,
    String quality = 'medium',
    String recordingMode = 'none',
  }) async {
    try {
      final responseBody = await _authenticatedRequest(() => _client.post(
        Uri.parse('${AppConfig.apiEndpoint}/sessions'),
        headers: _createAuthHeaders(),
        body: json.encode({
          'expirationMinutes': expirationMinutes,
          'deviceId': _deviceId,
          'audioEnabled': audioEnabled,
          'videoEnabled': videoEnabled,
          'quality': quality,
          'recordingMode': recordingMode,
        }),
      ));
      
      if (responseBody['status'] == 'success') {
        return responseBody['data'];
      }
      
      throw ApiException(500, 'Failed to create session', null);
    } catch (e) {
      debugPrint('Create session error: $e');
      rethrow;
    }
  }
  
  /// Join a session
  Future<Map<String, dynamic>> joinSession(
    String sessionCode, {
    bool audioEnabled = true,
    bool videoEnabled = true,
    String quality = 'medium',
  }) async {
    try {
      final responseBody = await _authenticatedRequest(() => _client.post(
        Uri.parse('${AppConfig.apiEndpoint}/sessions/$sessionCode/join'),
        headers: _createAuthHeaders(),
        body: json.encode({
          'sessionCode': sessionCode,
          'deviceId': _deviceId,
          'audioEnabled': audioEnabled,
          'videoEnabled': videoEnabled,
          'quality': quality,
        }),
      ));
      
      if (responseBody['status'] == 'success') {
        return responseBody['data'];
      }
      
      throw ApiException(500, 'Failed to join session', null);
    } catch (e) {
      debugPrint('Join session error: $e');
      rethrow;
    }
  }
  
  /// End a session
  Future<bool> endSession(String sessionCode) async {
    try {
      final responseBody = await _authenticatedRequest(() => _client.delete(
        Uri.parse('${AppConfig.apiEndpoint}/sessions/$sessionCode'),
        headers: _createAuthHeaders(),
      ));
      
      return responseBody['status'] == 'success';
    } catch (e) {
      debugPrint('End session error: $e');
      return false;
    }
  }
  
  /// Get WebRTC configuration
  Future<Map<String, dynamic>> getWebRTCConfig() async {
    try {
      final networkType = await _getNetworkType();
      final batteryLevel = await _getBatteryLevel();
      final deviceType = await _getDeviceType();
      
      final responseBody = await _authenticatedRequest(() => _client.get(
        Uri.parse('${AppConfig.apiEndpoint}/webrtc/config?networkType=$networkType&batteryLevel=$batteryLevel&deviceType=$deviceType'),
        headers: _createAuthHeaders(),
      ));
      
      if (responseBody['status'] == 'success') {
        return responseBody['data'];
      }
      
      throw ApiException(500, 'Failed to get WebRTC config', null);
    } catch (e) {
      debugPrint('Get WebRTC config error: $e');
      rethrow;
    }
  }
  
  /// Initialize WebRTC connection
  Future<Map<String, dynamic>> initializeWebRTCConnection(
    String receiverId, {
    String quality = 'medium',
    bool audioEnabled = true,
    bool videoEnabled = true,
  }) async {
    try {
      final networkType = await _getNetworkType();
      final batteryLevel = await _getBatteryLevel();
      
      final responseBody = await _authenticatedRequest(() => _client.post(
        Uri.parse('${AppConfig.apiEndpoint}/webrtc/connect/$receiverId'),
        headers: _createAuthHeaders(),
        body: json.encode({
          'quality': quality,
          'audioEnabled': audioEnabled,
          'videoEnabled': videoEnabled,
          'networkType': networkType,
          'batteryLevel': batteryLevel,
          'deviceId': _deviceId,
        }),
      ));
      
      if (responseBody['status'] == 'success') {
        return responseBody['data'];
      }
      
      throw ApiException(500, 'Failed to initialize WebRTC connection', null);
    } catch (e) {
      debugPrint('Initialize WebRTC connection error: $e');
      rethrow;
    }
  }
  
  /// Dispose of resources
  void dispose() {
    _client.close();
  }
}

/// API exception
class ApiException implements Exception {
  final int statusCode;
  final String message;
  final dynamic data;
  
  ApiException(this.statusCode, this.message, this.data);
  
  @override
  String toString() => 'ApiException: $statusCode - $message';
}

/// Unauthorized exception
class UnauthorizedException extends ApiException {
  UnauthorizedException(String message) : super(401, message, null);
}
