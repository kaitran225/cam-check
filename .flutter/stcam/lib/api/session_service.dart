import 'package:flutter/material.dart';
import 'api_client.dart';
import '../models/session.dart';
import '../config/app_config.dart';

/// Service for managing camera viewing sessions
class SessionService extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();
  
  Session? _currentSession;
  List<Session> _availableSessions = [];
  bool _isLoading = false;
  String? _errorMessage;
  
  // Getters
  Session? get currentSession => _currentSession;
  List<Session> get availableSessions => _availableSessions;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  bool get hasActiveSession => _currentSession != null;
  
  /// Create a new camera viewing session
  Future<Session?> createSession({
    int expirationMinutes = 10,
    bool audioEnabled = true,
    bool videoEnabled = true,
    String quality = 'medium',
    String recordingMode = 'none',
  }) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();
    
    try {
      final response = await _apiClient.createSession(
        expirationMinutes: expirationMinutes,
        audioEnabled: audioEnabled,
        videoEnabled: videoEnabled,
        quality: quality,
        recordingMode: recordingMode,
      );
      
      if (response.containsKey('sessionCode')) {
        _currentSession = Session(
          sessionCode: response['sessionCode'],
          audioEnabled: audioEnabled,
          videoEnabled: videoEnabled,
          quality: quality,
          expiresAt: response['expiresAt'] != null 
              ? DateTime.parse(response['expiresAt']) 
              : DateTime.now().add(Duration(minutes: expirationMinutes)),
          recordingMode: recordingMode,
          role: 'creator',
        );
        
        _isLoading = false;
        notifyListeners();
        return _currentSession;
      } else {
        _errorMessage = 'Failed to create session';
        _isLoading = false;
        notifyListeners();
        return null;
      }
    } catch (e) {
      _errorMessage = 'Error creating session: ${e.toString()}';
      _isLoading = false;
      notifyListeners();
      return null;
    }
  }
  
  /// Join an existing camera viewing session
  Future<Session?> joinSession(
    String sessionCode, {
    bool audioEnabled = true,
    bool videoEnabled = true,
    String quality = 'medium',
  }) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();
    
    try {
      final response = await _apiClient.joinSession(
        sessionCode,
        audioEnabled: audioEnabled,
        videoEnabled: videoEnabled,
        quality: quality,
      );
      
      if (response.containsKey('sessionId')) {
        _currentSession = Session(
          sessionCode: sessionCode,
          connectedTo: response['connectedTo'],
          audioEnabled: audioEnabled,
          videoEnabled: videoEnabled,
          quality: quality,
          joinedAt: response['joinedAt'] != null 
              ? DateTime.parse(response['joinedAt']) 
              : DateTime.now(),
          role: 'joiner',
        );
        
        _isLoading = false;
        notifyListeners();
        return _currentSession;
      } else {
        _errorMessage = 'Failed to join session';
        _isLoading = false;
        notifyListeners();
        return null;
      }
    } catch (e) {
      _errorMessage = 'Error joining session: ${e.toString()}';
      _isLoading = false;
      notifyListeners();
      return null;
    }
  }
  
  /// End the current session
  Future<bool> endSession() async {
    if (_currentSession == null) return false;
    
    _isLoading = true;
    notifyListeners();
    
    try {
      final success = await _apiClient.endSession(_currentSession!.sessionCode);
      
      if (success) {
        _currentSession = null;
        _isLoading = false;
        notifyListeners();
        return true;
      } else {
        _errorMessage = 'Failed to end session';
        _isLoading = false;
        notifyListeners();
        return false;
      }
    } catch (e) {
      _errorMessage = 'Error ending session: ${e.toString()}';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }
  
  /// Get the current user's active session
  Future<Session?> getCurrentSession() async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();
    
    try {
      // This would be a real API call in a production app
      // For now, we'll just return the current session if it exists
      if (_currentSession != null) {
        _isLoading = false;
        notifyListeners();
        return _currentSession;
      }
      
      // In a real app, you would call the API to get the current session
      // final response = await _apiClient.getCurrentSession();
      // if (response.containsKey('sessionCode')) {
      //   _currentSession = Session.fromJson(response);
      //   _isLoading = false;
      //   notifyListeners();
      //   return _currentSession;
      // }
      
      _isLoading = false;
      notifyListeners();
      return null;
    } catch (e) {
      _errorMessage = 'Error getting current session: ${e.toString()}';
      _isLoading = false;
      notifyListeners();
      return null;
    }
  }
  
  /// Clear any error messages
  void clearError() {
    _errorMessage = null;
    notifyListeners();
  }
  
  /// Update session settings
  Future<bool> updateSessionSettings({
    bool? audioEnabled,
    bool? videoEnabled,
    String? quality,
    String? recordingMode,
  }) async {
    if (_currentSession == null) return false;
    
    // Update local session state
    _currentSession = _currentSession!.copyWith(
      audioEnabled: audioEnabled ?? _currentSession!.audioEnabled,
      videoEnabled: videoEnabled ?? _currentSession!.videoEnabled,
      quality: quality ?? _currentSession!.quality,
      recordingMode: recordingMode ?? _currentSession!.recordingMode,
    );
    
    notifyListeners();
    
    // In a real app, you would call the API to update the session settings
    // try {
    //   final response = await _apiClient.updateSessionSettings(
    //     _currentSession!.sessionCode,
    //     audioEnabled: audioEnabled,
    //     videoEnabled: videoEnabled,
    //     quality: quality,
    //     recordingMode: recordingMode,
    //   );
    //   return response['status'] == 'success';
    // } catch (e) {
    //   _errorMessage = 'Error updating session settings: ${e.toString()}';
    //   notifyListeners();
    //   return false;
    // }
    
    return true;
  }
  
  /// Start a session timer to track expiration
  void startSessionTimer() {
    if (_currentSession?.expiresAt == null) return;
    
    // Set up a timer to notify when the session is about to expire
    final expiresAt = _currentSession!.expiresAt!;
    final now = DateTime.now();
    final timeUntilExpiration = expiresAt.difference(now);
    
    if (timeUntilExpiration.isNegative) {
      // Session already expired
      _currentSession = null;
      notifyListeners();
      return;
    }
    
    // Notify 1 minute before expiration
    if (timeUntilExpiration.inMinutes > 1) {
      Future.delayed(timeUntilExpiration - const Duration(minutes: 1), () {
        if (_currentSession != null) {
          // Session is about to expire
          notifyListeners();
        }
      });
    }
    
    // Notify when session expires
    Future.delayed(timeUntilExpiration, () {
      if (_currentSession != null) {
        // Session expired
        _currentSession = null;
        notifyListeners();
      }
    });
  }
}
