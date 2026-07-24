import 'package:flutter/material.dart';

import '../../../app/theme.dart';
import '../widgets/glass_card.dart';

class AdminSectionAction {
  final String featureKey;
  final String title;
  final String description;
  final IconData icon;
  final Color color;

  const AdminSectionAction({
    required this.featureKey,
    required this.title,
    required this.description,
    required this.icon,
    required this.color,
  });
}

class AdminSectionScreen extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Color accentColor;
  final List<AdminSectionAction> actions;
  final Future<void> Function(String key) onOpenFeature;

  const AdminSectionScreen({
    super.key,
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.accentColor,
    required this.actions,
    required this.onOpenFeature,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 14),
            sliver: SliverToBoxAdapter(
              child: Row(
                children: [
                  Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: accentColor.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(17),
                      border: Border.all(color: Colors.white),
                    ),
                    child: Icon(icon, color: accentColor, size: 24),
                  ),
                  const SizedBox(width: 13),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          style: const TextStyle(
                            color: AppColors.textPrimary,
                            fontSize: 22,
                            fontWeight: FontWeight.w800,
                            letterSpacing: -0.4,
                          ),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          subtitle,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: AppColors.textSecondary,
                            fontSize: 12.5,
                            height: 1.3,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 110),
            sliver: SliverList.separated(
              itemCount: actions.length,
              separatorBuilder: (context, index) => const SizedBox(height: 10),
              itemBuilder: (context, index) {
                final action = actions[index];
                return GlassCard(
                  borderRadius: 22,
                  padding: const EdgeInsets.all(14),
                  onTap: () => onOpenFeature(action.featureKey),
                  child: Row(
                    children: [
                      Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          color: action.color.withValues(alpha: 0.11),
                          borderRadius: BorderRadius.circular(15),
                        ),
                        child: Icon(action.icon, color: action.color, size: 22),
                      ),
                      const SizedBox(width: 13),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              action.title,
                              style: const TextStyle(
                                color: AppColors.textPrimary,
                                fontSize: 14.5,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 3),
                            Text(
                              action.description,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: AppColors.textSecondary,
                                fontSize: 11.5,
                                height: 1.3,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      const Icon(
                        Icons.chevron_right_rounded,
                        color: AppColors.textMuted,
                        size: 21,
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
