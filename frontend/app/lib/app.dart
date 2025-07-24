import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'config/theme.dart';
import 'api/auth_service.dart';
import 'api/session_service.dart';
import 'api/webrtc_service.dart';
import 'screens/auth/login_screen.dart';
import 'screens/camera/camera_screen.dart';
import 'screens/session/create_session_screen.dart';
import 'screens/session/join_session_screen.dart';
import 'screens/streaming/streaming_screen.dart';

/// Main app widget
class CamCheckApp extends StatelessWidget {
  const CamCheckApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CamCheck',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkTheme, // Default to dark theme
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.dark, // Force dark mode for now
      home: const LoginScreen(),
      builder: (context, child) {
        // Error handling
        ErrorWidget.builder = (FlutterErrorDetails details) {
          return Scaffold(
            backgroundColor: Colors.red,
            body: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.error_outline, color: Colors.white, size: 60),
                    const SizedBox(height: 16),
                    Text(
                      'An error occurred',
                      style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      details.exception.toString(),
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Colors.white,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 32),
                    ElevatedButton(
                      onPressed: () {
                        // Restart app or navigate to a safe screen
                        Navigator.pushReplacement(
                          context,
                          MaterialPageRoute(builder: (context) => const LoginScreen()),
                        );
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.white,
                        foregroundColor: Colors.red,
                      ),
                      child: const Text('Return to Login'),
                    ),
                  ],
                ),
              ),
            ),
          );
        };
        
        return child!;
      },
    );
  }
}

/// App router widget that handles navigation based on authentication state
class AppRouter extends StatefulWidget {
  /// Constructor
  const AppRouter({Key? key}) : super(key: key);

  @override
  State<AppRouter> createState() => _AppRouterState();
}

class _AppRouterState extends State<AppRouter> {
  @override
  void initState() {
    super.initState();
    _initializeAuth();
  }

  /// Initialize authentication service
  Future<void> _initializeAuth() async {
    final authService = Provider.of<AuthService>(context, listen: false);
    await authService.initialize();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<AuthService>(
      builder: (context, authService, _) {
        // Show loading indicator while initializing
        if (authService.isLoading) {
          return const Scaffold(
            body: Center(
              child: CircularProgressIndicator(),
            ),
          );
        }

        // Show login screen if not logged in
        if (!authService.isLoggedIn) {
          return const LoginScreen();
        }

        // Show main screen if logged in
        return Consumer<SessionService>(
          builder: (context, sessionService, _) {
            // Show streaming screen if session is active
            if (sessionService.isSessionActive) {
              return const StreamingScreen();
            }

            // Show camera screen if no active session
            return const CameraScreen();
          },
        );
      },
    );
  }
} 