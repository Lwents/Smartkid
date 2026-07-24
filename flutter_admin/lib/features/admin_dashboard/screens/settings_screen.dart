import 'package:flutter/material.dart';

import '../../../app/theme.dart';
import '../widgets/glass_card.dart';

class SettingsScreen extends StatelessWidget {
  final String adminName;
  final String adminEmail;
  final Future<void> Function(String key) onOpenFeature;
  final Future<void> Function() onLogout;

  const SettingsScreen({
    super.key,
    required this.adminName,
    required this.adminEmail,
    required this.onOpenFeature,
    required this.onLogout,
  });

  Future<void> _confirmLogout(BuildContext context) async {
    final shouldLogout = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: const Color(0xFFF8FAFD),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        icon: const Icon(
          Icons.logout_rounded,
          color: AppColors.accentRed,
          size: 30,
        ),
        title: const Text('Đăng xuất khỏi SmartKid?'),
        content: const Text(
          'Phiên quản trị hiện tại sẽ kết thúc. Bạn cần đăng nhập lại để tiếp tục.',
          textAlign: TextAlign.center,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            style: FilledButton.styleFrom(backgroundColor: AppColors.accentRed),
            child: const Text('Đăng xuất'),
          ),
        ],
      ),
    );
    if (shouldLogout == true) await onLogout();
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: ListView(
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 112),
        children: [
          const Text(
            'Cài đặt hệ thống',
            style: TextStyle(
              color: AppColors.textPrimary,
              fontSize: 22,
              fontWeight: FontWeight.bold,
              letterSpacing: -0.4,
            ),
          ),
          const SizedBox(height: 18),
          GlassCard(
            borderRadius: 24,
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                _AdminAvatar(name: adminName),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        adminName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        adminEmail,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AppColors.textSecondary,
                          fontSize: 12.5,
                        ),
                      ),
                    ],
                  ),
                ),
                const Icon(
                  Icons.verified_rounded,
                  color: AppColors.primaryPurple,
                  size: 22,
                ),
              ],
            ),
          ),
          const SizedBox(height: 22),
          const Text(
            'Quản trị và bảo mật',
            style: TextStyle(
              color: AppColors.textPrimary,
              fontSize: 17,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 10),
          GlassCard(
            borderRadius: 24,
            padding: EdgeInsets.zero,
            child: Column(
              children: [
                _SettingsRow(
                  icon: Icons.shield_outlined,
                  title: 'Bảo mật',
                  subtitle: 'Chính sách và trạng thái bảo mật',
                  onTap: () => onOpenFeature('admin_security'),
                ),
                const Divider(height: 1, indent: 62, endIndent: 16),
                _SettingsRow(
                  icon: Icons.monitor_heart_outlined,
                  title: 'Sức khỏe hệ thống',
                  subtitle: 'CPU, RAM, ổ đĩa và sao lưu',
                  onTap: () => onOpenFeature('admin_health'),
                ),
                const Divider(height: 1, indent: 62, endIndent: 16),
                _SettingsRow(
                  icon: Icons.tune_rounded,
                  title: 'Cấu hình hệ thống',
                  subtitle: 'Thiết lập vận hành SmartKid',
                  onTap: () => onOpenFeature('admin_config'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 22),
          GlassCard(
            borderRadius: 24,
            padding: const EdgeInsets.all(16),
            backgroundColor: const Color(0xFFFFF5F5),
            borderColor: const Color(0xFFFFD5D5),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(
                      Icons.logout_rounded,
                      color: AppColors.accentRed,
                      size: 22,
                    ),
                    SizedBox(width: 9),
                    Expanded(
                      child: Text(
                        'Kết thúc phiên làm việc',
                        style: TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                const Text(
                  'Đăng xuất an toàn khỏi tài khoản quản trị trên thiết bị này.',
                  style: TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 12.5,
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 14),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: FilledButton.icon(
                    onPressed: () => _confirmLogout(context),
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.accentRed,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                    icon: const Icon(Icons.logout_rounded, size: 19),
                    label: const Text(
                      'Đăng xuất',
                      style: TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminAvatar extends StatelessWidget {
  final String name;

  const _AdminAvatar({required this.name});

  @override
  Widget build(BuildContext context) {
    final parts = name
        .trim()
        .split(RegExp(r'\s+'))
        .where((part) => part.isNotEmpty)
        .toList();
    final initials = parts.isEmpty
        ? 'AD'
        : parts.length == 1
        ? parts.first[0].toUpperCase()
        : '${parts.first[0]}${parts.last[0]}'.toUpperCase();
    return Container(
      width: 50,
      height: 50,
      decoration: const BoxDecoration(
        shape: BoxShape.circle,
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFFC7D2FE), Color(0xFF818CF8)],
        ),
      ),
      alignment: Alignment.center,
      child: Text(
        initials,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 16,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

class _SettingsRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  const _SettingsRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: AppColors.primaryPurpleLight,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, color: AppColors.primaryPurple, size: 19),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      color: AppColors.textPrimary,
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 11.5,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(
              Icons.chevron_right_rounded,
              color: AppColors.textMuted,
              size: 20,
            ),
          ],
        ),
      ),
    );
  }
}
