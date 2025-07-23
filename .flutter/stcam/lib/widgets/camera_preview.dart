import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import '../config/theme.dart';

/// Custom camera preview widget with additional features
class CustomCameraPreview extends StatelessWidget {
  final CameraController controller;
  final bool showOverlay;
  final bool showFocusCircle;
  final Offset? focusPoint;
  
  const CustomCameraPreview(
    this.controller, {
    super.key,
    this.showOverlay = true,
    this.showFocusCircle = false,
    this.focusPoint,
  });

  @override
  Widget build(BuildContext context) {
    if (!controller.value.isInitialized) {
      return Container(
        color: AppTheme.brandBlack,
        child: const Center(
          child: CircularProgressIndicator(),
        ),
      );
    }
    
    return Stack(
      fit: StackFit.expand,
      children: [
        // Camera preview
        AspectRatio(
          aspectRatio: controller.value.aspectRatio,
          child: CameraPreview(controller),
        ),
        
        // Optional overlay
        if (showOverlay)
          Container(
            decoration: BoxDecoration(
              border: Border.all(
                color: AppTheme.actionBlue.withOpacity(0.5),
                width: 2,
              ),
            ),
          ),
        
        // Focus circle
        if (showFocusCircle && focusPoint != null)
          Positioned(
            left: focusPoint!.dx - 16,
            top: focusPoint!.dy - 16,
            child: Container(
              height: 32,
              width: 32,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: AppTheme.actionBlue,
                  width: 2,
                ),
              ),
            ),
          ),
        
        // Camera status indicators
        Positioned(
          top: 8,
          right: 8,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: AppTheme.brandBlack.withOpacity(0.6),
              borderRadius: BorderRadius.circular(4),
            ),
            child: Row(
              children: [
                // Resolution indicator
                Text(
                  '${controller.value.previewSize?.width.toInt() ?? 0}x'
                  '${controller.value.previewSize?.height.toInt() ?? 0}',
                  style: AppTheme.caption,
                ),
                
                const SizedBox(width: 8),
                
                // Flash indicator
                if (controller.value.flashMode == FlashMode.torch)
                  const Icon(
                    Icons.flash_on,
                    color: AppTheme.warningYellow,
                    size: 14,
                  ),
                
                // Exposure indicator
                if (controller.value.exposureMode == ExposureMode.auto)
                  const Icon(
                    Icons.exposure,
                    color: AppTheme.brandWhite,
                    size: 14,
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
} 