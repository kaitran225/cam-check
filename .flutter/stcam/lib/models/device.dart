/// Model class for device information
class Device {
  final String deviceId;
  final String deviceName;
  final String deviceType; // 'ANDROID', 'IOS', 'WEB'
  final String osVersion;
  final String appVersion;
  final DateTime? lastSeen;
  final bool pushNotificationsEnabled;
  final String? fcmToken;
  final String? timezone;
  final Map<String, dynamic>? deviceSettings;
  
  Device({
    required this.deviceId,
    required this.deviceName,
    required this.deviceType,
    required this.osVersion,
    required this.appVersion,
    this.lastSeen,
    this.pushNotificationsEnabled = true,
    this.fcmToken,
    this.timezone,
    this.deviceSettings,
  });
  
  /// Create a Device from JSON data
  factory Device.fromJson(Map<String, dynamic> json) {
    return Device(
      deviceId: json['deviceId'],
      deviceName: json['deviceName'],
      deviceType: json['deviceType'],
      osVersion: json['osVersion'],
      appVersion: json['appVersion'],
      lastSeen: json['lastSeen'] != null 
          ? DateTime.parse(json['lastSeen']) 
          : null,
      pushNotificationsEnabled: json['pushNotificationsEnabled'] ?? true,
      fcmToken: json['fcmToken'],
      timezone: json['timezone'],
      deviceSettings: json['deviceSettings'],
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
      'fcmToken': fcmToken,
      'timezone': timezone,
      'deviceSettings': deviceSettings,
    };
  }
  
  /// Create a copy of this Device with some updated fields
  Device copyWith({
    String? deviceId,
    String? deviceName,
    String? deviceType,
    String? osVersion,
    String? appVersion,
    DateTime? lastSeen,
    bool? pushNotificationsEnabled,
    String? fcmToken,
    String? timezone,
    Map<String, dynamic>? deviceSettings,
  }) {
    return Device(
      deviceId: deviceId ?? this.deviceId,
      deviceName: deviceName ?? this.deviceName,
      deviceType: deviceType ?? this.deviceType,
      osVersion: osVersion ?? this.osVersion,
      appVersion: appVersion ?? this.appVersion,
      lastSeen: lastSeen ?? this.lastSeen,
      pushNotificationsEnabled: pushNotificationsEnabled ?? this.pushNotificationsEnabled,
      fcmToken: fcmToken ?? this.fcmToken,
      timezone: timezone ?? this.timezone,
      deviceSettings: deviceSettings ?? this.deviceSettings,
    );
  }
  
  /// Check if this is an Android device
  bool get isAndroid => deviceType == 'ANDROID';
  
  /// Check if this is an iOS device
  bool get isIOS => deviceType == 'IOS';
  
  /// Check if this is a web client
  bool get isWeb => deviceType == 'WEB';
  
  /// Get the formatted last seen time
  String get formattedLastSeen {
    if (lastSeen == null) return 'Never';
    
    final now = DateTime.now();
    final difference = now.difference(lastSeen!);
    
    if (difference.inMinutes < 1) {
      return 'Just now';
    } else if (difference.inHours < 1) {
      return '${difference.inMinutes} minutes ago';
    } else if (difference.inDays < 1) {
      return '${difference.inHours} hours ago';
    } else if (difference.inDays < 30) {
      return '${difference.inDays} days ago';
    } else {
      return '${lastSeen!.day}/${lastSeen!.month}/${lastSeen!.year}';
    }
  }
} 