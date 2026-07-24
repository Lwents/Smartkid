import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../app/theme.dart';
import '../../../core/admin_platform_bridge.dart';
import '../data/admin_dashboard_catalog.dart';
import '../models/admin_dashboard_snapshot.dart';
import '../models/admin_tool_item.dart';
import '../models/kpi_item.dart';
import '../models/quick_action_item.dart';
import '../widgets/activity_chart_widget.dart';
import '../widgets/admin_tools_widget.dart';
import '../widgets/header_widget.dart';
import '../widgets/kpi_grid_widget.dart';
import '../widgets/quick_actions_widget.dart';

class DashboardScreen extends StatefulWidget {
  final Future<void> Function(String key) onOpenFeature;
  final VoidCallback onOpenSettings;

  const DashboardScreen({
    super.key,
    required this.onOpenFeature,
    required this.onOpenSettings,
  });

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final ScrollController _scrollController = ScrollController();
  AdminDashboardSnapshot _snapshot = AdminDashboardSnapshot.empty;
  bool _isLoading = true;
  bool _showActivityChart = false;
  String _errorMessage = '';

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_handleScroll);
    _loadDashboard(showLoading: false);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_handleScroll);
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _loadDashboard({bool showLoading = true}) async {
    if (mounted && showLoading) {
      setState(() {
        _isLoading = true;
        _errorMessage = '';
      });
    }
    try {
      final payload = await AdminPlatformBridge.instance.loadDashboard();
      if (!mounted) return;
      setState(() {
        _snapshot = AdminDashboardSnapshot.fromPlatform(payload);
        _isLoading = false;
        _errorMessage = '';
      });
    } on MissingPluginException {
      // Widget tests and standalone previews do not have the Android host channel.
      if (mounted) setState(() => _isLoading = false);
    } on PlatformException catch (error) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorMessage = error.message ?? 'Không thể tải dữ liệu';
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorMessage = 'Không thể kết nối dữ liệu quản trị';
        });
      }
    }
  }

  void _handleScroll() {
    if (_showActivityChart || !_scrollController.hasClients) return;
    if (_scrollController.offset > 420) {
      setState(() => _showActivityChart = true);
    }
  }

  void _scrollToTools() {
    if (!_scrollController.hasClients) return;
    _scrollController.animateTo(
      _scrollController.position.maxScrollExtent * 0.62,
      duration: const Duration(milliseconds: 520),
      curve: Curves.easeOutCubic,
    );
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: RefreshIndicator(
        color: AppColors.primaryPurple,
        onRefresh: () => _loadDashboard(),
        child: CustomScrollView(
          controller: _scrollController,
          physics: const AlwaysScrollableScrollPhysics(
            parent: BouncingScrollPhysics(),
          ),
          slivers: [
            SliverToBoxAdapter(
              child: RepaintBoundary(
                child: HeaderWidget(
                  adminName: _snapshot.adminName,
                  onMenuPressed: _scrollToTools,
                  onNotificationPressed: () =>
                      widget.onOpenFeature('admin_notifications'),
                  onProfilePressed: widget.onOpenSettings,
                ),
              ),
            ),
            if (_isLoading)
              const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.symmetric(horizontal: 22),
                  child: LinearProgressIndicator(
                    minHeight: 2,
                    color: AppColors.primaryPurple,
                    backgroundColor: Colors.transparent,
                    borderRadius: BorderRadius.all(Radius.circular(2)),
                  ),
                ),
              ),
            if (_errorMessage.isNotEmpty)
              SliverToBoxAdapter(
                child: _DashboardError(
                  message: _errorMessage,
                  onRetry: _loadDashboard,
                ),
              ),
            SliverToBoxAdapter(
              child: RepaintBoundary(
                child: KpiGridWidget(
                  items: _snapshot.kpis,
                  onItemTap: (KpiItem item) => widget.onOpenFeature(item.id),
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: RepaintBoundary(
                child: QuickActionsWidget(
                  items: AdminDashboardCatalog.quickActions,
                  onItemTap: (QuickActionItem action) =>
                      widget.onOpenFeature(action.id),
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: RepaintBoundary(
                child: _showActivityChart
                    ? const ActivityChartWidget()
                    : const SizedBox(height: 326),
              ),
            ),
            AdminToolsWidget(
              tools: AdminDashboardCatalog.tools(_snapshot),
              onToolTap: (AdminToolItem tool) => widget.onOpenFeature(tool.id),
            ),
            const SliverToBoxAdapter(child: SizedBox(height: 104)),
          ],
        ),
      ),
    );
  }
}

class _DashboardError extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;

  const _DashboardError({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 6, 16, 2),
      padding: const EdgeInsets.fromLTRB(14, 10, 8, 10),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF1F2).withValues(alpha: 0.9),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFFECACA)),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.cloud_off_outlined,
            color: AppColors.accentRed,
            size: 20,
          ),
          const SizedBox(width: 9),
          Expanded(
            child: Text(
              message,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AppColors.textSecondary,
                fontSize: 12,
                height: 1.3,
              ),
            ),
          ),
          TextButton(onPressed: onRetry, child: const Text('Thử lại')),
        ],
      ),
    );
  }
}
