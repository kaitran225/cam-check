import 'package:flutter/material.dart';
import 'api_client.dart';

/// Authentication service for handling user login/logout and session management
class AuthService extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();
  
  bool _isLoggedIn = false;
  String? _username;
  List<String> _roles = [];
  bool _isLoading = false;
  String? _errorMessage;
  
  // Getters
  bool get isLoggedIn => _isLoggedIn;
  String? get username => _username;
  List<String> get roles => _roles;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  bool get isAdmin => _roles.contains('ADMIN');
  bool get isSuperuser => _roles.contains('SUPERUSER');
  
  /// Initialize the auth service
  Future<void> initialize() async {
    await _apiClient.initialize();
    _isLoggedIn = _apiClient.isLoggedIn;
    if (_isLoggedIn) {
      _username = _apiClient.userId;
      notifyListeners();
    }
  }
  
  /// Login with username and password
  Future<bool> login(String username, String password) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();
    
    try {
      final success = await _apiClient.login(username, password);
      
      if (success) {
        _isLoggedIn = true;
        _username = username;
        
        // Get user configuration
        try {
          final config = await _apiClient.getMobileConfig();
          if (config.containsKey('roles')) {
            _roles = List<String>.from(config['roles']);
          }
        } catch (e) {
          debugPrint('Error getting user config: $e');
        }
      } else {
        _errorMessage = 'Invalid username or password';
      }
      
      _isLoading = false;
      notifyListeners();
      return success;
    } catch (e) {
      _isLoading = false;
      _errorMessage = 'Login failed: ${e.toString()}';
      notifyListeners();
      return false;
    }
  }
  
  /// Logout the current user
  Future<void> logout() async {
    _isLoading = true;
    notifyListeners();
    
    try {
      await _apiClient.logout();
    } catch (e) {
      debugPrint('Error during logout: $e');
    } finally {
      _isLoggedIn = false;
      _username = null;
      _roles = [];
      _isLoading = false;
      notifyListeners();
    }
  }
  
  /// Clear any error messages
  void clearError() {
    _errorMessage = null;
    notifyListeners();
  }
}
