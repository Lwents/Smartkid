import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartkid_admin_flutter_module/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.example.smartkid/admin');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          return switch (call.method) {
            'getSession' => {
              'name': 'Quản trị viên',
              'email': 'admin@smartkid.vn',
            },
            'loadDashboard' => {
              'name': 'Quản trị viên',
              'email': 'admin@smartkid.vn',
              'kpis': {
                'dailyActiveUsers': 3,
                'signupsLastSevenDays': 3,
                'grossToday': 0.0,
                'transactionsToday': 0,
                'refundRate': 0.0,
                'approvalsPending': 0,
              },
              'activeUsers': {'count': 3, 'windowMinutes': 10},
              'system': {'cpuPercent': 12, 'ramPercent': 28, 'diskPercent': 35},
              'topCourses': <Object>[],
            },
            'loadActivityChart' => [
              {'date': '2026-07-18', 'newUsers': 0},
              {'date': '2026-07-19', 'newUsers': 1},
              {'date': '2026-07-20', 'newUsers': 0},
              {'date': '2026-07-21', 'newUsers': 1},
              {'date': '2026-07-22', 'newUsers': 0},
              {'date': '2026-07-23', 'newUsers': 0},
              {'date': '2026-07-24', 'newUsers': 1},
            ],
            'openFeature' => {'opened': true, 'message': ''},
            'logout' => true,
            _ => null,
          };
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  testWidgets(
    'Dashboard renders header title, KPIs, and bottom navigation bar',
    (WidgetTester tester) async {
      // Set realistic mobile phone viewport: 390x844 logical pixels
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1.0;

      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      // Build app and trigger frame
      await tester.pumpWidget(const SmartKidAdminApp());
      await tester.pumpAndSettle();

      // Verify Header title and subtitle exist
      expect(find.text('SmartKid Admin'), findsOneWidget);
      expect(find.text('Trung tâm quản trị hệ thống'), findsOneWidget);

      // Verify System Overview KPI Section Title exists
      expect(find.text('Tổng quan hệ thống'), findsOneWidget);

      // Verify all 6 KPI titles exist in full text without truncation
      expect(find.text('Người dùng'), findsWidgets);
      expect(find.text('Đăng ký mới (7 ngày)'), findsOneWidget);
      expect(find.text('Doanh thu hôm nay'), findsOneWidget);
      expect(find.text('Giao dịch hôm nay'), findsOneWidget);
      expect(find.text('Tỷ lệ hoàn tiền'), findsOneWidget);
      expect(find.text('Khóa học chờ duyệt'), findsOneWidget);

      // Scroll down to reveal Quick Actions section
      final scrollable = find
          .byWidgetPredicate(
            (widget) =>
                widget is Scrollable &&
                widget.axisDirection == AxisDirection.down,
          )
          .first;
      await tester.scrollUntilVisible(
        find.text('Thao tác nhanh'),
        200.0,
        scrollable: scrollable,
      );
      expect(find.text('Thao tác nhanh'), findsOneWidget);

      // Scroll down to reveal Admin Tools section
      await tester.scrollUntilVisible(
        find.text('Công cụ quản trị'),
        300.0,
        scrollable: scrollable,
      );
      expect(find.text('Công cụ quản trị'), findsOneWidget);

      // Verify Bottom Navigation Bar items exist
      expect(find.text('Tổng quan'), findsWidgets);
      expect(find.text('Người dùng'), findsWidgets);
      expect(find.text('Nội dung'), findsWidgets);
      expect(find.text('Cài đặt'), findsWidgets);

      // Test switching tabs (Tap on "Cài đặt")
      await tester.tap(find.text('Cài đặt').last);
      await tester.pumpAndSettle();

      // Verify the real settings screen and logout confirmation appear.
      expect(find.text('Cài đặt hệ thống'), findsOneWidget);
      expect(find.text('Đăng xuất'), findsOneWidget);

      await tester.tap(find.text('Đăng xuất'));
      await tester.pumpAndSettle();
      expect(find.text('Đăng xuất khỏi SmartKid?'), findsOneWidget);
    },
  );

  testWidgets(
    'Dashboard renders and switches tabs at narrow viewport 320x568 without overflow',
    (WidgetTester tester) async {
      // Set small mobile phone viewport: 320x568 logical pixels
      tester.view.physicalSize = const Size(320, 568);
      tester.view.devicePixelRatio = 1.0;

      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      // Build app and trigger frame
      await tester.pumpWidget(const SmartKidAdminApp());
      await tester.pumpAndSettle();

      // Verify header and the first dashboard section.
      expect(find.text('SmartKid Admin'), findsOneWidget);
      expect(find.text('Tổng quan hệ thống'), findsOneWidget);

      // Scroll down to reveal Quick Actions section
      final scrollable = find
          .byWidgetPredicate(
            (widget) =>
                widget is Scrollable &&
                widget.axisDirection == AxisDirection.down,
          )
          .first;
      await tester.scrollUntilVisible(
        find.text('Thao tác nhanh'),
        200.0,
        scrollable: scrollable,
      );
      expect(find.text('Thao tác nhanh'), findsOneWidget);

      // Scroll down to reveal Admin Tools section
      await tester.scrollUntilVisible(
        find.text('Công cụ quản trị'),
        300.0,
        scrollable: scrollable,
      );
      expect(find.text('Công cụ quản trị'), findsOneWidget);

      // Verify Bottom Navigation Bar items exist
      expect(find.text('Tổng quan'), findsWidgets);
      expect(find.text('Người dùng'), findsWidgets);
      expect(find.text('Nội dung'), findsWidgets);
      expect(find.text('Cài đặt'), findsWidgets);

      // Test switching through all tabs to ensure zero overflow errors
      for (final label in ['Tổng quan', 'Người dùng', 'Nội dung', 'Cài đặt']) {
        await tester.tap(find.text(label).last);
        await tester.pumpAndSettle();
      }
    },
  );

  testWidgets('Overview and settings support horizontal swipe navigation', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(const SmartKidAdminApp());
    await tester.pumpAndSettle();

    for (final expectedText in [
      'Quản lý tài khoản, quyền truy cập và hoạt động',
      'Quản lý khóa học và chất lượng nội dung học tập',
      'Cài đặt hệ thống',
    ]) {
      await tester.drag(find.byType(PageView), const Offset(-320, 0));
      await tester.pumpAndSettle();
      expect(find.text(expectedText), findsOneWidget);
    }

    for (var index = 0; index < 3; index++) {
      await tester.drag(find.byType(PageView), const Offset(320, 0));
      await tester.pumpAndSettle();
    }
    expect(find.text('SmartKid Admin'), findsOneWidget);
  });
}
