import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_client.dart';
import '../models/user.dart';

/// Authentication service for handling user authentication
class AuthService extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();
  User? _currentUser;
  bool _isLoading = false;
  String? _error;

  /// Get current user
  User? get currentUser => _currentUser;

  /// Check if user is logged in
  bool get isLoggedIn => _currentUser != null;

  /// Check if service is loading
  bool get isLoading => _isLoading;

  /// Get error message
  String? get error => _error;

  /// Initialize auth service
  Future<void> initialize() async {
    _isLoading = true;
    notifyListeners();

    try {
      await _apiClient.initialize();
      
      if (_apiClient.isLoggedIn) {
        // Try to get user config to validate token
        try {
          await _apiClient.getMobileConfig();
          
          // Set current user from stored data
          final prefs = await SharedPreferences.getInstance();
          final userData = prefs.getString('user_data');
          
          if (userData != null) {
            _currentUser = User.fromJson(
              Map<String, dynamic>.from(
                _parseJson(userData) as Map
              )
            );
          }
        } catch (e) {
          // Token is invalid, clear it
          await _apiClient.logout();
          _currentUser = null;
        }
      }
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Login with username and password
  Future<bool> login(String username, String password) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final user = await _apiClient.login(username, password);
      
      if (user != null) {
        _currentUser = user;
        
        // Save user data to shared preferences
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('user_data', _stringifyJson(user.toJson()));
        
        notifyListeners();
        return true;
      } else {
        _error = 'Invalid username or password';
        notifyListeners();
        return false;
      }
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return false;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Logout
  Future<void> logout() async {
    _isLoading = true;
    notifyListeners();

    try {
      await _apiClient.logout();
      
      // Clear user data from shared preferences
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove('user_data');
      
      _currentUser = null;
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Check if user has a specific role
  bool hasRole(String role) {
    return _currentUser?.hasRole(role) ?? false;
  }

  /// Check if user is admin
  bool get isAdmin => _currentUser?.isAdmin ?? false;

  /// Check if user is superuser
  bool get isSuperuser => _currentUser?.isSuperuser ?? false;

  /// Clear error
  void clearError() {
    _error = null;
    notifyListeners();
  }

  /// Helper method to stringify JSON
  String _stringifyJson(Map<String, dynamic> json) {
    return json.toString();
  }

  /// Helper method to parse JSON
  dynamic _parseJson(String jsonString) {
    // Convert string representation of map to actual map
    final trimmed = jsonString.trim();
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
      final map = <String, dynamic>{};
      final keyValuePairs = trimmed
          .substring(1, trimmed.length - 1)
          .split(', ')
          .where((pair) => pair.isNotEmpty);

      for (final pair in keyValuePairs) {
        final parts = pair.split(': ');
        if (parts.length == 2) {
          final key = parts[0].trim();
          final value = parts[1].trim();
          
          // Handle different value types
          if (value == 'null') {
            map[key] = null;
          } else if (value == 'true') {
            map[key] = true;
          } else if (value == 'false') {
            map[key] = false;
          } else if (value.startsWith('[') && value.endsWith(']')) {
            // Handle lists
            final listStr = value.substring(1, value.length - 1);
            final items = listStr.split(', ').where((item) => item.isNotEmpty).toList();
            map[key] = items;
          } else if (value.startsWith('{') && value.endsWith('}')) {
            // Handle nested maps
            map[key] = _parseJson(value);
          } else {
            // Handle strings and numbers
            if (value.startsWith("'") && value.endsWith("'")) {
              map[key] = value.substring(1, value.length - 1);
            } else if (int.tryParse(value) != null) {
              map[key] = int.parse(value);
            } else if (double.tryParse(value) != null) {
              map[key] = double.parse(value);
            } else {
              map[key] = value;
            }
          }
        }
      }
      return map;
    }
    return null;
  }
} 