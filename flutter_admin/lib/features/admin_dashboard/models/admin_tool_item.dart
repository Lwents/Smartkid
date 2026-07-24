import 'package:flutter/material.dart';

class AdminToolItem {
  final String id;
  final String title;
  final String description;
  final IconData icon;
  final List<Color> iconGradient;
  final String? badge;

  const AdminToolItem({
    required this.id,
    required this.title,
    required this.description,
    required this.icon,
    required this.iconGradient,
    this.badge,
  });
}
