import 'package:flutter/material.dart';

/// Theme configuration for the CamCheck mobile app
class AppTheme {
  // Brand Colors
  static const Color brandBlack = Color(0xFF000000);
  static const Color brandWhite = Color(0xFFFFFFFF);
  static const Color brandGray = Color(0xFF333333);
  static const Color lightGray = Color(0xFF999999);

  // Accent Colors
  static const Color actionBlue = Color(0xFF0066CC);
  static const Color successGreen = Color(0xFF33CC66);
  static const Color warningYellow = Color(0xFFFFCC00);
  static const Color errorRed = Color(0xFFCC3300);

  // Light Theme Colors
  static const Color lightBackground = Color(0xFFF5F5F5);
  static const Color lightText = Color(0xFF212121);
  static const Color lightSurface = Color(0xFFFFFFFF);

  // Spacing
  static const double spacing2xs = 4.0;
  static const double spacingXs = 8.0;
  static const double spacingS = 16.0;
  static const double spacingM = 24.0;
  static const double spacingL = 32.0;
  static const double spacingXl = 48.0;
  static const double spacing2xl = 64.0;

  // Text Styles
  static TextStyle headingLarge = const TextStyle(
    fontSize: 24,
    fontWeight: FontWeight.bold,
    letterSpacing: 0.5,
  );

  static TextStyle headingMedium = const TextStyle(
    fontSize: 20,
    fontWeight: FontWeight.bold,
    letterSpacing: 0.5,
  );

  static TextStyle headingSmall = const TextStyle(
    fontSize: 18,
    fontWeight: FontWeight.bold,
    letterSpacing: 0.5,
  );

  static TextStyle bodyLarge = const TextStyle(
    fontSize: 16,
    fontWeight: FontWeight.normal,
  );

  static TextStyle bodyMedium = const TextStyle(
    fontSize: 14,
    fontWeight: FontWeight.normal,
  );

  static TextStyle bodySmall = const TextStyle(
    fontSize: 12,
    fontWeight: FontWeight.normal,
  );

  static TextStyle caption = const TextStyle(
    fontSize: 11,
    fontWeight: FontWeight.normal,
  );

  static TextStyle button = const TextStyle(
    fontSize: 16,
    fontWeight: FontWeight.w500,
    letterSpacing: 0.5,
  );

