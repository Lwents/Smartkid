import 'package:flutter/material.dart';

class KpiItem {
  final String id;
  final String title;
  final String value;
  final String subtext;
  final String trend;
  final bool isPositiveTrend;
  final IconData icon;
  final List<Color> gradientColors;

  const KpiItem({
    required this.id,
    required this.title,
    required this.value,
    required this.subtext,
    required this.trend,
    required this.isPositiveTrend,
    required this.icon,
    required this.gradientColors,
  });

  IconData get trendIcon {
    if (trend == '0%' || trend == '0' || trend == '—') {
      return Icons.remove_rounded;
    }
    return isPositiveTrend
        ? Icons.trending_up_rounded
        : Icons.trending_down_rounded;
  }

  Color get trendColor {
    if (trend == '0%' || trend == '0' || trend == '—') {
      return const Color(0xFF94A3B8); // AppColors.textMuted
    }
    return isPositiveTrend
        ? const Color(0xFF10B981) // AppColors.accentGreen
        : const Color(0xFFEF4444); // AppColors.accentRed
  }
}
