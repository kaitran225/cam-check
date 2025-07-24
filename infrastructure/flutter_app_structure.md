# CamCheck Flutter App Structure

This document outlines the recommended project structure for the CamCheck Flutter mobile application.

## Project Directory Structure

```
camcheck_mobile/
├── android/                    # Android-specific configuration
├── ios/                        # iOS-specific configuration
├── lib/
│   ├── api/                    # API Client and services
│   │   ├── api_client.dart     # Main API client
│   │   ├── auth_service.dart   # Authentication service
│   │   ├── device_service.dart # Device management
│   │   ├── session_service.dart # Session management
│   │   └── webrtc_service.dart # WebRTC management
│   ├── config/                 # Configuration files
│   │   ├── app_config.dart     # App configuration
│   │   └── theme.dart          # App theme and styling
│   ├── models/                 # Data models
│   │   ├── device.dart         # Device model
│   │   ├── session.dart        # Session model
│   │   └── user.dart           # User model
│   ├── screens/                # App screens
│   │   ├── auth/               # Authentication screens
│   │   │   ├── login_screen.dart
│   │   │   └── register_screen.dart
│   │   ├── camera/             # Camera-related screens
│   │   │   ├── camera_screen.dart
│   │   │   └── settings_screen.dart
│   │   ├── session/            # Session-related screens
│   │   │   ├── create_session_screen.dart
│   │   │   └── join_session_screen.dart
│   │   ├── streaming/          # Streaming screens
│   │   │   ├── streaming_screen.dart
│   │   │   └── viewer_screen.dart
│   │   └── settings/           # Settings screens
│   │       ├── account_settings.dart
│   │       └── app_settings.dart
│   ├── utils/                  # Utility functions
│   │   ├── camera_utils.dart   # Camera helper functions
│   │   ├── notification_utils.dart # Notification helpers
│   │   └── storage_utils.dart  # Storage helpers
│   ├── widgets/                # Reusable widgets
│   │   ├── camera_preview.dart # Camera preview widget
│   │   ├── stream_controls.dart # Streaming controls
│   │   └── session_card.dart   # Session display card
│   ├── main.dart               # App entry point
│   └── app.dart                # App configuration and routes
├── pubspec.yaml                # Flutter dependencies
└── README.md                   # Project documentation
```

## Key Components

### API Client

The API client (`lib/api/api_client.dart`) serves as the central point for all communication with the backend API. It handles authentication, token management, and API requests.

### Authentication

Authentication is managed through the `AuthService` class, which uses JWT tokens for secure authentication with the server.

### Camera Handling

The app uses the `camera` package for local camera access and the `flutter_webrtc` package for WebRTC streaming.

### State Management

The app uses a combination of Provider and Riverpod for state management:

- `Provider` for simple state (e.g., theme, preferences)
- `Riverpod` for complex state (e.g., authentication, session management)

### Navigation

The app uses Flutter's built-in Navigator 2.0 for routing and navigation.

## Data Flow

1. User authenticates using the `AuthService`
2. API client stores JWT tokens securely
3. App registers the device with the backend
4. User can create or join sessions
5. Camera streams are managed through WebRTC
6. Push notifications are handled through Firebase Messaging

## Key Features

- **Local Camera Access**: Access to device cameras for streaming
- **WebRTC Streaming**: Peer-to-peer video streaming
- **Session Management**: Create and join viewing sessions
- **Push Notifications**: Receive alerts for new sessions, missed calls, etc.
- **Quality Settings**: Adjust streaming quality based on network conditions
- **Offline Mode**: Limited functionality when offline

## Dependencies

Key Flutter packages used in the project:

```yaml
dependencies:
  flutter:
    sdk: flutter
  camera: ^0.10.0
  flutter_webrtc: ^0.9.18
  http: ^0.13.5
  provider: ^6.0.5
  flutter_riverpod: ^2.1.3
  shared_preferences: ^2.0.15
  flutter_secure_storage: ^6.0.0
  firebase_core: ^2.4.1
  firebase_messaging: ^14.2.1
  connectivity_plus: ^3.0.2
  device_info_plus: ^8.0.0
  battery_plus: ^3.0.2
  permission_handler: ^10.2.0
  path_provider: ^2.0.11
  flutter_svg: ^1.1.6
  intl: ^0.18.0
```

## Getting Started

1. Clone the repository
2. Run `flutter pub get` to install dependencies
3. Configure Firebase for push notifications
4. Update the API base URL in `lib/config/app_config.dart`
5. Run the app with `flutter run`

## Testing

The app includes both unit tests and widget tests:

- Unit tests for API client and services
- Widget tests for key UI components
- Integration tests for critical user flows 