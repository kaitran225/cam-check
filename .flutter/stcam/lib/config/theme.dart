import 'package:flutter/material.dart';

/// App theme configuration based on the CamCheck design system
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
  
  // Text Styles
  static const TextStyle headingLarge = TextStyle(
    fontSize: 24,
    fontWeight: FontWeight.bold,
    letterSpacing: 0.5,
    fontFamily: 'Roboto',
  );
  
  static const TextStyle headingMedium = TextStyle(
    fontSize: 20,
    fontWeight: FontWeight.bold,
    letterSpacing: 0.5,
    fontFamily: 'Roboto',
  );
  
  static const TextStyle headingSmall = TextStyle(
    fontSize: 18,
    fontWeight: FontWeight.bold,
    letterSpacing: 0.5,
    fontFamily: 'Roboto',
  );
  
  static const TextStyle bodyLarge = TextStyle(
    fontSize: 16,
    fontWeight: FontWeight.normal,
    letterSpacing: 0.5,
    fontFamily: 'Roboto',
  );
  
  static const TextStyle bodyMedium = TextStyle(
    fontSize: 14,
    fontWeight: FontWeight.normal,
    letterSpacing: 0.5,
    fontFamily: 'Roboto',
  );
  
  static const TextStyle bodySmall = TextStyle(
    fontSize: 12,
    fontWeight: FontWeight.normal,
    letterSpacing: 0.5,
    fontFamily: 'Roboto',
  );
  
  static const TextStyle caption = TextStyle(
    fontSize: 11,
    fontWeight: FontWeight.normal,
    letterSpacing: 0.5,
    fontFamily: 'Roboto',
  );
  
  static const TextStyle buttonText = TextStyle(
    fontSize: 16,
    fontWeight: FontWeight.w500, // Medium
    letterSpacing: 0.5,
    fontFamily: 'Roboto',
  );
  
  // Dark Theme (Default)
  static final ThemeData darkTheme = ThemeData(
    brightness: Brightness.dark,
    primaryColor: brandBlack,
    scaffoldBackgroundColor: brandBlack,
    colorScheme: const ColorScheme.dark(
      primary: brandWhite,
      secondary: actionBlue,
      surface: brandGray,
      background: brandBlack,
      error: errorRed,
      onPrimary: brandBlack,
      onSecondary: brandWhite,
      onSurface: brandWhite,
      onBackground: brandWhite,
      onError: brandWhite,
    ),
    textTheme: const TextTheme(
      displayLarge: headingLarge,
      displayMedium: headingMedium,
      displaySmall: headingSmall,
      bodyLarge: bodyLarge,
      bodyMedium: bodyMedium,
      bodySmall: bodySmall,
      labelLarge: buttonText,
      labelMedium: bodyMedium,
      labelSmall: caption,
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
        textStyle: buttonText,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(4),
        ),
        minimumSize: const Size(double.infinity, 48),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: brandWhite,
        side: const BorderSide(color: brandWhite, width: 1),
        textStyle: buttonText,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(4),
        ),
        minimumSize: const Size(double.infinity, 48),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: brandWhite,
        textStyle: buttonText,
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      fillColor: brandGray,
      filled: true,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: brandWhite, width: 1),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: brandWhite, width: 1),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: actionBlue, width: 2),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(4),
        borderSide: const BorderSide(color: errorRed, width: 1),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      hintStyle: bodyMedium.copyWith(color: lightGray),
      labelStyle: bodyMedium.copyWith(color: brandWhite),
    ),
    cardTheme: CardTheme(
      color: brandGray,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(4),
      ),
      margin: const EdgeInsets.symmetric(vertical: 8),
    ),
    dividerTheme: const DividerThemeData(
      color: brandGray,
      thickness: 1,
      space: 1,
    ),
    iconTheme: const IconThemeData(
      color: brandWhite,
      size: 24,
    ),
    switchTheme: SwitchThemeData(
      thumbColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.selected)) {
          return actionBlue;
        }
        return lightGray;
      }),
      trackColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.selected)) {
          return actionBlue.withOpacity(0.5);
        }
        return lightGray.withOpacity(0.5);
      }),
    ),
    checkboxTheme: CheckboxThemeData(
      fillColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.selected)) {
          return actionBlue;
        }
        return Colors.transparent;
      }),
      checkColor: MaterialStateProperty.all(brandWhite),
      side: const BorderSide(color: brandWhite, width: 1.5),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(2),
      ),
    ),
    radioTheme: RadioThemeData(
      fillColor: MaterialStateProperty.resolveWith((states) {
        if (states.contains(MaterialState.selected)) {
          return actionBlue;
        }
        return brandWhite;
      }),
    ),
    bottomNavigationBarTheme: const BottomNavigationBarThemeData(
      backgroundColor: brandBlack,
      selectedItemColor: actionBlue,
      unselectedItemColor: lightGray,
      showSelectedLabels: true,
      showUnselectedLabels: true,
      type: BottomNavigationBarType.fixed,
      elevation: 8,
    ),
    snackBarTheme: const SnackBarThemeData(
      backgroundColor: brandGray,
      contentTextStyle: bodyMedium,
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(4)),
      ),
    ),
    dialogTheme: DialogTheme(
      backgroundColor: brandGray,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(4),
      ),
      titleTextStyle: headingMedium,
      contentTextStyle: bodyMedium,
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: brandGray,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.only(
          topLeft: Radius.circular(16),
          topRight: Radius.circular(16),
        ),
      ),
    ),
    tabBarTheme: const TabBarTheme(
      labelColor: actionBlue,
      unselectedLabelColor: lightGray,
      indicatorSize: TabBarIndicatorSize.tab,
      indicator: UnderlineTabIndicator(
        borderSide: BorderSide(color: actionBlue, width: 2),
      ),
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      color: actionBlue,
      circularTrackColor: brandGray,
      linearTrackColor: brandGray,
    ),
  );
  
  // Light Theme
  static final ThemeData lightTheme = ThemeData(
    brightness: Brightness.light,
    primaryColor: brandWhite,
    scaffoldBackgroundColor: lightBackground,
    colorScheme: const ColorScheme.light(
      primary: actionBlue,
      secondary: brandBlack,
      surface: brandWhite,
      background: lightBackground,
      error: errorRed,
      onPrimary: brandWhite,
      onSecondary: brandWhite,
      onSurface: lightText,
      onBackground: lightText,
      onError: brandWhite,
    ),
    // Rest of light theme configuration would go here
    // (Similar to dark theme but with light mode colors)
  );
}

