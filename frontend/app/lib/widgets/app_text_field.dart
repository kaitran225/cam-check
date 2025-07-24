import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../config/theme.dart';

/// A reusable text field widget that follows the design system
class AppTextField extends StatelessWidget {
  /// Controller for the text field
  final TextEditingController? controller;
  
  /// Label text
  final String label;
  
  /// Hint text
  final String? hint;
  
  /// Error text
  final String? errorText;
  
  /// Helper text
  final String? helperText;
  
  /// Icon to display on the left
  final IconData? prefixIcon;
  
  /// Icon to display on the right
  final IconData? suffixIcon;
  
  /// Whether the text field is obscured (for passwords)
  final bool obscureText;
  
  /// Whether the text field is enabled
  final bool enabled;
  
  /// Whether the text field is required
  final bool required;
  
  /// Whether the text field is read-only
  final bool readOnly;
  
  /// Input type
  final TextInputType keyboardType;
  
  /// Text input action
  final TextInputAction textInputAction;
  
  /// Input formatters
  final List<TextInputFormatter>? inputFormatters;
  
  /// Maximum number of lines
  final int? maxLines;
  
  /// Minimum number of lines
  final int? minLines;
  
  /// Maximum length
  final int? maxLength;
  
  /// Focus node
  final FocusNode? focusNode;
  
  /// Callback when text changes
  final Function(String)? onChanged;
  
  /// Callback when text field is submitted
  final Function(String)? onSubmitted;
  
  /// Callback when suffix icon is pressed
  final VoidCallback? onSuffixIconPressed;
  
  /// Constructor
  const AppTextField({
    Key? key,
    this.controller,
    required this.label,
    this.hint,
    this.errorText,
    this.helperText,
    this.prefixIcon,
    this.suffixIcon,
    this.obscureText = false,
    this.enabled = true,
    this.required = false,
    this.readOnly = false,
    this.keyboardType = TextInputType.text,
    this.textInputAction = TextInputAction.next,
    this.inputFormatters,
    this.maxLines = 1,
    this.minLines,
    this.maxLength,
    this.focusNode,
    this.onChanged,
    this.onSubmitted,
    this.onSuffixIconPressed,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;
    
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        // Label
        if (label.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: AppTheme.spacingXs),
            child: Row(
              children: [
                Text(
                  label,
                  style: AppTheme.bodyMedium.copyWith(
                    color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandBlack,
                  ),
                ),
                if (required)
                  Text(
                    ' *',
                    style: AppTheme.bodyMedium.copyWith(
                      color: AppTheme.errorRed,
                    ),
                  ),
              ],
            ),
          ),
          
        // Text field
        TextField(
          controller: controller,
          focusNode: focusNode,
          obscureText: obscureText,
          enabled: enabled,
          readOnly: readOnly,
          keyboardType: keyboardType,
          textInputAction: textInputAction,
          maxLines: maxLines,
          minLines: minLines,
          maxLength: maxLength,
          inputFormatters: inputFormatters,
          onChanged: onChanged,
          onSubmitted: onSubmitted,
          style: AppTheme.bodyMedium.copyWith(
            color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandBlack,
          ),
          decoration: InputDecoration(
            hintText: hint,
            hintStyle: AppTheme.bodyMedium.copyWith(
              color: AppTheme.lightGray,
            ),
            errorText: errorText,
            errorStyle: AppTheme.bodySmall.copyWith(
              color: AppTheme.errorRed,
            ),
            helperText: helperText,
            helperStyle: AppTheme.bodySmall.copyWith(
              color: isDarkMode ? AppTheme.lightGray : AppTheme.brandGray,
            ),
            filled: true,
            fillColor: isDarkMode ? AppTheme.brandGray : AppTheme.brandWhite,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: AppTheme.spacingS,
              vertical: AppTheme.spacingXs,
            ),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(4.0),
              borderSide: BorderSide(
                color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandGray,
              ),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(4.0),
              borderSide: BorderSide(
                color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandGray,
              ),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(4.0),
              borderSide: const BorderSide(
                color: AppTheme.actionBlue,
                width: 2.0,
              ),
            ),
            errorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(4.0),
              borderSide: const BorderSide(
                color: AppTheme.errorRed,
              ),
            ),
            focusedErrorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(4.0),
              borderSide: const BorderSide(
                color: AppTheme.errorRed,
                width: 2.0,
              ),
            ),
            prefixIcon: prefixIcon != null ? Icon(prefixIcon) : null,
            suffixIcon: suffixIcon != null
                ? IconButton(
                    icon: Icon(suffixIcon),
                    onPressed: onSuffixIconPressed,
                  )
                : null,
          ),
        ),
      ],
    );
  }
} 