import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../app/theme.dart';
import '../../../core/admin_platform_bridge.dart';
import '../models/activity_chart_data.dart';
import '../painters/activity_chart_painter.dart';
import 'glass_card.dart';

class ActivityChartWidget extends StatefulWidget {
  const ActivityChartWidget({super.key});

  @override
  State<ActivityChartWidget> createState() => _ActivityChartWidgetState();
}

class _ActivityChartWidgetState extends State<ActivityChartWidget> {
  String _selectedFilter = '7_days';
  bool _loading = true;
  ActivityChartData _data = const ActivityChartData(
    periodKey: '7_days',
    periodName: '7 ngày',
    labels: [],
    values: [],
    metricTitle: 'Người dùng mới',
  );

  @override
  void initState() {
    super.initState();
    _loadPeriod('7_days', 7, '7 ngày');
  }

  Future<void> _loadPeriod(String key, int days, String name) async {
    final to = DateTime.now();
    final from = DateTime(
      to.year,
      to.month,
      to.day,
    ).subtract(Duration(days: days - 1));
    await _loadRange(key: key, name: name, from: from, to: to);
  }

  Future<void> _loadRange({
    required String key,
    required String name,
    required DateTime from,
    required DateTime to,
  }) async {
    if (!_loading) {
      setState(() {
        _selectedFilter = key;
        _loading = true;
      });
    }
    try {
      final points = await AdminPlatformBridge.instance.loadActivityChart(
        from: from,
        to: to,
      );
      if (!mounted) return;
      setState(() {
        _selectedFilter = key;
        _data = _chartData(key, name, points);
        _loading = false;
      });
    } on MissingPluginException {
      // Standalone previews intentionally render an empty chart state.
      if (mounted) setState(() => _loading = false);
    } on PlatformException {
      if (mounted) {
        setState(() {
          _selectedFilter = key;
          _data = _emptyData(key, name);
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _selectedFilter = key;
          _data = _emptyData(key, name);
          _loading = false;
        });
      }
    }
  }

  Future<void> _selectCustomRange() async {
    final now = DateTime.now();
    final range = await showDateRangePicker(
      context: context,
      firstDate: DateTime(now.year - 2),
      lastDate: now,
      initialDateRange: DateTimeRange(
        start: now.subtract(const Duration(days: 13)),
        end: now,
      ),
      helpText: 'Chọn khoảng thời gian',
      cancelText: 'Hủy',
      confirmText: 'Áp dụng',
      saveText: 'Áp dụng',
    );
    if (range == null) return;
    await _loadRange(
      key: 'custom',
      name: '${_shortDate(range.start)} – ${_shortDate(range.end)}',
      from: range.start,
      to: range.end,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Biểu đồ hoạt động',
            style: TextStyle(
              color: AppColors.textPrimary,
              fontSize: 18,
              fontWeight: FontWeight.bold,
              letterSpacing: -0.3,
            ),
          ),
          const SizedBox(height: 12),
          GlassCard(
            borderRadius: 20,
            padding: const EdgeInsets.all(4),
            backgroundColor: Colors.white.withValues(alpha: 0.52),
            child: Row(
              children: [
                _filterTab(
                  '7_days',
                  '7 ngày',
                  () => _loadPeriod('7_days', 7, '7 ngày'),
                ),
                _filterTab(
                  '30_days',
                  '30 ngày',
                  () => _loadPeriod('30_days', 30, '30 ngày'),
                ),
                _filterTab(
                  '90_days',
                  '90 ngày',
                  () => _loadPeriod('90_days', 90, '90 ngày'),
                ),
                _filterTab(
                  'custom',
                  'Tùy chỉnh',
                  _selectCustomRange,
                  icon: Icons.calendar_today_outlined,
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          GlassCard(
            borderRadius: 24,
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        _data.metricTitle,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    if (_loading)
                      const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    else
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.primaryPurpleLight,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          _data.periodName,
                          style: const TextStyle(
                            color: AppColors.primaryPurple,
                            fontSize: 10.5,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 12),
                SizedBox(
                  height: 176,
                  width: double.infinity,
                  child: _data.values.isEmpty && !_loading
                      ? const Center(
                          child: Text(
                            'Chưa có dữ liệu trong khoảng thời gian này',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              color: AppColors.textMuted,
                              fontSize: 12,
                            ),
                          ),
                        )
                      : RepaintBoundary(
                          child: CustomPaint(
                            painter: ActivityChartPainter(
                              labels: _data.labels,
                              values: _data.values,
                            ),
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

  Widget _filterTab(
    String key,
    String label,
    VoidCallback onTap, {
    IconData? icon,
  }) {
    final selected = _selectedFilter == key;
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.symmetric(vertical: 9, horizontal: 2),
          decoration: BoxDecoration(
            color: selected ? Colors.white : Colors.transparent,
            borderRadius: BorderRadius.circular(16),
            boxShadow: selected
                ? const [
                    BoxShadow(
                      color: Color(0x0F1E293B),
                      blurRadius: 8,
                      offset: Offset(0, 2),
                    ),
                  ]
                : null,
          ),
          child: FittedBox(
            fit: BoxFit.scaleDown,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  label,
                  style: TextStyle(
                    color: selected
                        ? AppColors.primaryPurple
                        : AppColors.textSecondary,
                    fontSize: 11.5,
                    fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                  ),
                ),
                if (icon != null) ...[
                  const SizedBox(width: 3),
                  Icon(
                    icon,
                    size: 12,
                    color: selected
                        ? AppColors.primaryPurple
                        : AppColors.textSecondary,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  ActivityChartData _chartData(
    String key,
    String name,
    List<Map<String, dynamic>> points,
  ) {
    if (points.isEmpty) return _emptyData(key, name);
    final groupSize = (points.length / 7).ceil().clamp(1, points.length);
    final labels = <String>[];
    final values = <double>[];
    for (var start = 0; start < points.length; start += groupSize) {
      final end = (start + groupSize).clamp(0, points.length);
      final group = points.sublist(start, end);
      labels.add(_displayApiDate(group.last['date']?.toString() ?? ''));
      values.add(
        group.fold<double>(0, (total, point) {
          final value = point['newUsers'];
          return total +
              (value is num
                  ? value.toDouble()
                  : double.tryParse('$value') ?? 0);
        }),
      );
    }
    return ActivityChartData(
      periodKey: key,
      periodName: name,
      labels: labels,
      values: values,
      metricTitle: 'Người dùng mới',
    );
  }

  ActivityChartData _emptyData(String key, String name) => ActivityChartData(
    periodKey: key,
    periodName: name,
    labels: const [],
    values: const [],
    metricTitle: 'Người dùng mới',
  );

  String _displayApiDate(String value) {
    final date = DateTime.tryParse(value);
    return date == null ? value : _shortDate(date);
  }

  String _shortDate(DateTime date) =>
      '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}';
}
