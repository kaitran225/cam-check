import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../config/theme.dart';

/// A widget that displays a camera feed
class CameraPreviewWidget extends StatelessWidget {
  /// The video stream to display
  final MediaStream? stream;
  
  /// Whether this is a local stream
  final bool isLocal;
  
  /// Whether the camera is active
  final bool isActive;
  
  /// Label to display on the camera
  final String? label;
  
  /// Whether to mirror the camera feed (for front camera)
  final bool mirror;
  
  /// Fit mode for the video
  final BoxFit fit;
  
  /// Callback when the camera is tapped
  final VoidCallback? onTap;
  
  /// Constructor
  const CameraPreviewWidget({
    Key? key,
    required this.stream,
    this.isLocal = true,
    this.isActive = true,
    this.label,
    this.mirror = true,
    this.fit = BoxFit.cover,
    this.onTap,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: AppTheme.brandBlack,
          borderRadius: BorderRadius.circular(4.0),
          border: Border.all(
            color: isLocal ? AppTheme.actionBlue : AppTheme.brandGray,
            width: isLocal ? 2.0 : 1.0,
          ),
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          fit: StackFit.expand,
          children: [
            // Camera feed
            if (stream != null && isActive)
              Transform(
                alignment: Alignment.center,
                transform: Matrix4.identity()..scale(mirror ? -1.0 : 1.0, 1.0, 1.0),
                child: RTCVideoView(
                  RTCVideoRenderer()..srcObject = stream,
                  objectFit: fit == BoxFit.cover 
                      ? RTCVideoViewObjectFit.RTCVideoViewObjectFitCover
                      : RTCVideoViewObjectFit.RTCVideoViewObjectFitContain,
                  mirror: false, // We handle mirroring with the Transform
                ),
              )
            else
              // Placeholder when no stream
              Container(
                color: AppTheme.brandBlack,
                child: Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        isActive ? Icons.videocam_off : Icons.videocam,
                        color: AppTheme.brandWhite.withOpacity(0.5),
                        size: 48,
                      ),
                      const SizedBox(height: AppTheme.spacingXs),
                      Text(
                        isActive ? 'No camera feed' : 'Camera inactive',
                        style: AppTheme.bodyMedium.copyWith(
                          color: AppTheme.brandWhite.withOpacity(0.5),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              
            // Label
            if (label != null)
              Positioned(
                bottom: AppTheme.spacingXs,
                left: AppTheme.spacingXs,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppTheme.spacingXs,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: AppTheme.brandBlack.withOpacity(0.7),
                    borderRadius: BorderRadius.circular(2),
                  ),
                  child: Text(
                    label!,
                    style: AppTheme.bodySmall.copyWith(
                      color: AppTheme.brandWhite,
                    ),
                  ),
                ),
              ),
              
            // Loading indicator when stream is initializing
            if (stream != null && !isActive)
              const Center(
                child: CircularProgressIndicator(
                  valueColor: AlwaysStoppedAnimation<Color>(AppTheme.actionBlue),
                ),
              ),
          ],
        ),
      ),
    );
  }
} 