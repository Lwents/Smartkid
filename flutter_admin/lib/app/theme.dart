import 'package:flutter/material.dart';

class AppColors {
  // Background Gradient Colors
  static const Color bgGradientStart = Color(0xFFF7F9FC);
  static const Color bgGradientEnd = Color(0xFFF1F5F9);

  // Main Text Colors
  static const Color textPrimary = Color(0xFF1E293B);
  static const Color textSecondary = Color(0xFF64748B);
  static const Color textMuted = Color(0xFF94A3B8);

  // Accent Colors
  static const Color primaryPurple = Color(0xFF635BFF);
  static const Color primaryPurpleLight = Color(0xFFEDE9FE);
  static const Color accentBlue = Color(0xFF3B82F6);
  static const Color accentIceBlue = Color(0xFF38BDF8);
  static const Color accentGreen = Color(0xFF10B981);
  static const Color accentOrange = Color(0xFFF59E0B);
  static const Color accentRed = Color(0xFFEF4444);

  // Glass Colors
  static Color glassBackground = Colors.white.withValues(alpha: 0.78);
  static Color glassBackgroundHeader = Colors.white.withValues(alpha: 0.84);
  static Color glassBorder = Colors.white.withValues(alpha: 0.9);
  static Color glassShadow = const Color(0x121E293B);
}

class AppTheme {
  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      scaffoldBackgroundColor: AppColors.bgGradientStart,
      colorScheme: ColorScheme.fromSeed(
        seedColor: AppColors.primaryPurple,
        primary: AppColors.primaryPurple,
        secondary: AppColors.accentBlue,
        surface: AppColors.bgGradientStart,
      ),
      fontFamily: 'Roboto',
      textTheme: const TextTheme(
        headlineMedium: TextStyle(
          color: AppColors.textPrimary,
          fontSize: 22,
          fontWeight: FontWeight.bold,
          letterSpacing: -0.5,
        ),
        titleLarge: TextStyle(
          color: AppColors.textPrimary,
          fontSize: 18,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.3,
        ),
        titleMedium: TextStyle(
          color: AppColors.textPrimary,
          fontSize: 16,
          fontWeight: FontWeight.w600,
        ),
        bodyLarge: TextStyle(
          color: AppColors.textPrimary,
          fontSize: 14,
          fontWeight: FontWeight.w500,
        ),
        bodyMedium: TextStyle(
          color: AppColors.textSecondary,
          fontSize: 13,
          fontWeight: FontWeight.normal,
        ),
        bodySmall: TextStyle(color: AppColors.textMuted, fontSize: 12),
      ),
    );
  }
}
