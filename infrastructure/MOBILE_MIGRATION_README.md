# CamCheck Mobile Migration Guide

## Overview

This document outlines the migration path from the CamCheck web application to a Flutter-based mobile app. The backend API has been enhanced to support both web and mobile clients during the transition period.

## Table of Contents

1. [Migration Strategy](#migration-strategy)
2. [API Enhancements](#api-enhancements)
3. [Authentication](#authentication)
4. [Device Management](#device-management)
5. [Session Management](#session-management)
6. [WebRTC for Mobile](#webrtc-for-mobile)
7. [Push Notifications](#push-notifications)
8. [Mobile Configuration](#mobile-configuration)
9. [Flutter API Client](#flutter-api-client)
10. [Migration Timeline](#migration-timeline)

## Migration Strategy

The migration follows a phased approach:

1. **Phase 1: API Enhancement** (Completed)
   - Add mobile-specific API endpoints
   - Implement JWT authentication
   - Add device management
   - Create REST alternatives to WebSocket endpoints
   - Enhance WebRTC for mobile clients
   - Add push notification support

2. **Phase 2: Flutter App Development** (Current)
   - Create Flutter app with API client
   - Implement local camera access
   - Develop mobile UI components
   - Add WebRTC integration
   - Implement push notification handling

3. **Phase 3: Beta Testing** (Next)
   - Release beta version to limited users
   - Collect feedback and iterate
   - Fix compatibility issues

4. **Phase 4: Full Migration** (Future)
   - Release mobile app to all users
   - Gradually deprecate web interface
   - Complete migration to mobile-first approach

## API Enhancements

All mobile-specific API endpoints are available under the `/api/v2/` prefix. The original API endpoints continue to work under `/api/v1/` for backward compatibility.

### Authentication

The API now supports JWT-based authentication:

```
POST /api/v2/auth/login
POST /api/v2/auth/refresh
POST /api/v2/auth/logout
GET  /api/v2/auth/validate
```

### Device Management

New endpoints for device registration and management:

```
POST   /api/v2/devices/register
GET    /api/v2/devices
GET    /api/v2/devices/{deviceId}
PUT    /api/v2/devices/{deviceId}
DELETE /api/v2/devices/{deviceId}
PUT    /api/v2/devices/{deviceId}/fcm-token
```

### Session Management

REST alternatives to WebSocket-based session management:

```
POST   /api/v2/sessions
POST   /api/v2/sessions/{sessionCode}/join
DELETE /api/v2/sessions/{sessionCode}
GET    /api/v2/sessions
GET    /api/v2/sessions/my-session
PUT    /api/v2/sessions/{sessionCode}/settings
```

### WebRTC for Mobile

Enhanced WebRTC endpoints for mobile optimization:

```
GET    /api/v2/webrtc/config
POST   /api/v2/webrtc/connect/{receiverId}
POST   /api/v2/webrtc/candidates/{connectionId}
POST   /api/v2/webrtc/sdp/{connectionId}
GET    /api/v2/webrtc/status/{connectionId}
DELETE /api/v2/webrtc/{connectionId}
```

### Push Notifications

Support for Firebase Cloud Messaging (FCM) for push notifications:

- Session invitations
- Missed calls
- Motion detection alerts
- System alerts

### Mobile Configuration

Mobile-specific configuration endpoints:

```
GET  /api/v2/config
POST /api/v2/config/preferences
GET  /api/v2/config/quality-options
GET  /api/v2/config/version
```

## Flutter API Client

A Flutter API client has been provided in `flutter_api_client_example.dart` to help developers get started with the mobile app development. This client handles:

- Authentication and token management
- Device registration
- Session creation and management
- WebRTC signaling
- Push notification registration
- Network and battery awareness for optimal performance

### Required Flutter Dependencies

```yaml
dependencies:
  http: ^0.13.5
  shared_preferences: ^2.0.15
  flutter_secure_storage: ^6.0.0
  connectivity_plus: ^3.0.2
  device_info_plus: ^8.0.0
  battery_plus: ^3.0.2
  firebase_core: ^2.4.1
  firebase_messaging: ^14.2.1
  flutter_webrtc: ^0.9.18
```

### Usage Example

```dart
// Initialize the API client
final api = CamCheckApiClient();
await api.initialize();

// Login
final loggedIn = await api.login('username', 'password');
if (loggedIn) {
  // Get mobile configuration
  final config = await api.getMobileConfig();
  
  // Create a session
  final session = await api.createSession(
    audioEnabled: true,
    videoEnabled: true,
    quality: 'medium',
  );
  
  // Get WebRTC configuration
  final webrtcConfig = await api.getWebRTCConfig();
  
  // Initialize WebRTC connection
  final connection = await api.initializeWebRTCConnection('otherUser');
}
```

## Migration Timeline

1. **Q2 2023**: Complete API enhancements (Completed)
2. **Q3 2023**: Develop Flutter app (In Progress)
3. **Q4 2023**: Beta testing
4. **Q1 2024**: Full release and gradual web deprecation
5. **Q2 2024**: Complete migration to mobile-first approach

## Conclusion

This migration provides a path to transition from the web interface to a mobile-first approach while maintaining backward compatibility. The enhanced API supports both web and mobile clients during the transition period, allowing for a gradual migration of users to the mobile platform.

For any questions or issues, please contact the development team. 