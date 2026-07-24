import 'package:flutter/services.dart';

class FeatureOpenResult {
  final bool opened;
  final String message;

  const FeatureOpenResult({required this.opened, required this.message});
}

class AdminPlatformBridge {
  AdminPlatformBridge._();

  static final AdminPlatformBridge instance = AdminPlatformBridge._();
  static const MethodChannel _channel = MethodChannel(
    'com.example.smartkid/admin',
  );

  Future<Map<String, dynamic>> getSession() async {
    final value = await _channel.invokeMethod<Object?>('getSession');
    return _asStringMap(value);
  }

  Future<Map<String, dynamic>> loadDashboard() async {
    final value = await _channel.invokeMethod<Object?>('loadDashboard');
    return _asStringMap(value);
  }

  Future<List<Map<String, dynamic>>> loadActivityChart({
    required DateTime from,
    required DateTime to,
  }) async {
    final value = await _channel.invokeMethod<Object?>('loadActivityChart', {
      'from': _dateKey(from),
      'to': _dateKey(to),
    });
    if (value is! List) return const [];
    return value.map(_asStringMap).toList(growable: false);
  }

  Future<FeatureOpenResult> openFeature(String key) async {
    final value = await _channel.invokeMethod<Object?>('openFeature', {
      'key': key,
    });
    final payload = _asStringMap(value);
    return FeatureOpenResult(
      opened: payload['opened'] == true,
      message: payload['message']?.toString() ?? '',
    );
  }

  Future<void> logout() async {
    await _channel.invokeMethod<Object?>('logout');
  }

  static String _dateKey(DateTime date) {
    String twoDigits(int value) => value.toString().padLeft(2, '0');
    return '${date.year}-${twoDigits(date.month)}-${twoDigits(date.day)}';
  }

  static Map<String, dynamic> _asStringMap(Object? value) {
    if (value is! Map) return <String, dynamic>{};
    return value.map(
      (key, item) => MapEntry(key.toString(), _normalizeValue(item)),
    );
  }

  static dynamic _normalizeValue(Object? value) {
    if (value is Map) return _asStringMap(value);
    if (value is List) {
      return value.map(_normalizeValue).toList(growable: false);
    }
    return value;
  }
}
