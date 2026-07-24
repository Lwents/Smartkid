import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/admin_platform_bridge.dart';
import '../features/admin_dashboard/screens/admin_section_screen.dart';
import '../features/admin_dashboard/screens/dashboard_screen.dart';
import '../features/admin_dashboard/screens/settings_screen.dart';
import '../features/admin_dashboard/widgets/custom_bottom_nav.dart';

class AppShell extends StatefulWidget {
  const AppShell({super.key});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int _currentIndex = 0;
  int _navigationTransitionId = 0;
  bool _isProgrammaticNavigation = false;
  String _adminName = 'Quản trị viên';
  String _adminEmail = '';
  late final PageController _pageController;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
    _loadSession();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  Future<void> _loadSession() async {
    try {
      final session = await AdminPlatformBridge.instance.getSession();
      if (!mounted) return;
      setState(() {
        _adminName = _nonEmpty(session['name'], 'Quản trị viên');
        _adminEmail = _nonEmpty(
          session['email'],
          'Tài khoản quản trị SmartKid',
        );
      });
    } on MissingPluginException {
      // Standalone Flutter previews have no native SmartKid session.
    } catch (_) {
      // Dashboard data still provides a safe fallback identity.
    }
  }

  Future<void> _openFeature(String key) async {
    try {
      final response = await AdminPlatformBridge.instance.openFeature(key);
      if (!mounted || response.opened) return;
      _showMessage(
        response.message.isEmpty ? 'Chức năng chưa sẵn sàng' : response.message,
      );
    } on MissingPluginException {
      if (mounted) _showMessage('Chức năng này chỉ mở trong ứng dụng SmartKid');
    } on PlatformException catch (error) {
      if (mounted) _showMessage(error.message ?? 'Không thể mở chức năng');
    } catch (_) {
      if (mounted) _showMessage('Không thể mở chức năng');
    }
  }

  Future<void> _logout() async {
    try {
      await AdminPlatformBridge.instance.logout();
    } on MissingPluginException {
      if (mounted) {
        _showMessage('Đăng xuất chỉ hoạt động trong ứng dụng SmartKid');
      }
    } catch (_) {
      if (mounted) _showMessage('Không thể đăng xuất, vui lòng thử lại');
    }
  }

  void _selectNavigation(int index) {
    _openLocalPage(index);
  }

