import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:firebase_core/firebase_core.dart';
import 'config/theme.dart';
import 'screens/auth/login_screen.dart';
import 'screens/camera/camera_screen.dart';
import 'screens/session/create_session_screen.dart';
import 'screens/session/join_session_screen.dart';
import 'api/auth_service.dart';
import 'api/session_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Initialize Firebase for push notifications
  try {
    await Firebase.initializeApp();
  } catch (e) {
    debugPrint('Failed to initialize Firebase: $e');
  }
  
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AuthService()),
        ChangeNotifierProvider(create: (_) => SessionService()),
      ],
      child: const MyApp(),
    ),
  );
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  bool _isInitialized = false;
  
  @override
  void initState() {
    super.initState();
    _initializeApp();
  }
  
  Future<void> _initializeApp() async {
    // Initialize services
    final authService = Provider.of<AuthService>(context, listen: false);
    await authService.initialize();
    
    setState(() {
      _isInitialized = true;
    });
  }
  
  @override
  Widget build(BuildContext context) {
    if (!_isInitialized) {
      return MaterialApp(
        title: 'CamCheck Mobile',
        theme: AppTheme.darkTheme,
        darkTheme: AppTheme.darkTheme,
        themeMode: ThemeMode.dark,
        debugShowCheckedModeBanner: false,
        home: const SplashScreen(),
      );
    }
    
    final authService = Provider.of<AuthService>(context);
    
    return MaterialApp(
      title: 'CamCheck Mobile',
      theme: AppTheme.darkTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.dark,
      debugShowCheckedModeBanner: false,
      initialRoute: authService.isLoggedIn ? '/camera' : '/login',
      routes: {
        '/login': (context) => const LoginScreen(),
        '/camera': (context) => const CameraScreen(),
        '/create_session': (context) => const CreateSessionScreen(),
        '/join_session': (context) => const JoinSessionScreen(),
      },
    );
  }
}

/// Splash screen shown during app initialization
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.brandBlack,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // App logo
            const Icon(
              Icons.camera,
              size: 80,
              color: AppTheme.brandWhite,
            ),
            const SizedBox(height: 24),
            const Text(
              'CamCheck',
              style: AppTheme.headingLarge,
            ),
            const SizedBox(height: 8),
            const Text(
              'Security Camera',
              style: AppTheme.bodyMedium,
            ),
            const SizedBox(height: 48),
            // Loading indicator
            const CircularProgressIndicator(),
            const SizedBox(height: 24),
            Text(
              'Loading...',
              style: AppTheme.bodySmall.copyWith(color: AppTheme.lightGray),
            ),
          ],
        ),
      ),
    );
  }
}