  // Dark Theme (Default)
  static ThemeData darkTheme = ThemeData(
    brightness: Brightness.dark,
    primaryColor: brandBlack,
    scaffoldBackgroundColor: brandBlack,
    colorScheme: const ColorScheme.dark(
      primary: brandWhite,
      secondary: actionBlue,
      surface: brandGray,
      background: brandBlack,
      error: errorRed,
    ),
    textTheme: TextTheme(
      displayLarge: headingLarge.copyWith(color: brandWhite),
      displayMedium: headingMedium.copyWith(color: brandWhite),
      displaySmall: headingSmall.copyWith(color: brandWhite),
      bodyLarge: bodyLarge.copyWith(color: brandWhite),
      bodyMedium: bodyMedium.copyWith(color: brandWhite),
      bodySmall: bodySmall.copyWith(color: brandWhite),
      labelLarge: button.copyWith(color: brandWhite),
      titleMedium: bodyMedium.copyWith(color: lightGray),
      titleSmall: caption.copyWith(color: lightGray),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: brandBlack,
      foregroundColor: brandWhite,
      elevation: 0,
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: actionBlue,
        foregroundColor: brandWhite,
        minimumSize: const Size.fromHeight(48),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(4),
        ),
        padding: const EdgeInsets.symmetric(horizontal: spacingS),
        textStyle: button,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: brandWhite,
        minimumSize: const Size.fromHeight(48),
        side: const BorderSide(color: brandWhite),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(4),
        ),
        padding: const EdgeInsets.symmetric(horizontal: spacingS),
        textStyle: button,
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: brandWhite,
        padding: const EdgeInsets.symmetric(horizontal: spacingXs),
        textStyle: button,
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: brandGray,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: brandWhite),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: brandWhite),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: actionBlue, width: 2),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: errorRed),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: spacingS, vertical: spacingXs),
    ),
    cardTheme: CardThemeData(
      color: brandGray,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(4),
      ),
      margin: const EdgeInsets.all(0),
    ),
    dividerTheme: const DividerThemeData(
      color: brandGray,
      thickness: 1,
    ),
    switchTheme: SwitchThemeData(
      thumbColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.disabled)) {
          return lightGray;
        }
        return states.contains(MaterialState.selected) ? actionBlue : brandWhite;
      }),
      trackColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.disabled)) {
          return lightGray.withOpacity(0.5);
        }
        return states.contains(MaterialState.selected) ? actionBlue.withOpacity(0.5) : brandGray;
      }),
    ),
    checkboxTheme: CheckboxThemeData(
      fillColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.disabled)) {
          return lightGray;
        }
        return states.contains(MaterialState.selected) ? actionBlue : null;
      }),
      side: const BorderSide(color: brandWhite),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(2),
      ),
    ),
    radioTheme: RadioThemeData(
      fillColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.disabled)) {
          return lightGray;
        }
        return states.contains(MaterialState.selected) ? actionBlue : brandWhite;
      }),
    ),
    bottomNavigationBarTheme: const BottomNavigationBarThemeData(
      backgroundColor: brandBlack,
      selectedItemColor: actionBlue,
      unselectedItemColor: lightGray,
    ),
  );

  // Light Theme
  static ThemeData lightTheme = ThemeData(
    brightness: Brightness.light,
    primaryColor: lightBackground,
    scaffoldBackgroundColor: lightBackground,
    colorScheme: const ColorScheme.light(
      primary: lightText,
      secondary: actionBlue,
      surface: lightSurface,
      background: lightBackground,
      error: errorRed,
    ),
    textTheme: TextTheme(
      displayLarge: headingLarge.copyWith(color: lightText),
      displayMedium: headingMedium.copyWith(color: lightText),
      displaySmall: headingSmall.copyWith(color: lightText),
      bodyLarge: bodyLarge.copyWith(color: lightText),
      bodyMedium: bodyMedium.copyWith(color: lightText),
      bodySmall: bodySmall.copyWith(color: lightText),
      labelLarge: button.copyWith(color: lightText),
      titleMedium: bodyMedium.copyWith(color: brandGray),
      titleSmall: caption.copyWith(color: brandGray),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: lightSurface,
      foregroundColor: lightText,
      elevation: 0,
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: actionBlue,
        foregroundColor: brandWhite,
        minimumSize: const Size.fromHeight(48),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(4),
        ),
        padding: const EdgeInsets.symmetric(horizontal: spacingS),
        textStyle: button,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: lightText,
        minimumSize: const Size.fromHeight(48),
        side: const BorderSide(color: lightText),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(4),
        ),
        padding: const EdgeInsets.symmetric(horizontal: spacingS),
        textStyle: button,
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: lightText,
        padding: const EdgeInsets.symmetric(horizontal: spacingXs),
        textStyle: button,
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: lightSurface,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: brandGray),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: brandGray),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: actionBlue, width: 2),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: errorRed),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: spacingS, vertical: spacingXs),
    ),
    cardTheme: CardThemeData(
      color: lightSurface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(4),
      ),
      margin: const EdgeInsets.all(0),
    ),
    dividerTheme: const DividerThemeData(
      color: lightGray,
      thickness: 1,
    ),
    switchTheme: SwitchThemeData(
      thumbColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.disabled)) {
          return brandGray;
        }
        return states.contains(MaterialState.selected) ? actionBlue : lightSurface;
      }),
      trackColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.disabled)) {
          return brandGray.withOpacity(0.5);
        }
        return states.contains(MaterialState.selected) ? actionBlue.withOpacity(0.5) : lightGray;
      }),
    ),
    checkboxTheme: CheckboxThemeData(
      fillColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.disabled)) {
          return brandGray;
        }
        return states.contains(MaterialState.selected) ? actionBlue : null;
      }),
      side: const BorderSide(color: brandGray),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(2),
      ),
    ),
    radioTheme: RadioThemeData(
      fillColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.disabled)) {
          return brandGray;
        }
        return states.contains(MaterialState.selected) ? actionBlue : lightText;
      }),
    ),
    bottomNavigationBarTheme: const BottomNavigationBarThemeData(
      backgroundColor: lightSurface,
      selectedItemColor: actionBlue,
      unselectedItemColor: brandGray,
    ),
  );
} 