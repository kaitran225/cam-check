/// Session model representing a camera viewing session
class Session {
  final String sessionCode;
  final String? sessionId;
  final String? connectedTo;
  final DateTime? expiresAt;
  final int? expiresIn;
  final bool audioEnabled;
  final bool videoEnabled;
  final String quality;
  final String? recordingMode;
  final DateTime? joinedAt;
  final String? role;
  
  /// Constructor
  Session({
    required this.sessionCode,
    this.sessionId,
    this.connectedTo,
    this.expiresAt,
    this.expiresIn,
    this.audioEnabled = true,
    this.videoEnabled = true,
    this.quality = 'medium',
    this.recordingMode,
    this.joinedAt,
    this.role,
  });
  
  /// Create Session from JSON for session creation response
  factory Session.fromCreateJson(Map<String, dynamic> json) {
    return Session(
      sessionCode: json['sessionCode'] as String,
      expiresAt: json['expiresAt'] != null 
          ? DateTime.parse(json['expiresAt'] as String) 
          : null,
      expiresIn: json['expiresIn'] as int?,
      role: 'creator',
    );
  }
  
  /// Create Session from JSON for session join response
  factory Session.fromJoinJson(Map<String, dynamic> json) {
    return Session(
      sessionCode: json['sessionId'] as String,
      sessionId: json['sessionId'] as String,
      connectedTo: json['connectedTo'] as String?,
      audioEnabled: json['audioEnabled'] as bool? ?? true,
      videoEnabled: json['videoEnabled'] as bool? ?? true,
      quality: json['quality'] as String? ?? 'medium',
      joinedAt: json['joinedAt'] != null 
          ? DateTime.parse(json['joinedAt'] as String) 
          : DateTime.now(),
      role: 'joiner',
    );
  }
  
  /// Create Session from JSON for get session response
  factory Session.fromGetJson(Map<String, dynamic> json) {
    return Session(
      sessionCode: json['sessionCode'] as String,
      sessionId: json['sessionId'] as String?,
      connectedTo: json['connectedTo'] as String?,
      audioEnabled: json['audioEnabled'] as bool? ?? true,
      videoEnabled: json['videoEnabled'] as bool? ?? true,
      quality: json['quality'] as String? ?? 'medium',
      joinedAt: json['joinedAt'] != null 
          ? DateTime.parse(json['joinedAt'] as String) 
          : null,
      role: json['role'] as String?,
    );
  }
  
  /// Convert Session to JSON
  Map<String, dynamic> toJson() {
    return {
      'sessionCode': sessionCode,
      'sessionId': sessionId,
      'connectedTo': connectedTo,
      'expiresAt': expiresAt?.toIso8601String(),
      'expiresIn': expiresIn,
      'audioEnabled': audioEnabled,
      'videoEnabled': videoEnabled,
      'quality': quality,
      'recordingMode': recordingMode,
      'joinedAt': joinedAt?.toIso8601String(),
      'role': role,
    };
  }
  
  /// Check if session is active
  bool get isActive {
    if (expiresAt != null) {
      return DateTime.now().isBefore(expiresAt!);
    }
    return connectedTo != null;
  }
  
  /// Create a copy of this session with updated fields
  Session copyWith({
    String? sessionCode,
    String? sessionId,
    String? connectedTo,
    DateTime? expiresAt,
    int? expiresIn,
    bool? audioEnabled,
    bool? videoEnabled,
    String? quality,
    String? recordingMode,
    DateTime? joinedAt,
    String? role,
  }) {
    return Session(
      sessionCode: sessionCode ?? this.sessionCode,
      sessionId: sessionId ?? this.sessionId,
      connectedTo: connectedTo ?? this.connectedTo,
      expiresAt: expiresAt ?? this.expiresAt,
      expiresIn: expiresIn ?? this.expiresIn,
      audioEnabled: audioEnabled ?? this.audioEnabled,
      videoEnabled: videoEnabled ?? this.videoEnabled,
      quality: quality ?? this.quality,
      recordingMode: recordingMode ?? this.recordingMode,
      joinedAt: joinedAt ?? this.joinedAt,
      role: role ?? this.role,
    );
  }
} 