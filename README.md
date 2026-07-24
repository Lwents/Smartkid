# SmartKid Android (Java)

SmartKid là ứng dụng Android viết bằng Java, ánh xạ nghiệp vụ của frontend SunEdu
sang REST API Django. Ứng dụng không kết nối trực tiếp PostgreSQL, không dùng
SQLite/Room và không sinh dữ liệu demo khi server trả lỗi.

## Chức năng

- Xác thực: đăng nhập username/email, OTP, JWT/refresh token, đăng ký học viên,
  quên mật khẩu, deep link đặt lại mật khẩu, đổi mật khẩu và đăng xuất.
- Học viên: dashboard, khóa học của tôi, danh mục, ghi danh, giỏ hàng, thanh toán
  MoMo, lịch sử thanh toán, trình phát bài học, video/tài liệu/TTS, tiến độ, lộ
  trình, đề thi, kết quả/xếp hạng/chứng chỉ, trò chơi, thông báo, hồ sơ/phụ huynh.
- Tương tác học tập: hỏi đáp theo bài học, phản hồi/reaction/report, AI Tutor,
  phân tích học tập, đánh giá đầu vào và khôi phục streak qua API thật.
- Giáo viên: dashboard, hỏi đáp, khóa học/thư viện, tạo khóa học, tạo đề và câu
  hỏi, xuất bản đề, thống kê/lượt nộp, tạo trò chơi/câu hỏi/cặp ghép từ, học viên,
  tiến độ, phản hồi và thông báo.
- Quản trị: dashboard, người dùng, tạo/khóa/mở tài khoản, duyệt khóa học, sức khỏe
  hệ thống, nhật ký, chính sách bảo mật, cấu hình, báo cáo, giao dịch và thông báo.

Bảng đối chiếu chi tiết với frontend nằm tại `FEATURE_PARITY.md`.

## Kiến trúc

```text
com.example.smartkid
├── core/              hằng số, log, JSON an toàn, theo dõi mạng
├── data/
│   ├── local/         SharedPreferences cho JWT/session và ID giỏ hàng
│   ├── model/         model Java
│   ├── remote/        Volley, refresh token, chuẩn hóa lỗi
│   └── repository/    truy cập API và ánh xạ JSON
├── domain/            kiểm tra/luật nghiệp vụ Java có unit test
└── ui/                Activity, Fragment, Adapter theo từng chức năng
```

`SharedPreferences` chỉ giữ phiên và lựa chọn giỏ hàng; dữ liệu nghiệp vụ luôn
được đồng bộ lại từ PostgreSQL thông qua backend.

## Chạy backend Docker

```bash
cd /home/lwent/Downloads/SunEdu/backend
docker compose up -d --build
docker compose ps
docker compose logs -f web
```

Backend: `http://localhost:8000`, PostgreSQL: `localhost:5433`, Redis:
`localhost:6379`. Tắt stack bằng `docker compose down`; không thêm `-v` nếu muốn
giữ volume PostgreSQL.

Tác vụ nền có thể bật riêng bằng `docker compose --profile worker up -d` và bộ
giám sát bằng `docker compose --profile monitoring up -d`; hai nhóm này không
khởi động mặc định để máy học tập nhẹ hơn.

Không có tài khoản/mật khẩu đăng nhập mặc định. Hãy dùng màn **Đăng ký** để tạo
tài khoản học viên thật. Tài khoản giáo viên/quản trị do admin tạo qua API/app.

## Địa chỉ API Android

- Debug và release: `http://160.250.181.242:8000/api/`.
- Backend đang dùng HTTP nên ứng dụng bật cleartext traffic trong Android manifest.

Cả debug và release hiện cho phép HTTP để kết nối trực tiếp tới VPS. Khi backend có
HTTPS, nên đổi lại `android:usesCleartextTraffic="false"`.

## Kiểm tra và APK

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

AI Tutor/câu hỏi AI cần `OPENROUTER_API_KEY` hoặc provider tương ứng trong
`backend/.env`. Thanh toán thật cần cấu hình đầy đủ các biến `MOMO_*`. Khi thiếu
cấu hình, server trả lỗi rõ ràng và Android hiển thị lỗi thay vì dựng kết quả giả.
