# SmartKid Admin Flutter Module

Module này chỉ cung cấp giao diện Admin Dashboard cho ứng dụng Android
`com.example.smartkid`. Đăng nhập, phiên người dùng, API và các màn quản lý vẫn
do ứng dụng Android chính xử lý qua `AdminFlutterActivity`.

## Cấu trúc

- `lib/app`: theme và shell điều hướng.
- `lib/core`: cầu nối Flutter với Android.
- `lib/features/admin_dashboard`: model, catalog, painter, screen và widget.
- `test`: kiểm tra giao diện ở kích thước 390 x 844 và 320 x 568.

## Kiểm tra

```bash
flutter analyze
flutter test
flutter build aar --no-profile
```

Sau khi tạo lại AAR, build APK từ thư mục gốc của dự án SmartKid:

```bash
./gradlew assembleDebug
```
