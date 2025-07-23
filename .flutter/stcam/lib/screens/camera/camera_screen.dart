import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:provider/provider.dart';
import '../../api/auth_service.dart';
import '../../config/theme.dart';
import '../../config/app_config.dart';
import '../../widgets/camera_preview.dart';

/// Camera screen for viewing and controlling the camera
class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> with WidgetsBindingObserver {
  List<CameraDescription>? _cameras;
  CameraController? _cameraController;
  bool _isCameraInitialized = false;
  bool _isStreaming = false;
  bool _isFrontCamera = false;
  bool _isFlashOn = false;
  
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initializeCamera();
  }
  
  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _cameraController?.dispose();
    super.dispose();
  }
  
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // App state changed before we got the chance to initialize the camera
    if (_cameraController == null || !_cameraController!.value.isInitialized) {
      return;
    }
    
    if (state == AppLifecycleState.inactive) {
      _cameraController?.dispose();
    } else if (state == AppLifecycleState.resumed) {
      _initializeCamera();
    }
  }
  
  Future<void> _initializeCamera() async {
    try {
      // Get available cameras
      _cameras = await availableCameras();
      
      if (_cameras == null || _cameras!.isEmpty) {
        _showErrorMessage('No cameras available');
        return;
      }
      
      // Use the first camera by default (usually the back camera)
      final cameraIndex = _isFrontCamera ? 1 : 0;
      final cameraToUse = _cameras!.length > cameraIndex ? _cameras![cameraIndex] : _cameras!.first;
      
      // Initialize the controller
      _cameraController = CameraController(
        cameraToUse,
        ResolutionPreset.medium,
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.jpeg,
      );
      
      await _cameraController!.initialize();
      
      if (mounted) {
        setState(() {
          _isCameraInitialized = true;
        });
      }
    } catch (e) {
      _showErrorMessage('Failed to initialize camera: $e');
    }
  }
  
  Future<void> _toggleCameraDirection() async {
    setState(() {
      _isCameraInitialized = false;
      _isFrontCamera = !_isFrontCamera;
    });
    
    await _cameraController?.dispose();
    await _initializeCamera();
  }
  
  Future<void> _toggleFlash() async {
    if (_cameraController == null || !_cameraController!.value.isInitialized) {
      return;
    }
    
    try {
      if (_isFlashOn) {
        await _cameraController!.setFlashMode(FlashMode.off);
      } else {
        await _cameraController!.setFlashMode(FlashMode.torch);
      }
      
      setState(() {
        _isFlashOn = !_isFlashOn;
      });
    } catch (e) {
      _showErrorMessage('Failed to toggle flash: $e');
    }
  }
  
  Future<void> _takeSnapshot() async {
    if (_cameraController == null || !_cameraController!.value.isInitialized) {
      return;
    }
    
    try {
      final image = await _cameraController!.takePicture();
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Snapshot saved'),
            duration: Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      _showErrorMessage('Failed to take snapshot: $e');
    }
  }
  
  void _toggleStreaming() {
    setState(() {
      _isStreaming = !_isStreaming;
    });
    
    if (_isStreaming) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Streaming started'),
          duration: Duration(seconds: 2),
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Streaming stopped'),
          duration: Duration(seconds: 2),
        ),
      );
    }
  }
  
  void _showErrorMessage(String message) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: AppTheme.errorRed,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }
  
  void _showCreateSessionDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Create Session'),
        content: const Text('Create a new viewing session?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('CANCEL'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              // TODO: Implement session creation
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Session created: 123456'),
                  duration: Duration(seconds: 3),
                ),
              );
            },
            child: const Text('CREATE'),
          ),
        ],
      ),
    );
  }
  
  void _logout() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Logout'),
        content: const Text('Are you sure you want to logout?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('CANCEL'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              final authService = Provider.of<AuthService>(context, listen: false);
              authService.logout().then((_) {
                Navigator.pushReplacementNamed(context, '/login');
              });
            },
            child: const Text('LOGOUT'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authService = Provider.of<AuthService>(context);
    
    return Scaffold(
      backgroundColor: AppTheme.brandBlack,
      appBar: AppBar(
        title: const Text('CamCheck'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () {
              // TODO: Navigate to settings screen
            },
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: _isCameraInitialized
                ? ClipRRect(
                    borderRadius: BorderRadius.circular(AppConfig.defaultBorderRadius),
                    child: CustomCameraPreview(_cameraController!),
                  )
                : const Center(
                    child: CircularProgressIndicator(),
                  ),
          ),
          // Camera status indicator
          Container(
            padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
            color: _isStreaming ? AppTheme.successGreen : AppTheme.brandGray,
            child: Row(
              children: [
                Icon(
                  _isStreaming ? Icons.circle : Icons.circle_outlined,
                  color: _isStreaming ? AppTheme.brandWhite : AppTheme.lightGray,
                  size: 12,
                ),
                const SizedBox(width: 8),
                Text(
                  _isStreaming ? 'Streaming' : 'Not Streaming',
                  style: AppTheme.bodySmall,
                ),
                const Spacer(),
                Text(
                  'User: ${authService.username ?? "Unknown"}',
                  style: AppTheme.caption,
                ),
              ],
            ),
          ),
          // Camera controls
          Container(
            padding: const EdgeInsets.all(16),
            color: AppTheme.brandGray,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                // Toggle camera
                IconButton(
                  icon: const Icon(Icons.flip_camera_ios),
                  onPressed: _toggleCameraDirection,
                  tooltip: 'Switch Camera',
                  color: AppTheme.brandWhite,
                ),
                // Toggle flash
                IconButton(
                  icon: Icon(_isFlashOn ? Icons.flash_on : Icons.flash_off),
                  onPressed: _toggleFlash,
                  tooltip: 'Toggle Flash',
                  color: _isFlashOn ? AppTheme.warningYellow : AppTheme.brandWhite,
                ),
                // Start/Stop streaming
                FloatingActionButton(
                  onPressed: _toggleStreaming,
                  backgroundColor: _isStreaming ? AppTheme.errorRed : AppTheme.successGreen,
                  child: Icon(
                    _isStreaming ? Icons.stop : Icons.play_arrow,
                    color: AppTheme.brandWhite,
                  ),
                ),
                // Take snapshot
                IconButton(
                  icon: const Icon(Icons.camera_alt),
                  onPressed: _takeSnapshot,
                  tooltip: 'Take Snapshot',
                  color: AppTheme.brandWhite,
                ),
                // Create session
                IconButton(
                  icon: const Icon(Icons.share),
                  onPressed: _showCreateSessionDialog,
                  tooltip: 'Create Session',
                  color: AppTheme.actionBlue,
                ),
              ],
            ),
          ),
        ],
      ),
      drawer: Drawer(
        child: Container(
          color: AppTheme.brandBlack,
          child: ListView(
            padding: EdgeInsets.zero,
            children: [
              DrawerHeader(
                decoration: const BoxDecoration(
                  color: AppTheme.brandGray,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(
                      Icons.camera,
                      size: 48,
                      color: AppTheme.brandWhite,
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'CamCheck',
                      style: AppTheme.headingMedium,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'User: ${authService.username ?? "Unknown"}',
                      style: AppTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              ListTile(
                leading: const Icon(Icons.camera, color: AppTheme.brandWhite),
                title: const Text('Camera', style: AppTheme.bodyMedium),
                onTap: () {
                  Navigator.pop(context);
                },
              ),
              ListTile(
                leading: const Icon(Icons.history, color: AppTheme.brandWhite),
                title: const Text('Sessions', style: AppTheme.bodyMedium),
                onTap: () {
                  Navigator.pop(context);
                  // TODO: Navigate to sessions screen
                },
              ),
              ListTile(
                leading: const Icon(Icons.settings, color: AppTheme.brandWhite),
                title: const Text('Settings', style: AppTheme.bodyMedium),
                onTap: () {
                  Navigator.pop(context);
                  // TODO: Navigate to settings screen
                },
              ),
              const Divider(color: AppTheme.brandGray),
              ListTile(
                leading: const Icon(Icons.logout, color: AppTheme.errorRed),
                title: const Text('Logout', style: AppTheme.bodyMedium),
                onTap: () {
                  Navigator.pop(context);
                  _logout();
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
} 