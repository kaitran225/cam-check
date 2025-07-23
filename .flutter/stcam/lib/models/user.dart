/// Model class for user data
class User {
  final String username;
  final List<String> roles;
  final Map<String, dynamic>? preferences;
  final bool requiresPasswordChange;
  
  User({
    required this.username,
    required this.roles,
    this.preferences,
    this.requiresPasswordChange = false,
  });
  
  /// Create a User from JSON data
  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      username: json['username'],
      roles: List<String>.from(json['roles'] ?? []),
      preferences: json['preferences'],
      requiresPasswordChange: json['requiresPasswordChange'] ?? false,
    );
  }
  
  /// Convert User to JSON
  Map<String, dynamic> toJson() {
    return {
      'username': username,
      'roles': roles,
      'preferences': preferences,
      'requiresPasswordChange': requiresPasswordChange,
    };
  }
  
  /// Check if the user has a specific role
  bool hasRole(String role) {
    return roles.contains(role);
  }
  
  /// Check if the user is an admin
  bool get isAdmin => roles.contains('ADMIN');
  
  /// Check if the user is a superuser
  bool get isSuperuser => roles.contains('SUPERUSER');
  
  /// Check if the user is a regular user
  bool get isUser => roles.contains('USER');
  
  /// Create a copy of this User with some updated fields
  User copyWith({
    String? username,
    List<String>? roles,
    Map<String, dynamic>? preferences,
    bool? requiresPasswordChange,
  }) {
    return User(
      username: username ?? this.username,
      roles: roles ?? this.roles,
      preferences: preferences ?? this.preferences,
      requiresPasswordChange: requiresPasswordChange ?? this.requiresPasswordChange,
    );
  }
} 