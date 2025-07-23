import 'package:flutter/material.dart';

import '../config/theme.dart';

/// Button types
enum ButtonType {
  /// Primary button with filled background
  primary,
  
  /// Secondary button with outline
  secondary,
  
  /// Text button without background
  text,
  
  /// Danger button for destructive actions
  danger,
}

/// A reusable button widget that follows the design system
class AppButton extends StatelessWidget {
  /// Button text
  final String text;
  
  /// Button type
  final ButtonType type;
  
  /// Button icon
  final IconData? icon;
  
  /// Whether the button is loading
  final bool isLoading;
  
  /// Whether the button is disabled
  final bool isDisabled;
  
  /// Button width (null for auto)
  final double? width;
  
  /// Button height (null for default)
  final double? height;
  
  /// Button onPressed callback
  final VoidCallback? onPressed;
  
  /// Constructor
  const AppButton({
    Key? key,
    required this.text,
    this.type = ButtonType.primary,
    this.icon,
    this.isLoading = false,
    this.isDisabled = false,
    this.width,
    this.height,
    required this.onPressed,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    // Determine if button is disabled
    final bool disabled = isDisabled || isLoading;
    
    // Build the button based on type
    switch (type) {
      case ButtonType.primary:
        return _buildElevatedButton(context, disabled);
      case ButtonType.secondary:
        return _buildOutlinedButton(context, disabled);
      case ButtonType.text:
        return _buildTextButton(context, disabled);
      case ButtonType.danger:
        return _buildDangerButton(context, disabled);
    }
  }
  
  /// Build elevated button
  Widget _buildElevatedButton(BuildContext context, bool disabled) {
    return SizedBox(
      width: width,
      height: height ?? 48.0,
      child: ElevatedButton(
        onPressed: disabled ? null : onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: AppTheme.actionBlue,
          foregroundColor: AppTheme.brandWhite,
          disabledBackgroundColor: AppTheme.brandGray,
          disabledForegroundColor: AppTheme.lightGray,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(4.0),
          ),
          padding: const EdgeInsets.symmetric(horizontal: AppTheme.spacingS),
        ),
        child: _buildButtonContent(context),
      ),
    );
  }
  
  /// Build outlined button
  Widget _buildOutlinedButton(BuildContext context, bool disabled) {
    return SizedBox(
      width: width,
      height: height ?? 48.0,
      child: OutlinedButton(
        onPressed: disabled ? null : onPressed,
        style: OutlinedButton.styleFrom(
          foregroundColor: Theme.of(context).brightness == Brightness.dark
              ? AppTheme.brandWhite
              : AppTheme.brandBlack,
          side: BorderSide(
            color: disabled
                ? AppTheme.lightGray
                : Theme.of(context).brightness == Brightness.dark
                    ? AppTheme.brandWhite
                    : AppTheme.brandBlack,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(4.0),
          ),
          padding: const EdgeInsets.symmetric(horizontal: AppTheme.spacingS),
        ),
        child: _buildButtonContent(context),
      ),
    );
  }
  
  /// Build text button
  Widget _buildTextButton(BuildContext context, bool disabled) {
    return SizedBox(
      width: width,
      height: height,
      child: TextButton(
        onPressed: disabled ? null : onPressed,
        style: TextButton.styleFrom(
          foregroundColor: Theme.of(context).brightness == Brightness.dark
              ? AppTheme.brandWhite
              : AppTheme.brandBlack,
          disabledForegroundColor: AppTheme.lightGray,
          padding: const EdgeInsets.symmetric(horizontal: AppTheme.spacingXs),
        ),
        child: _buildButtonContent(context),
      ),
    );
  }
  
  /// Build danger button
  Widget _buildDangerButton(BuildContext context, bool disabled) {
    return SizedBox(
      width: width,
      height: height ?? 48.0,
      child: ElevatedButton(
        onPressed: disabled ? null : onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: AppTheme.errorRed.withOpacity(0.8),
          foregroundColor: AppTheme.brandWhite,
          disabledBackgroundColor: AppTheme.brandGray,
          disabledForegroundColor: AppTheme.lightGray,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(4.0),
          ),
          padding: const EdgeInsets.symmetric(horizontal: AppTheme.spacingS),
        ),
        child: _buildButtonContent(context),
      ),
    );
  }
  
  /// Build button content with icon and/or loading indicator
  Widget _buildButtonContent(BuildContext context) {
    if (isLoading) {
      return const SizedBox(
        width: 20,
        height: 20,
        child: CircularProgressIndicator(
          strokeWidth: 2,
          valueColor: AlwaysStoppedAnimation<Color>(AppTheme.brandWhite),
        ),
      );
    }
    
    if (icon != null) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 18),
          const SizedBox(width: AppTheme.spacingXs),
          Text(text),
        ],
      );
    }
    
    return Text(text);
  }
} 