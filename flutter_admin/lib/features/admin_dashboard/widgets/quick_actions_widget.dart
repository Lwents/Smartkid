import 'package:flutter/material.dart';
import '../../../app/theme.dart';
import '../models/quick_action_item.dart';
import 'glass_card.dart';

class QuickActionsWidget extends StatelessWidget {
  final List<QuickActionItem> items;
  final Function(QuickActionItem)? onItemTap;

  const QuickActionsWidget({super.key, required this.items, this.onItemTap});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Thao tác nhanh',
            style: TextStyle(
              color: AppColors.textPrimary,
              fontSize: 18,
              fontWeight: FontWeight.bold,
              letterSpacing: -0.3,
            ),
          ),
          const SizedBox(height: 12),
          LayoutBuilder(
            builder: (context, constraints) {
              final double width = constraints.maxWidth;
              final int columns = width > 500 ? 2 : 2;

              return GridView.builder(
                shrinkWrap: true,
                primary: false,
                padding: EdgeInsets.zero,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: items.length,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: columns,
                  crossAxisSpacing: 10,
                  mainAxisSpacing: 10,
                  childAspectRatio: width > 360 ? 2.6 : 2.2,
                ),
                itemBuilder: (context, index) {
                  final item = items[index];
                  return GlassCard(
                    blur: 0.0,
                    borderRadius: 20.0,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12.0,
                      vertical: 10.0,
                    ),
                    onTap: () => onItemTap?.call(item),
                    child: Row(
                      children: [
                        // Icon Container
                        Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                            color: item.gradientColors.first.withValues(
                              alpha: 0.12,
                            ),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Icon(
                            item.icon,
                            color: item.gradientColors.first,
                            size: 18,
                          ),
                        ),
                        const SizedBox(width: 10),

                        // Title Text
                        Expanded(
                          child: Text(
                            item.title,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: AppColors.textPrimary,
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),

                        // Chevron Right
                        const Icon(
                          Icons.chevron_right_rounded,
                          color: AppColors.textMuted,
                          size: 18,
                        ),
                      ],
                    ),
                  );
                },
              );
            },
          ),
        ],
      ),
    );
  }
}
