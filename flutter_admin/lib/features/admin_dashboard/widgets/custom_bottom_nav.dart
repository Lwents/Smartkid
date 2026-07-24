import 'package:flutter/material.dart';

import '../../../app/theme.dart';

class CustomBottomNav extends StatelessWidget {
  final int selectedIndex;
  final ValueChanged<int> onItemSelected;

  const CustomBottomNav({
    super.key,
    required this.selectedIndex,
    required this.onItemSelected,
  });

  static const List<({String label, IconData icon})> navItems = [
    (label: 'Tổng quan', icon: Icons.home_rounded),
    (label: 'Người dùng', icon: Icons.people_rounded),
    (label: 'Nội dung', icon: Icons.menu_book_rounded),
    (label: 'Cài đặt', icon: Icons.settings_rounded),
  ];

  @override
  Widget build(BuildContext context) {
    final safeIndex = selectedIndex.clamp(0, navItems.length - 1);
    return RepaintBoundary(
      child: Container(
        height: 66,
        margin: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        padding: const EdgeInsets.all(4),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xEFFFFFFF), Color(0xE0F9FBFD), Color(0xD8F2F5F9)],
            stops: [0, 0.55, 1],
          ),
          borderRadius: BorderRadius.circular(32),
          border: Border.all(color: const Color(0xE8FFFFFF), width: 1.2),
          boxShadow: const [
            BoxShadow(
              color: Color(0x1A1E293B),
              blurRadius: 16,
              offset: Offset(0, 7),
            ),
          ],
        ),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final itemWidth = constraints.maxWidth / navItems.length;
            return Stack(
              children: [
                AnimatedPositioned(
                  duration: const Duration(milliseconds: 280),
                  curve: Curves.easeOutCubic,
                  left: itemWidth * safeIndex,
                  top: 0,
                  bottom: 0,
                  width: itemWidth,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 2),
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: [Color(0xF2F0F0FF), Color(0xE8EEF5FF)],
                        ),
                        borderRadius: BorderRadius.circular(24),
                        border: Border.all(
                          color: const Color(0xE8FFFFFF),
                          width: 1,
                        ),
                        boxShadow: const [
                          BoxShadow(
                            color: Color(0x17635BFF),
                            blurRadius: 8,
                            offset: Offset(0, 3),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                Row(
                  children: List.generate(navItems.length, (index) {
                    final item = navItems[index];
                    final selected = safeIndex == index;
                    return Expanded(
                      child: Semantics(
                        button: true,
                        selected: selected,
                        label: item.label,
                        child: InkWell(
                          onTap: () => onItemSelected(index),
                          borderRadius: BorderRadius.circular(24),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              AnimatedScale(
                                duration: const Duration(milliseconds: 220),
                                curve: Curves.easeOutBack,
                                scale: selected ? 1.08 : 1,
                                child: Icon(
                                  item.icon,
                                  color: selected
                                      ? AppColors.primaryPurple
                                      : AppColors.textSecondary,
                                  size: 20,
                                ),
                              ),
                              const SizedBox(height: 2),
                              FittedBox(
                                fit: BoxFit.scaleDown,
                                child: AnimatedDefaultTextStyle(
                                  duration: const Duration(milliseconds: 200),
                                  style: TextStyle(
                                    color: selected
                                        ? AppColors.primaryPurple
                                        : AppColors.textSecondary,
                                    fontSize: 10.5,
                                    fontWeight: selected
                                        ? FontWeight.w700
                                        : FontWeight.w500,
                                  ),
                                  child: Text(item.label, maxLines: 1),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    );
                  }),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
