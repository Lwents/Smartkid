import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'app/app.dart';
import 'app/theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      statusBarBrightness: Brightness.light,
      systemNavigationBarColor: Color(0xFFF5F8FD),
      systemNavigationBarIconBrightness: Brightness.dark,
      systemNavigationBarDividerColor: Colors.transparent,
      systemNavigationBarContrastEnforced: false,
    ),
  );
  runApp(const SmartKidAdminApp());
}

class SmartKidAdminApp extends StatelessWidget {
  const SmartKidAdminApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SmartKid Admin',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      initialRoute: '/admin',
      routes: {
        '/': (context) => const AppShell(),
        '/admin': (context) => const AppShell(),
      },
    );
  }
}
