# CamCheck Frontend

This is the frontend application for CamCheck, built using Flutter for cross-platform mobile support.

## Project Structure

```
frontend/
├── mobile/              # Flutter mobile application
│   ├── lib/            # Application source code
│   │   ├── api/        # API client and services
│   │   ├── models/     # Data models
│   │   ├── screens/    # UI screens
│   │   ├── services/   # Business logic services
│   │   ├── utils/      # Utility functions
│   │   └── widgets/    # Reusable UI components
│   ├── test/           # Test files
│   └── pubspec.yaml    # Flutter dependencies
└── README.md           # This file
```

## Technology Stack

- Flutter 3.x
- Dart 3.x
- Provider for state management
- WebRTC for real-time communication
- JWT for authentication
- HTTP for API communication
- WebSocket for real-time updates

## Features

- Real-time camera streaming
- WebRTC peer-to-peer communication
- User authentication
- Camera management
- Recording and snapshot functionality
- Motion detection
- Push notifications
- Offline support
- Dark/light theme

## Development Setup

1. Prerequisites:
   - Flutter SDK 3.x
   - Android Studio / Xcode
   - VS Code with Flutter extensions
   - Android/iOS emulator or physical device

2. Install dependencies:
   ```bash
   cd mobile
   flutter pub get
   ```

3. Run the application:
   ```bash
   flutter run
   ```

## Architecture

The application follows a clean architecture pattern:

1. **Presentation Layer** (`screens/`, `widgets/`)
   - UI components
   - Screen layouts
   - Widget state management

2. **Business Logic Layer** (`services/`)
   - Application logic
   - State management
   - Data processing

3. **Data Layer** (`api/`, `models/`)
   - API communication
   - Data models
   - Local storage

## State Management

The application uses Provider for state management:

- Global state for app-wide data
- Local state for screen-specific data
- Service classes for business logic
- Repository pattern for data access

## Testing

The application includes several types of tests:

1. Unit Tests:
   ```bash
   flutter test test/unit/
   ```

2. Widget Tests:
   ```bash
   flutter test test/widget/
   ```

3. Integration Tests:
   ```bash
   flutter test integration_test/
   ```

## Code Style

Follow the official Dart style guide:

- Use `dart format` for code formatting
- Follow the Flutter style guide
- Write descriptive variable and method names
- Include documentation for public APIs
- Keep methods focused and concise

## Building for Production

1. Android:
   ```bash
   flutter build apk --release
   ```

2. iOS:
   ```bash
   flutter build ios --release
   ```

## Performance Considerations

- Use lazy loading for images
- Implement pagination for lists
- Cache network responses
- Optimize WebRTC settings for mobile
- Use appropriate image formats and sizes
- Implement background mode efficiently

## Security

- Secure storage for tokens
- Certificate pinning for API calls
- Encryption for sensitive data
- Secure WebRTC configuration
- Input validation
- Error handling

## Contributing

1. Create a feature branch
2. Make your changes
3. Write/update tests
4. Run the test suite
5. Submit a pull request

## Dependencies

Key dependencies include:

- `provider`: State management
- `dio`: HTTP client
- `flutter_webrtc`: WebRTC support
- `shared_preferences`: Local storage
- `json_serializable`: JSON serialization
- `camera`: Camera access
- `path_provider`: File system access
- `flutter_secure_storage`: Secure storage

## Configuration

Environment-specific configuration is managed through:

- `.env` files for environment variables
- `flavor` configurations for different environments
- Build-time configuration for constants

## Debugging

1. Enable debugging:
   ```bash
   flutter run --debug
   ```

2. Use Flutter DevTools:
   - Performance profiling
   - Widget inspector
   - Network monitoring
   - Memory analysis

## Known Issues

- Document any known issues or limitations
- Include workarounds if available
- Track issues in the issue tracker

## Future Improvements

- Offline-first architecture
- Enhanced error handling
- Better state persistence
- Performance optimizations
- UI/UX improvements
- Additional platform support 