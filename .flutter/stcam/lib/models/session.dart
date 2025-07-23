/// Model class for a camera viewing session
class Session {
  final String sessionCode;
  final String? connectedTo;
  final bool audioEnabled;
  final bool videoEnabled;
  final String quality;
  final DateTime? expiresAt;
  final DateTime? joinedAt;
  final String? recordingMode;
  final String role; // 'creator' or 'joiner'
  
  Session({
    required this.sessionCode,
    this.connectedTo,
    required this.audioEnabled,
    required this.videoEnabled,
    required this.quality,
    this.expiresAt,
    this.joinedAt,
    this.recordingMode,
    required this.role,
  });
  
  /// Create a Session from JSON data
  factory Session.fromJson(Map<String, dynamic> json) {
    return Session(
      sessionCode: json['sessionCode'],
      connectedTo: json['connectedTo'],
      audioEnabled: json['audioEnabled'] ?? true,
      videoEnabled: json['videoEnabled'] ?? true,
      quality: json['quality'] ?? 'medium',
      expiresAt: json['expiresAt'] != null 
          ? DateTime.parse(json['expiresAt']) 
          : null,
      joinedAt: json['joinedAt'] != null 
          ? DateTime.parse(json['joinedAt']) 
          : null,
      recordingMode: json['recordingMode'],
      role: json['role'] ?? 'joiner',
    );
  }
  
  /// Convert Session to JSON
  Map<String, dynamic> toJson() {
    return {
      'sessionCode': sessionCode,
      'connectedTo': connectedTo,
      'audioEnabled': audioEnabled,
      'videoEnabled': videoEnabled,
      'quality': quality,
      'expiresAt': expiresAt?.toIso8601String(),
      'joinedAt': joinedAt?.toIso8601String(),
      'recordingMode': recordingMode,
      'role': role,
    };
  }
  
  /// Create a copy of this Session with some updated fields
  Session copyWith({
    String? sessionCode,
    String? connectedTo,
    bool? audioEnabled,
    bool? videoEnabled,
    String? quality,
    DateTime? expiresAt,
    DateTime? joinedAt,
    String? recordingMode,
    String? role,
  }) {
    return Session(
      sessionCode: sessionCode ?? this.sessionCode,
      connectedTo: connectedTo ?? this.connectedTo,
      audioEnabled: audioEnabled ?? this.audioEnabled,
      videoEnabled: videoEnabled ?? this.videoEnabled,
      quality: quality ?? this.quality,
      expiresAt: expiresAt ?? this.expiresAt,
      joinedAt: joinedAt ?? this.joinedAt,
      recordingMode: recordingMode ?? this.recordingMode,
      role: role ?? this.role,
    );
  }
  
  /// Check if the session is active (not expired)
  bool get isActive {
    if (expiresAt == null) return true;
    return expiresAt!.isAfter(DateTime.now());
  }
  
  /// Calculate time remaining in the session
  Duration? get timeRemaining {
    if (expiresAt == null) return null;
    final now = DateTime.now();
    if (expiresAt!.isBefore(now)) return Duration.zero;
    return expiresAt!.difference(now);
  }
  
  /// Format the time remaining as a string (e.g. "10:30")
  String? get formattedTimeRemaining {
    final remaining = timeRemaining;
    if (remaining == null) return null;
    
    final minutes = remaining.inMinutes;
    final seconds = remaining.inSeconds % 60;
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }
  
  /// Check if this user is the creator of the session
  bool get isCreator => role == 'creator';
  
  /// Check if this session has a connected peer
  bool get hasConnectedPeer => connectedTo != null;
} 