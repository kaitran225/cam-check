import 'package:flutter/material.dart';

import '../config/theme.dart';
import 'app_button.dart';

/// A widget that displays camera stream controls
class StreamControls extends StatelessWidget {
  /// Whether audio is enabled
  final bool audioEnabled;
  
  /// Whether video is enabled
  final bool videoEnabled;
  
  /// Whether the session is active
  final bool sessionActive;
  
  /// Callback when audio toggle is pressed
  final VoidCallback? onAudioToggle;
  
  /// Callback when video toggle is pressed
  final VoidCallback? onVideoToggle;
  
  /// Callback when snapshot button is pressed
  final VoidCallback? onSnapshot;
  
  /// Callback when flip camera button is pressed
  final VoidCallback? onFlipCamera;
  
  /// Callback when end session button is pressed
  final VoidCallback? onEndSession;
  
  /// Constructor
  const StreamControls({
    Key? key,
    this.audioEnabled = true,
    this.videoEnabled = true,
    this.sessionActive = false,
    this.onAudioToggle,
    this.onVideoToggle,
    this.onSnapshot,
    this.onFlipCamera,
    this.onEndSession,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        vertical: AppTheme.spacingXs,
        horizontal: AppTheme.spacingS,
      ),
      decoration: BoxDecoration(
        color: AppTheme.brandBlack.withOpacity(0.8),
        borderRadius: BorderRadius.circular(4.0),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          // Audio toggle
          _buildControlButton(
            context,
            icon: audioEnabled ? Icons.mic : Icons.mic_off,
            label: audioEnabled ? 'Mute' : 'Unmute',
            onPressed: onAudioToggle,
            color: audioEnabled ? AppTheme.brandWhite : AppTheme.lightGray,
          ),
          
          // Video toggle
          _buildControlButton(
            context,
            icon: videoEnabled ? Icons.videocam : Icons.videocam_off,
            label: videoEnabled ? 'Hide' : 'Show',
            onPressed: onVideoToggle,
            color: videoEnabled ? AppTheme.brandWhite : AppTheme.lightGray,
          ),
          
          // Snapshot button
          _buildControlButton(
            context,
            icon: Icons.camera_alt,
            label: 'Snapshot',
            onPressed: videoEnabled ? onSnapshot : null,
          ),
          
          // Flip camera button
          _buildControlButton(
            context,
            icon: Icons.flip_camera_ios,
            label: 'Flip',
            onPressed: videoEnabled ? onFlipCamera : null,
          ),
          
          // End session button
          if (sessionActive)
            _buildControlButton(
              context,
              icon: Icons.call_end,
              label: 'End',
              onPressed: onEndSession,
              color: AppTheme.errorRed,
              backgroundColor: AppTheme.errorRed.withOpacity(0.2),
            ),
        ],
      ),
    );
  }
  
  /// Build a control button
  Widget _buildControlButton(
    BuildContext context, {
    required IconData icon,
    required String label,
    VoidCallback? onPressed,
    Color? color,
    Color? backgroundColor,
  }) {
    final isDisabled = onPressed == null;
    
    return InkWell(
      onTap: onPressed,
      borderRadius: BorderRadius.circular(8.0),
      child: Padding(
        padding: const EdgeInsets.symmetric(
          vertical: AppTheme.spacingXs,
          horizontal: AppTheme.spacingS,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: backgroundColor ?? AppTheme.brandGray.withOpacity(0.5),
                shape: BoxShape.circle,
              ),
              child: Icon(
                icon,
                color: isDisabled
                    ? AppTheme.lightGray
                    : (color ?? AppTheme.brandWhite),
                size: 20,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: AppTheme.caption.copyWith(
                color: isDisabled
                    ? AppTheme.lightGray
                    : AppTheme.brandWhite,
              ),
            ),
          ],
        ),
      ),
    );
  }
} 