  Future<void> _openLocalPage(int navigationIndex) async {
    final page = navigationIndex.clamp(0, 3);
    if (page == _currentIndex) return;

    final distance = (_currentIndex - page).abs();
    final transitionId = ++_navigationTransitionId;
    _isProgrammaticNavigation = true;
    setState(() => _currentIndex = page);

    if (!_pageController.hasClients) {
      _isProgrammaticNavigation = false;
      return;
    }

    try {
      await _pageController.animateToPage(
        page,
        duration: Duration(milliseconds: distance > 1 ? 300 : 250),
        curve: Curves.easeOutQuart,
      );
    } finally {
      if (transitionId == _navigationTransitionId) {
        _isProgrammaticNavigation = false;
      }
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFFF7F9FC), Color(0xFFFBFCFE), Color(0xFFF3F6FA)],
            stops: [0, 0.56, 1],
          ),
        ),
        child: Stack(
          children: [
            const RepaintBoundary(child: _BackgroundGlow()),
            PageView(
              controller: _pageController,
              allowImplicitScrolling: true,
              physics: const BouncingScrollPhysics(),
              onPageChanged: (page) {
                if (!_isProgrammaticNavigation && _currentIndex != page) {
                  setState(() => _currentIndex = page);
                }
              },
              children: [
                _KeepAlivePage(
                  child: DashboardScreen(
                    onOpenFeature: _openFeature,
                    onOpenSettings: () => _openLocalPage(3),
                  ),
                ),
                _KeepAlivePage(
                  child: AdminSectionScreen(
                    title: 'Người dùng',
                    subtitle: 'Quản lý tài khoản, quyền truy cập và hoạt động',
                    icon: Icons.people_outline_rounded,
                    accentColor: const Color(0xFF4F7CFF),
                    onOpenFeature: _openFeature,
                    actions: const [
                      AdminSectionAction(
                        featureKey: 'admin_users',
                        title: 'Quản lý người dùng',
                        description:
                            'Tìm kiếm, phân quyền, khóa và mở khóa tài khoản',
                        icon: Icons.manage_accounts_outlined,
                        color: Color(0xFF4F7CFF),
                      ),
                      AdminSectionAction(
                        featureKey: 'admin_active_users',
                        title: 'Người dùng đang hoạt động',
                        description: 'Theo dõi người dùng trực tuyến gần đây',
                        icon: Icons.people_alt_outlined,
                        color: Color(0xFF13B981),
                      ),
                      AdminSectionAction(
                        featureKey: 'admin_report_users',
                        title: 'Báo cáo người dùng',
                        description: 'Xem tăng trưởng và mức độ hoạt động',
                        icon: Icons.analytics_outlined,
                        color: Color(0xFF7357E8),
                      ),
                      AdminSectionAction(
                        featureKey: 'admin_security',
                        title: 'Bảo mật tài khoản',
                        description: 'Kiểm tra chính sách và cảnh báo bảo mật',
                        icon: Icons.shield_outlined,
                        color: Color(0xFF0EA5E9),
                      ),
                    ],
                  ),
                ),
                _KeepAlivePage(
                  child: AdminSectionScreen(
                    title: 'Nội dung',
                    subtitle: 'Quản lý khóa học và chất lượng nội dung học tập',
                    icon: Icons.menu_book_outlined,
                    accentColor: const Color(0xFF13B981),
                    onOpenFeature: _openFeature,
                    actions: const [
                      AdminSectionAction(
                        featureKey: 'admin_courses',
                        title: 'Quản lý khóa học',
                        description:
                            'Danh sách, trạng thái và nội dung khóa học',
                        icon: Icons.book_outlined,
                        color: Color(0xFF13B981),
                      ),
                      AdminSectionAction(
                        featureKey: 'admin_approval',
                        title: 'Duyệt khóa học',
                        description: 'Kiểm tra nội dung mới gửi từ giáo viên',
                        icon: Icons.verified_outlined,
                        color: Color(0xFFF59E0B),
                      ),
                      AdminSectionAction(
                        featureKey: 'admin_report_content',
                        title: 'Báo cáo nội dung',
                        description: 'Đánh giá hiệu quả và mức độ tương tác',
                        icon: Icons.dashboard_outlined,
                        color: Color(0xFF7357E8),
                      ),
                      AdminSectionAction(
                        featureKey: 'admin_report_learning',
                        title: 'Báo cáo học tập',
                        description: 'Tiến độ, điểm số và thời gian học',
                        icon: Icons.school_outlined,
                        color: Color(0xFF0EA5E9),
                      ),
                    ],
                  ),
                ),
                _KeepAlivePage(
                  child: SettingsScreen(
                    adminName: _adminName,
                    adminEmail: _adminEmail,
                    onOpenFeature: _openFeature,
                    onLogout: _logout,
                  ),
                ),
              ],
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: SafeArea(
                top: false,
                child: CustomBottomNav(
                  selectedIndex: _currentIndex,
                  onItemSelected: _selectNavigation,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _nonEmpty(Object? value, String fallback) {
    final text = value?.toString().trim() ?? '';
    return text.isEmpty ? fallback : text;
  }
}

class _KeepAlivePage extends StatefulWidget {
  final Widget child;

  const _KeepAlivePage({required this.child});

  @override
  State<_KeepAlivePage> createState() => _KeepAlivePageState();
}

class _KeepAlivePageState extends State<_KeepAlivePage>
    with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return RepaintBoundary(child: widget.child);
  }
}

class _BackgroundGlow extends StatelessWidget {
  const _BackgroundGlow();

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Stack(
        children: [
          Positioned(
            top: -90,
            right: -80,
            child: Container(
              width: 250,
              height: 250,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [Color(0x24D7E7F8), Color(0x00D7E7F8)],
                ),
              ),
            ),
          ),
          Positioned(
            top: 430,
            left: -120,
            child: Container(
              width: 280,
              height: 280,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [Color(0x18DDF2EC), Color(0x00DDF2EC)],
                ),
              ),
            ),
          ),
          Positioned(
            right: -100,
            bottom: 120,
            child: Container(
              width: 260,
              height: 260,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [Color(0x14E2DEFA), Color(0x00E2DEFA)],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
