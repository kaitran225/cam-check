import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../api/auth_service.dart';
import '../../api/session_service.dart';
import '../../api/webrtc_service.dart';
import '../../config/theme.dart';
import '../../widgets/app_button.dart';
import '../../widgets/camera_preview.dart';
import '../../widgets/session_card.dart';
import '../../widgets/stream_controls.dart';
import '../camera/camera_screen.dart';

/// Streaming screen for active sessions
class StreamingScreen extends StatefulWidget {
  /// Constructor
  const StreamingScreen({Key? key}) : super(key: key);

  @override
  State<StreamingScreen> createState() => _StreamingScreenState();
}

class _StreamingScreenState extends State<StreamingScreen> {
  bool _isLocalFullscreen = false;
  bool _showSessionInfo = false;
  bool _isInitializing = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initializeConnection();
  }

  /// Initialize WebRTC connection
  Future<void> _initializeConnection() async {
    setState(() {
      _isInitializing = true;
      _error = null;
    });

    try {
      final webrtcService = Provider.of<WebRTCService>(context, listen: false);
      final sessionService = Provider.of<SessionService>(context, listen: false);
      
      if (sessionService.currentSession == null) {
        throw Exception('No active session');
      }

      final session = sessionService.currentSession!;
      
      // Initialize WebRTC connection
      if (session.role == 'creator') {
        // Wait for someone to connect
      } else if (session.connectedTo != null) {
        // Connect to the session creator
        await webrtcService.initConnection(session.connectedTo!);
      }
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

  /// Toggle between local and remote views
  void _toggleFullscreen() {
    setState(() {
      _isLocalFullscreen = !_isLocalFullscreen;
    });
  }

  /// Toggle session info visibility
  void _toggleSessionInfo() {
    setState(() {
      _showSessionInfo = !_showSessionInfo;
    });
  }

  /// End the current session
  Future<void> _endSession() async {
    // Show confirmation dialog
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('End Session'),
        content: const Text('Are you sure you want to end this session?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('End Session'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    // End session
    final sessionService = Provider.of<SessionService>(context, listen: false);
    final webrtcService = Provider.of<WebRTCService>(context, listen: false);
    
    // End WebRTC connection
    await webrtcService.endConnection();
    
    // End session
    final success = await sessionService.endSession();
    
    if (success && mounted) {
      // Navigate back to camera screen
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (context) => const CameraScreen()),
      );
    }
  }

  /// Share session code
  void _shareSessionCode() {
    final sessionService = Provider.of<SessionService>(context, listen: false);
    final session = sessionService.currentSession;
    
    if (session == null) return;
    
    // Copy to clipboard
    Clipboard.setData(ClipboardData(text: session.sessionCode));
    
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Session code copied to clipboard'),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;
    
    return Scaffold(
      backgroundColor: isDarkMode ? AppTheme.brandBlack : AppTheme.lightBackground,
      appBar: AppBar(
        title: const Text('Active Session'),
        actions: [
          IconButton(
            icon: Icon(_showSessionInfo ? Icons.info_outline : Icons.info),
            onPressed: _toggleSessionInfo,
            tooltip: 'Session Info',
          ),
        ],
      ),
      body: SafeArea(
        child: Consumer3<AuthService, SessionService, WebRTCService>(
          builder: (context, authService, sessionService, webrtcService, _) {
            final session = sessionService.currentSession;
            final isAdmin = authService.isAdmin;
            
            if (session == null) {
              return const Center(
                child: Text('No active session'),
              );
            }
            
            return Padding(
              padding: const EdgeInsets.all(AppTheme.spacingM),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Session info
                  if (_showSessionInfo)
                    Padding(
                      padding: const EdgeInsets.only(bottom: AppTheme.spacingM),
                      child: SessionCard(
                        session: session,
                        showCode: isAdmin,
                        onEndSession: _endSession,
                        onShareCode: isAdmin ? _shareSessionCode : null,
                      ),
                    ),
                  
                  // Video streams
                  Expanded(
                    child: _isLocalFullscreen
                        ? _buildLocalFullscreenView(webrtcService)
                        : _buildRemoteFullscreenView(webrtcService),
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
                        'Error: $_error',
                        style: AppTheme.bodySmall.copyWith(
                          color: AppTheme.errorRed,
                        ),
                      ),
                    ),
                  
                  // Stream controls
                  StreamControls(
                    audioEnabled: webrtcService.audioEnabled,
                    videoEnabled: webrtcService.videoEnabled,
                    sessionActive: true,
                    onAudioToggle: webrtcService.toggleAudio,
                    onVideoToggle: webrtcService.toggleVideo,
                    onSnapshot: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Snapshot feature coming soon'),
                        ),
                      );
                    },
                    onFlipCamera: _toggleFullscreen,
                    onEndSession: _endSession,
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
  
  /// Build view with local video fullscreen
  Widget _buildLocalFullscreenView(WebRTCService webrtcService) {
    return Stack(
      children: [
        // Local video (fullscreen)
        CameraPreviewWidget(
          stream: webrtcService.localStream,
          isLocal: true,
          isActive: !_isInitializing,
          label: 'Your Camera',
          mirror: true,
          onTap: _toggleFullscreen,
        ),
        
        // Remote video (picture-in-picture)
        if (webrtcService.remoteStream != null)
          Positioned(
            bottom: AppTheme.spacingS,
            right: AppTheme.spacingS,
            width: 120,
            height: 160,
            child: GestureDetector(
              onTap: _toggleFullscreen,
              child: CameraPreviewWidget(
                stream: webrtcService.remoteStream,
                isLocal: false,
                isActive: true,
                label: 'Remote Camera',
                mirror: false,
              ),
            ),
          ),
      ],
    );
  }
  
  /// Build view with remote video fullscreen
  Widget _buildRemoteFullscreenView(WebRTCService webrtcService) {
    return Stack(
      children: [
        // Remote video (fullscreen)
        CameraPreviewWidget(
          stream: webrtcService.remoteStream,
          isLocal: false,
          isActive: webrtcService.isConnected,
          label: 'Remote Camera',
          mirror: false,
          onTap: _toggleFullscreen,
        ),
        
        // Local video (picture-in-picture)
        Positioned(
          bottom: AppTheme.spacingS,
          right: AppTheme.spacingS,
          width: 120,
          height: 160,
          child: GestureDetector(
            onTap: _toggleFullscreen,
            child: CameraPreviewWidget(
              stream: webrtcService.localStream,
              isLocal: true,
              isActive: !_isInitializing,
              label: 'Your Camera',
              mirror: true,
            ),
          ),
        ),
        
        // Connection status
        if (!webrtcService.isConnected && !_isInitializing)
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const CircularProgressIndicator(),
                const SizedBox(height: AppTheme.spacingS),
                Text(
                  'Connecting... (${webrtcService.connectionStatus})',
                  style: AppTheme.bodyMedium.copyWith(
                    color: AppTheme.brandWhite,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
} 