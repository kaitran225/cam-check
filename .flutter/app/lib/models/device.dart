/// Device model representing a registered device
class Device {
  final String deviceId;
  final String deviceName;
  final String deviceType;
  final String osVersion;
  final String appVersion;
  final DateTime? lastSeen;
  final bool pushNotificationsEnabled;
  final Map<String, dynamic>? deviceSettings;
  
  /// Constructor
  Device({
    required this.deviceId,
    required this.deviceName,
    required this.deviceType,
    required this.osVersion,
    required this.appVersion,
    this.lastSeen,
    this.pushNotificationsEnabled = true,
    this.deviceSettings,
  });
  
  /// Create Device from JSON
  factory Device.fromJson(Map<String, dynamic> json) {
    return Device(
      deviceId: json['deviceId'] as String,
      deviceName: json['deviceName'] as String,
      deviceType: json['deviceType'] as String,
      osVersion: json['osVersion'] as String,
      appVersion: json['appVersion'] as String,
      lastSeen: json['lastSeen'] != null 
          ? DateTime.parse(json['lastSeen'] as String) 
          : null,
      pushNotificationsEnabled: json['pushNotificationsEnabled'] as bool? ?? true,
      deviceSettings: json['deviceSettings'] as Map<String, dynamic>?,
    );
  }
  
  /// Convert Device to JSON
  Map<String, dynamic> toJson() {
    return {
      'deviceId': deviceId,
      'deviceName': deviceName,
      'deviceType': deviceType,
      'osVersion': osVersion,
      'appVersion': appVersion,
      'lastSeen': lastSeen?.toIso8601String(),
      'pushNotificationsEnabled': pushNotificationsEnabled,
      'deviceSettings': deviceSettings,
    };
  }
  
  /// Create a copy of this device with updated fields
  Device copyWith({
    String? deviceName,
    String? osVersion,
    String? appVersion,
    DateTime? lastSeen,
    bool? pushNotificationsEnabled,
    Map<String, dynamic>? deviceSettings,
  }) {
    return Device(
      deviceId: this.deviceId,
      deviceName: deviceName ?? this.deviceName,
      deviceType: this.deviceType,
      osVersion: osVersion ?? this.osVersion,
      appVersion: appVersion ?? this.appVersion,
      lastSeen: lastSeen ?? this.lastSeen,
      pushNotificationsEnabled: pushNotificationsEnabled ?? this.pushNotificationsEnabled,
      deviceSettings: deviceSettings ?? this.deviceSettings,
    );
  }
} 