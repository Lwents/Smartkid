import 'package:flutter/material.dart';
import '../../../app/theme.dart';
import '../models/admin_tool_item.dart';
import 'glass_card.dart';

class AdminToolsWidget extends StatelessWidget {
  final List<AdminToolItem> tools;
  final Function(AdminToolItem)? onToolTap;

  const AdminToolsWidget({super.key, required this.tools, this.onToolTap});

  @override
  Widget build(BuildContext context) {
    return SliverMainAxisGroup(
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          sliver: SliverToBoxAdapter(child: _buildHeader()),
        ),
        SliverPadding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          sliver: SliverList(
            delegate: SliverChildBuilderDelegate((context, index) {
              if (index.isOdd) return const SizedBox(height: 8);
              final tool = tools[index ~/ 2];
              return _AdminToolCard(
                tool: tool,
                onTap: () => onToolTap?.call(tool),
              );
            }, childCount: tools.isEmpty ? 0 : tools.length * 2 - 1),
          ),
        ),
      ],
    );
  }

  Widget _buildHeader() {
    return Row(
      children: [
        const Expanded(
          child: Text(
            'Công cụ quản trị',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: AppColors.textPrimary,
              fontSize: 18,
              fontWeight: FontWeight.bold,
              letterSpacing: -0.3,
            ),
          ),
        ),
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
          decoration: BoxDecoration(
            color: AppColors.primaryPurpleLight,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(
            '${tools.length} mục',
            style: const TextStyle(
              color: AppColors.primaryPurple,
              fontSize: 11,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
      ],
    );
  }
}

class _AdminToolCard extends StatelessWidget {
  final AdminToolItem tool;
  final VoidCallback onTap;

  const _AdminToolCard({required this.tool, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      borderRadius: 20,
      padding: const EdgeInsets.all(12),
      onTap: onTap,
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: tool.iconGradient,
              ),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(tool.icon, color: Colors.white, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        tool.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    if (tool.badge != null) ...[
                      const SizedBox(width: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: tool.iconGradient.first.withValues(
                            alpha: 0.12,
                          ),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          tool.badge!,
                          style: TextStyle(
                            color: tool.iconGradient.first,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  tool.description,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 12,
                    fontWeight: FontWeight.w400,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          const Icon(
            Icons.chevron_right_rounded,
            color: AppColors.textMuted,
            size: 20,
          ),
        ],
      ),
    );
  }
}
