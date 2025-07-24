import 'package:flutter/foundation.dart';

/// App configuration for different environments
class AppConfig {
  // API Base URL
  static const String apiBaseUrlProd = 'https://api.camcheck.com';
  static const String apiBaseUrlDev = 'http://10.0.2.2:8080'; // Android emulator localhost
  
  // API Version
  static const String apiVersion = 'v1';
  
  // Current environment
  static const bool isProduction = false; // Force development mode
  
  // Get the appropriate API URL based on environment
  static String get apiBaseUrl {
    return isProduction ? apiBaseUrlProd : apiBaseUrlDev;
  }
  
  // Full API URL with version
  static String get apiUrl {
    return '$apiBaseUrl/api/$apiVersion';
  }
  
  // App version
  static const String appVersion = '1.0.0';
  
  // Default settings
  static const Map<String, dynamic> defaultSettings = {
    'videoQuality': 'medium',
    'audioQuality': 'medium',
    'pushNotificationsEnabled': true,
    'backgroundModeEnabled': false,
    'dataUsageLimit': true,
    'automaticRecording': false,
    'darkMode': true,
  };
  
  // WebRTC Configuration
  static const Map<String, dynamic> defaultWebRTCConfig = {
    'iceServers': [
      {'urls': 'stun:stun.l.google.com:19302'},
      {'urls': 'stun:stun1.l.google.com:19302'},
    ],
    'iceTransportPolicy': 'all',
    'bundlePolicy': 'balanced',
  };
  
  // Video Quality Presets
  static const Map<String, Map<String, dynamic>> videoQualityPresets = {
    'low': {
      'resolution': '320x240',
      'frameRate': 15,
      'bitrate': 250000,
      'codec': 'h264',
    },
    'medium': {
      'resolution': '640x480',
      'frameRate': 25,
      'bitrate': 800000,
      'codec': 'h264',
    },
    'high': {
      'resolution': '1280x720',
      'frameRate': 30,
      'bitrate': 1500000,
      'codec': 'h264',
    },
  };
  
  // Audio Quality Presets
  static const Map<String, Map<String, dynamic>> audioQualityPresets = {
    'low': {
      'sampleRate': 8000,
      'channels': 1,
      'bitrate': 16000,
      'codec': 'opus',
    },
    'medium': {
      'sampleRate': 22050,
      'channels': 1,
      'bitrate': 32000,
      'codec': 'opus',
    },
    'high': {
      'sampleRate': 44100,
      'channels': 2,
      'bitrate': 64000,
      'codec': 'opus',
    },
  };
} 