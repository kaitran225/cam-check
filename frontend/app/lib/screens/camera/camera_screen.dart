import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../../api/auth_service.dart';
import '../../api/webrtc_service.dart';
import '../../config/theme.dart';
import '../../widgets/app_button.dart';
import '../../widgets/camera_preview.dart';
import '../../widgets/stream_controls.dart';
import '../session/create_session_screen.dart';
import '../session/join_session_screen.dart';

/// Camera screen
class CameraScreen extends StatefulWidget {
  /// Constructor
  const CameraScreen({Key? key}) : super(key: key);

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  bool _isFrontCamera = true;
  bool _isInitializing = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initializeCamera();
  }

  /// Initialize camera
  Future<void> _initializeCamera() async {
    setState(() {
      _isInitializing = true;
      _error = null;
    });

    try {
      final webrtcService = Provider.of<WebRTCService>(context, listen: false);
      await webrtcService.initialize();
      await webrtcService.initLocalStream(audio: true, video: true);
    } catch (e) {
      setState(() {
        _error = e.toString();
      });
    } finally {
      setState(() {
        _isInitializing = false;
      });
    }
  }

  /// Flip camera between front and back
  Future<void> _flipCamera() async {
    setState(() {
      _isInitializing = true;
      _error = null;
    });

    try {
      final webrtcService = Provider.of<WebRTCService>(context, listen: false);
      
      // Stop current stream
      await webrtcService.stopLocalStream();
      
      // Toggle camera
      _isFrontCamera = !_isFrontCamera;
      
      // Initialize new stream with flipped camera
      await webrtcService.initLocalStream(audio: true, video: true);
    } catch (e) {
      setState(() {
        _error = e.toString();
      });
    } finally {
      setState(() {
        _isInitializing = false;
      });
    }
  }

  /// Take a snapshot of the current camera view
  void _takeSnapshot() {
    // TODO: Implement snapshot functionality
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Snapshot feature coming soon'),
      ),
    );
  }

  /// Navigate to create session screen
  void _navigateToCreateSession() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => const CreateSessionScreen()),
    );
  }

  /// Navigate to join session screen
  void _navigateToJoinSession() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => const JoinSessionScreen()),
    );
  }

  /// Logout
  Future<void> _logout() async {
    // Show confirmation dialog
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Logout'),
        content: const Text('Are you sure you want to logout?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Logout'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    // Stop camera and logout
    final webrtcService = Provider.of<WebRTCService>(context, listen: false);
    await webrtcService.stopLocalStream();

    final authService = Provider.of<AuthService>(context, listen: false);
    await authService.logout();
  }

  @override
  Widget build(BuildContext context) {
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;
    final authService = Provider.of<AuthService>(context);
    final isAdmin = authService.isAdmin;

    return Scaffold(
      backgroundColor: isDarkMode ? AppTheme.brandBlack : AppTheme.lightBackground,
      appBar: AppBar(
        title: const Text('CamCheck'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: _logout,
            tooltip: 'Logout',
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppTheme.spacingM),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // User info
              Row(
                children: [
                  Icon(
                    Icons.person,
                    color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandBlack,
                  ),
                  const SizedBox(width: AppTheme.spacingXs),
                  Text(
                    'Logged in as: ${authService.currentUser?.username ?? 'User'}',
                    style: AppTheme.bodyMedium.copyWith(
                      color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandBlack,
                    ),
                  ),
                  if (isAdmin) ...[
                    const SizedBox(width: AppTheme.spacingXs),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: AppTheme.actionBlue.withOpacity(0.2),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        'ADMIN',
                        style: AppTheme.caption.copyWith(
                          color: AppTheme.actionBlue,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
              const SizedBox(height: AppTheme.spacingM),
              
              // Camera preview
              Expanded(
                child: Consumer<WebRTCService>(
                  builder: (context, webrtcService, _) {
                    return CameraPreviewWidget(
                      stream: webrtcService.localStream,
                      isLocal: true,
                      isActive: !_isInitializing,
                      label: _isFrontCamera ? 'Front Camera' : 'Back Camera',
                      mirror: _isFrontCamera,
                    );
                  },
                ),
              ),
              const SizedBox(height: AppTheme.spacingS),
              
              // Error message
              if (_error != null)
                Container(
                  padding: const EdgeInsets.all(AppTheme.spacingXs),
                  margin: const EdgeInsets.only(bottom: AppTheme.spacingS),
                  decoration: BoxDecoration(
                    color: AppTheme.errorRed.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(4.0),
                  ),
                  child: Text(
                    'Camera Error: $_error',
                    style: AppTheme.bodySmall.copyWith(
                      color: AppTheme.errorRed,
                    ),
                  ),
                ),
              
              // Camera controls
              Consumer<WebRTCService>(
                builder: (context, webrtcService, _) {
                  return StreamControls(
                    audioEnabled: webrtcService.audioEnabled,
                    videoEnabled: webrtcService.videoEnabled,
                    onAudioToggle: webrtcService.toggleAudio,
                    onVideoToggle: webrtcService.toggleVideo,
                    onSnapshot: _takeSnapshot,
                    onFlipCamera: _flipCamera,
                  );
                },
              ),
              const SizedBox(height: AppTheme.spacingM),
              
              // Session buttons
              Row(
                children: [
                  // Create session button (admin only)
                  if (isAdmin)
                    Expanded(
                      child: AppButton(
                        text: 'Create Session',
                        icon: Icons.add,
                        onPressed: _navigateToCreateSession,
                      ),
                    ),
                  
                  // Join session button (all users)
                  Expanded(
                    child: Padding(
                      padding: isAdmin
                          ? const EdgeInsets.only(left: AppTheme.spacingXs)
                          : EdgeInsets.zero,
                      child: AppButton(
                        text: 'Join Session',
                        icon: Icons.login,
                        type: isAdmin ? ButtonType.secondary : ButtonType.primary,
                        onPressed: _navigateToJoinSession,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
} 