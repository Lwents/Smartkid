# SmartKid EDU Android

SmartKid EDU là ứng dụng Android viết bằng Java, phục vụ ba vai trò học sinh,
giáo viên và quản trị viên qua REST API Django. Ứng dụng không kết nối trực tiếp
PostgreSQL và không dựng dữ liệu giả khi server trả lỗi.

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

## Kiến trúc

```text
com.example.smartkid
├── common/
│   ├── navigation/    điều hướng và phân quyền theo vai trò
│   ├── ui/            thành phần giao diện dùng chung
│   └── util/          hằng số, log và xử lý JSON an toàn
├── data/
│   ├── local/         SharedPreferences cho JWT/session và ID giỏ hàng
│   ├── model/         model Java
│   ├── remote/        Volley, refresh token, chuẩn hóa lỗi
│   └── repository/    truy cập API và ánh xạ JSON
├── domain/            kiểm tra/luật nghiệp vụ Java có unit test
└── feature/
    ├── shared/        xác thực, hồ sơ và thông báo
    ├── student/       học tập, khóa học, bài thi và AI
    ├── teacher/       soạn nội dung, đề thi và quản lý học viên
    └── admin/         dashboard và vận hành hệ thống
```

`SharedPreferences` chỉ giữ phiên và lựa chọn giỏ hàng; dữ liệu nghiệp vụ luôn
được đồng bộ lại từ PostgreSQL thông qua backend.

Tài nguyên Android được tách theo miền (`res-auth`, `res-course`, `res-exam`,
`res-teacher`, `res-admin`, ...) và được khai báo trong `sourceSets` để tránh một
thư mục `res` quá lớn.

## Chạy backend Docker

Backend nằm trong repository `BeSmartkid` và chạy bằng Docker Compose:

```bash
docker compose up -d --build
docker compose ps
```

Backend: `http://localhost:8000`, PostgreSQL: `localhost:5433`, Redis:
`localhost:6379`. Tắt stack bằng `docker compose down`; không thêm `-v` nếu muốn
giữ volume PostgreSQL.

Không có tài khoản/mật khẩu đăng nhập mặc định. Hãy dùng màn **Đăng ký** để tạo
tài khoản học viên thật. Tài khoản giáo viên/quản trị do admin tạo qua API/app.

## Địa chỉ API Android

- `API_BASE_URL` lấy từ tham số Gradle hoặc `local.properties`.
- Mặc định emulator sử dụng `http://10.0.2.2:8000/api/`.
- VPS dự phòng hiện đi qua nginx tại `http://160.250.181.242/api/`.

Cleartext HTTP chỉ được phép với các host backend đã khai báo trong
`network_security_config.xml`, không được mở cho toàn bộ Internet. Bản triển khai
chính thức nên chuyển VPS sang HTTPS và bỏ ngoại lệ HTTP tương ứng.

## Kiểm tra và APK

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions chạy unit test, Android Lint và build debug trên mọi push hoặc pull
request vào `main`. Bản release sử dụng R8 và resource shrinking.

AI Tutor/câu hỏi AI cần `OPENROUTER_API_KEY` hoặc provider tương ứng trong
`backend/.env`. Thanh toán thật cần cấu hình đầy đủ các biến `MOMO_*`. Khi thiếu
cấu hình, server trả lỗi rõ ràng và Android hiển thị lỗi thay vì dựng kết quả giả.
