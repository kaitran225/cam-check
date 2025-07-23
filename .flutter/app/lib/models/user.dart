/// User model representing a CamCheck user
class User {
  final String username;
  final List<String> roles;
  final Map<String, dynamic>? userData;
  final bool requiresPasswordChange;
  
  /// Constructor
  User({
    required this.username,
    required this.roles,
    this.userData,
    this.requiresPasswordChange = false,
  });
  
  /// Check if user has a specific role
  bool hasRole(String role) {
    return roles.contains(role);
  }
  
  /// Check if user is admin
  bool get isAdmin {
    return hasRole('ADMIN');
  }
  
  /// Check if user is superuser
  bool get isSuperuser {
    return hasRole('SUPERUSER');
  }
  
  /// Create User from JSON
  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      username: json['username'] as String,
      roles: List<String>.from(json['roles'] ?? []),
      userData: json['userData'] as Map<String, dynamic>?,
      requiresPasswordChange: json['requiresPasswordChange'] as bool? ?? false,
    );
  }
  
  /// Convert User to JSON
  Map<String, dynamic> toJson() {
    return {
      'username': username,
      'roles': roles,
      'userData': userData,
      'requiresPasswordChange': requiresPasswordChange,
    };
  }
} 