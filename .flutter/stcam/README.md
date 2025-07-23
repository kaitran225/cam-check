# CamCheck Mobile

A Flutter-based mobile application for CamCheck security camera system.

## Overview

CamCheck Mobile is a mobile client for the CamCheck security camera system. It allows users to:

- View and control their security cameras
- Create and join viewing sessions
- Take snapshots and record video
- Receive push notifications for motion detection and other events

## Features

- **Camera Access**: Access to device cameras for streaming
- **WebRTC Streaming**: Peer-to-peer video streaming
- **Session Management**: Create and join viewing sessions
- **Push Notifications**: Receive alerts for new sessions, missed calls, etc.
- **Quality Settings**: Adjust streaming quality based on network conditions
- **Offline Mode**: Limited functionality when offline

## Project Structure

The project follows a modular architecture:

```
lib/
├── api/                    # API Client and services
│   ├── api_client.dart     # Main API client
│   ├── auth_service.dart   # Authentication service
│   └── ...
├── config/                 # Configuration files
│   ├── app_config.dart     # App configuration
│   └── theme.dart          # App theme and styling
├── models/                 # Data models
├── screens/                # App screens
│   ├── auth/               # Authentication screens
│   ├── camera/             # Camera-related screens
│   └── ...
├── utils/                  # Utility functions
├── widgets/                # Reusable widgets
└── main.dart               # App entry point
```

## Getting Started

1. Ensure Flutter is installed and set up on your development machine
2. Clone the repository
3. Run `flutter pub get` to install dependencies
4. Configure Firebase for push notifications
5. Run the app with `flutter run`

## Dependencies

- Flutter SDK
- Camera package for camera access
- WebRTC for video streaming
- Firebase for push notifications
- HTTP for API communication
- Provider and Riverpod for state management

## API Integration

The app integrates with the CamCheck API v2 for all backend operations. The API client handles:

- Authentication and token management
- Device registration
- Session creation and management
- WebRTC signaling
- Push notification registration

## Design

The app follows the CamCheck design system, featuring:

- Dark mode by default (with light mode option)
- Minimalist UI with focus on camera feed
- Consistent color scheme and typography
- Responsive layout for various device sizes

## License

This project is proprietary and confidential.
