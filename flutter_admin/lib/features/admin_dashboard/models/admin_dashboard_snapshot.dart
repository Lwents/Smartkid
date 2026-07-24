import 'package:flutter/material.dart';

import 'kpi_item.dart';

class AdminDashboardSnapshot {
  final String adminName;
  final String email;
  final int dailyActiveUsers;
  final int signupsLastSevenDays;
  final double grossToday;
  final int transactionsToday;
  final double refundRate;
  final int approvalsPending;
  final int activeUsers;
  final int activeWindowMinutes;
  final int courseCount;
  final int cpuPercent;
  final int ramPercent;
  final int diskPercent;

  const AdminDashboardSnapshot({
    required this.adminName,
    required this.email,
    required this.dailyActiveUsers,
    required this.signupsLastSevenDays,
    required this.grossToday,
    required this.transactionsToday,
    required this.refundRate,
    required this.approvalsPending,
    required this.activeUsers,
    required this.activeWindowMinutes,
    required this.courseCount,
    required this.cpuPercent,
    required this.ramPercent,
    required this.diskPercent,
  });

  factory AdminDashboardSnapshot.fromPlatform(Map<String, dynamic> data) {
    final kpis = _map(data['kpis']);
    final active = _map(data['activeUsers']);
    final system = _map(data['system']);
    final courses = data['topCourses'];
    return AdminDashboardSnapshot(
      adminName: _text(data['name'], fallback: 'Quản trị viên'),
      email: _text(data['email']),
      dailyActiveUsers: _integer(kpis['dailyActiveUsers']),
      signupsLastSevenDays: _integer(kpis['signupsLastSevenDays']),
      grossToday: _decimal(kpis['grossToday']),
      transactionsToday: _integer(kpis['transactionsToday']),
      refundRate: _decimal(kpis['refundRate']),
      approvalsPending: _integer(kpis['approvalsPending']),
      activeUsers: _integer(active['count']),
      activeWindowMinutes: _integer(active['windowMinutes'], fallback: 10),
      courseCount: courses is List ? courses.length : 0,
      cpuPercent: _integer(system['cpuPercent']),
      ramPercent: _integer(system['ramPercent']),
      diskPercent: _integer(system['diskPercent']),
    );
  }

  static const empty = AdminDashboardSnapshot(
    adminName: 'Quản trị viên',
    email: '',
    dailyActiveUsers: 0,
    signupsLastSevenDays: 0,
    grossToday: 0,
    transactionsToday: 0,
    refundRate: 0,
    approvalsPending: 0,
    activeUsers: 0,
    activeWindowMinutes: 10,
    courseCount: 0,
    cpuPercent: 0,
    ramPercent: 0,
    diskPercent: 0,
  );

  List<KpiItem> get kpis => [
    KpiItem(
      id: 'admin_active_users',
      title: 'Người dùng',
      value: dailyActiveUsers.toString(),
      subtext: 'Đang hoạt động',
      trend: '0%',
      isPositiveTrend: true,
      icon: Icons.people_outline_rounded,
      gradientColors: const [Color(0xFF3B82F6), Color(0xFF60A5FA)],
    ),
    KpiItem(
      id: 'admin_report_users',
      title: 'Đăng ký mới (7 ngày)',
      value: signupsLastSevenDays.toString(),
      subtext: 'Trong 7 ngày gần đây',
      trend: '0%',
      isPositiveTrend: true,
      icon: Icons.person_add_alt_1_outlined,
      gradientColors: const [Color(0xFF10B981), Color(0xFF34D399)],
    ),
    KpiItem(
      id: 'admin_report_revenue',
      title: 'Doanh thu hôm nay',
      value: _money(grossToday),
      subtext: 'Tổng thanh toán',
      trend: '0%',
      isPositiveTrend: true,
      icon: Icons.bar_chart_rounded,
      gradientColors: const [Color(0xFFF59E0B), Color(0xFFFBBF24)],
    ),
    KpiItem(
      id: 'admin_transactions',
      title: 'Giao dịch hôm nay',
      value: transactionsToday.toString(),
      subtext: 'Giao dịch ghi nhận',
      trend: '0%',
      isPositiveTrend: true,
      icon: Icons.credit_card_outlined,
      gradientColors: const [Color(0xFF635BFF), Color(0xFF818CF8)],
    ),
    KpiItem(
      id: 'admin_transactions',
      title: 'Tỷ lệ hoàn tiền',
      value: '${refundRate.toStringAsFixed(1)}%',
      subtext: 'Trong 7 ngày',
      trend: '0%',
      isPositiveTrend: false,
      icon: Icons.rotate_left_rounded,
      gradientColors: const [Color(0xFFEF4444), Color(0xFFF87171)],
    ),
    KpiItem(
      id: 'admin_approval',
      title: 'Khóa học chờ duyệt',
      value: approvalsPending.toString(),
      subtext: 'Đang chờ xử lý',
      trend: '0%',
      isPositiveTrend: true,
      icon: Icons.verified_outlined,
      gradientColors: const [Color(0xFF0EA5E9), Color(0xFF38BDF8)],
    ),
  ];

  static String _money(double value) {
    final digits = value.round().toString();
    final buffer = StringBuffer();
    for (var index = 0; index < digits.length; index++) {
      if (index > 0 && (digits.length - index) % 3 == 0) buffer.write('.');
      buffer.write(digits[index]);
    }
    return '${buffer.toString()} đ';
  }

  static Map<String, dynamic> _map(Object? value) {
    if (value is! Map) return <String, dynamic>{};
    return value.map((key, item) => MapEntry(key.toString(), item));
  }

  static int _integer(Object? value, {int fallback = 0}) {
    if (value is num) return value.round();
    return int.tryParse(value?.toString() ?? '') ?? fallback;
  }

  static double _decimal(Object? value) {
    if (value is num) return value.toDouble();
    return double.tryParse(value?.toString() ?? '') ?? 0;
  }

  static String _text(Object? value, {String fallback = ''}) {
    final text = value?.toString().trim() ?? '';
    return text.isEmpty ? fallback : text;
  }
}
