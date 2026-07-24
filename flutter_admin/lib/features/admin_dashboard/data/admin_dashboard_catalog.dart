import 'package:flutter/material.dart';

import '../models/admin_dashboard_snapshot.dart';
import '../models/admin_tool_item.dart';
import '../models/quick_action_item.dart';

class AdminDashboardCatalog {
  static const quickActions = [
    QuickActionItem(
      id: 'admin_users',
      title: 'Quản lý người dùng',
      icon: Icons.people_outline_rounded,
      gradientColors: [Color(0xFF3B82F6), Color(0xFF60A5FA)],
    ),
    QuickActionItem(
      id: 'admin_approval',
      title: 'Duyệt khóa học',
      icon: Icons.verified_outlined,
      gradientColors: [Color(0xFF10B981), Color(0xFF34D399)],
    ),
    QuickActionItem(
      id: 'admin_transactions',
      title: 'Xem giao dịch',
      icon: Icons.credit_card_outlined,
      gradientColors: [Color(0xFF635BFF), Color(0xFF818CF8)],
    ),
    QuickActionItem(
      id: 'admin_health',
      title: 'Hệ thống',
      icon: Icons.settings_outlined,
      gradientColors: [Color(0xFF0EA5E9), Color(0xFF38BDF8)],
    ),
  ];

  static List<AdminToolItem> tools(AdminDashboardSnapshot data) => [
    AdminToolItem(
      id: 'admin_active_users',
      title: 'Người dùng đang hoạt động',
      description:
          'Theo dõi người dùng trực tuyến trong ${data.activeWindowMinutes} phút gần nhất',
      icon: Icons.people_alt_outlined,
      iconGradient: const [Color(0xFF3B82F6), Color(0xFF60A5FA)],
      badge: '${data.activeUsers} online',
    ),
    const AdminToolItem(
      id: 'admin_users',
      title: 'Quản lý người dùng',
      description: 'Phân quyền, khóa và quản lý tài khoản',
      icon: Icons.manage_accounts_outlined,
      iconGradient: [Color(0xFF635BFF), Color(0xFF818CF8)],
    ),
    const AdminToolItem(
      id: 'admin_courses',
      title: 'Quản lý khóa học',
      description: 'Quản lý nội dung, trạng thái và danh mục khóa học',
      icon: Icons.book_outlined,
      iconGradient: [Color(0xFF10B981), Color(0xFF34D399)],
    ),
    const AdminToolItem(
      id: 'admin_approval',
      title: 'Duyệt khóa học',
      description: 'Kiểm tra và phê duyệt nội dung từ giáo viên',
      icon: Icons.verified_outlined,
      iconGradient: [Color(0xFFF59E0B), Color(0xFFFBBF24)],
    ),
    AdminToolItem(
      id: 'admin_health',
      title: 'Sức khỏe hệ thống',
      description:
          'CPU ${data.cpuPercent}% • RAM ${data.ramPercent}% • Disk ${data.diskPercent}%',
      icon: Icons.monitor_heart_outlined,
      iconGradient: const [Color(0xFFEF4444), Color(0xFFF87171)],
      badge: '${data.cpuPercent}%',
    ),
    const AdminToolItem(
      id: 'admin_activity',
      title: 'Nhật ký hoạt động',
      description: 'Theo dõi lịch sử thao tác của quản trị viên',
      icon: Icons.history_rounded,
      iconGradient: [Color(0xFF8B5CF6), Color(0xFFA78BFA)],
    ),
    const AdminToolItem(
      id: 'admin_security',
      title: 'Bảo mật',
      description: 'Chính sách bảo mật và trạng thái chứng chỉ',
      icon: Icons.shield_outlined,
      iconGradient: [Color(0xFF0EA5E9), Color(0xFF38BDF8)],
    ),
    const AdminToolItem(
      id: 'admin_sessions',
      title: 'Phiên đăng nhập',
      description: 'Quản lý thiết bị và phiên đang kết nối',
      icon: Icons.devices_outlined,
      iconGradient: [Color(0xFFEC4899), Color(0xFFF472B6)],
    ),
    const AdminToolItem(
      id: 'admin_config',
      title: 'Cấu hình hệ thống',
      description: 'Thông số vận hành, email và cấu hình API',
      icon: Icons.tune_rounded,
      iconGradient: [Color(0xFF64748B), Color(0xFF94A3B8)],
    ),
    const AdminToolItem(
      id: 'admin_backups',
      title: 'Sao lưu hệ thống',
      description: 'Kiểm tra lịch sao lưu và khôi phục dữ liệu',
      icon: Icons.settings_backup_restore_rounded,
      iconGradient: [Color(0xFF0284C7), Color(0xFF38BDF8)],
    ),
    const AdminToolItem(
      id: 'admin_report_revenue',
      title: 'Báo cáo doanh thu',
      description: 'Thống kê doanh thu theo mốc thời gian',
      icon: Icons.bar_chart_rounded,
      iconGradient: [Color(0xFF059669), Color(0xFF34D399)],
    ),
    const AdminToolItem(
      id: 'admin_report_users',
      title: 'Báo cáo người dùng',
      description: 'Phân tích tăng trưởng và người dùng hoạt động',
      icon: Icons.analytics_outlined,
      iconGradient: [Color(0xFF4F46E5), Color(0xFF818CF8)],
    ),
    const AdminToolItem(
      id: 'admin_report_learning',
      title: 'Báo cáo học tập',
      description: 'Tiến độ, điểm số và thời gian học tập',
      icon: Icons.school_outlined,
      iconGradient: [Color(0xFFD97706), Color(0xFFFBBF24)],
    ),
    const AdminToolItem(
      id: 'admin_report_content',
      title: 'Báo cáo nội dung',
      description: 'Hiệu quả và mức độ tương tác với bài học',
      icon: Icons.dashboard_outlined,
      iconGradient: [Color(0xFF7C3AED), Color(0xFFA78BFA)],
    ),
    const AdminToolItem(
      id: 'admin_transactions',
      title: 'Giao dịch',
      description: 'Tra cứu thanh toán và trạng thái giao dịch',
      icon: Icons.credit_card_outlined,
      iconGradient: [Color(0xFF2563EB), Color(0xFF60A5FA)],
    ),
    const AdminToolItem(
      id: 'admin_notifications',
      title: 'Thông báo',
      description: 'Xem và quản lý thông báo hệ thống',
      icon: Icons.notifications_none_rounded,
      iconGradient: [Color(0xFFDC2626), Color(0xFFF87171)],
    ),
  ];
}
