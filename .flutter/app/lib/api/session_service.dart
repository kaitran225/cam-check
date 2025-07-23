import 'package:flutter/material.dart';

import 'api_client.dart';
import '../models/session.dart';

/// Session service for managing camera viewing sessions
class SessionService extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();
  Session? _currentSession;
  bool _isLoading = false;
  String? _error;

  /// Get current session
  Session? get currentSession => _currentSession;

  /// Check if session is active
  bool get isSessionActive => _currentSession?.isActive ?? false;

  /// Check if service is loading
  bool get isLoading => _isLoading;

  /// Get error message
  String? get error => _error;

  /// Create a new session
  Future<Session?> createSession({
    int expirationMinutes = 10,
    bool audioEnabled = true,
    bool videoEnabled = true,
    String quality = 'medium',
    String recordingMode = 'none',
  }) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final session = await _apiClient.createSession(
        expirationMinutes: expirationMinutes,
        audioEnabled: audioEnabled,
        videoEnabled: videoEnabled,
        quality: quality,
        recordingMode: recordingMode,
      );

      if (session != null) {
        _currentSession = session;
        notifyListeners();
      } else {
        _error = 'Failed to create session';
      }

      return session;
    } catch (e) {
      _error = e.toString();
      return null;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Join a session
  Future<Session?> joinSession(
    String sessionCode, {
    bool audioEnabled = true,
    bool videoEnabled = true,
    String quality = 'medium',
  }) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final session = await _apiClient.joinSession(
        sessionCode,
        audioEnabled: audioEnabled,
        videoEnabled: videoEnabled,
        quality: quality,
      );

      if (session != null) {
        _currentSession = session;
        notifyListeners();
      } else {
        _error = 'Failed to join session';
      }

      return session;
    } catch (e) {
      _error = e.toString();
      return null;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// End the current session
  Future<bool> endSession() async {
    if (_currentSession == null) return true;

    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final success = await _apiClient.endSession(_currentSession!.sessionCode);

      if (success) {
        _currentSession = null;
        notifyListeners();
      } else {
        _error = 'Failed to end session';
      }

      return success;
    } catch (e) {
      _error = e.toString();
      return false;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Update session settings
  void updateSessionSettings({
    bool? audioEnabled,
    bool? videoEnabled,
    String? quality,
  }) {
    if (_currentSession == null) return;

    _currentSession = _currentSession!.copyWith(
      audioEnabled: audioEnabled,
      videoEnabled: videoEnabled,
      quality: quality,
    );

    notifyListeners();
  }

  /// Clear error
  void clearError() {
    _error = null;
    notifyListeners();
  }

  /// Clear session
  void clearSession() {
    _currentSession = null;
    notifyListeners();
  }
} 