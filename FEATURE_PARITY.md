# Đối chiếu frontend SunEdu → SmartKid Android

Ngày rà soát: 22/07/2026. Nguồn đối chiếu là `frontend/src/router/index.ts`, các
page/service Vue và URL Django trong `/home/lwent/Downloads/SunEdu/backend`.

## Xác thực và dùng chung

| Frontend | Android | Nguồn dữ liệu |
|---|---|---|
| Login, OTP | `LoginActivity` | JWT/OTP API |
| Register | `RegisterActivity` | API đăng ký, role student |
| Forgot/Reset password | `ForgotPasswordActivity`, `ResetPasswordActivity` | API + deep link `smartkid://reset-password` |
| Notifications | `FeatureListActivity` | Danh sách, đọc một/đọc tất cả qua API |
| Profile, change password | Màn hồ sơ/đổi mật khẩu | API tài khoản |

## Học viên

| Nhóm route frontend | Android/API |
|---|---|
| Dashboard | Dashboard thật, thống kê và hoạt động từ server |
| My courses, catalog, course detail | Danh sách/tìm kiếm/sắp xếp, chi tiết, ghi danh miễn phí hoặc thêm khóa trả phí vào giỏ |
| Course player | Bài học, tiến độ, video, tài liệu, TextToSpeech, AI Tutor và thảo luận bài học |
| Learning path | Lộ trình server, phân tích học tập, gợi ý bài học, đánh giá đầu vào, streak |
| Exams/detail/result | Bắt đầu attempt, câu hỏi động, timer, nộp đáp án, kết quả thật |
| Certificates/ranking | API chứng chỉ và xếp hạng; không có fallback demo |
| Games | Quiz/word match, session, nộp điểm, leaderboard qua API |
| Payments/cart/checkout/history | Giỏ giữ ID khóa học, đồng bộ giá từ catalog, MoMo, lịch sử; backend tự ghi danh sau IPN thành công |
| Account/parent | Xem/sửa hồ sơ, đổi mật khẩu, thông tin phụ huynh lưu trong PostgreSQL |

## Giáo viên

| Route frontend | Trạng thái Android |
|---|---|
| Dashboard, lesson QA | Có; xem dữ liệu và trả lời học viên |
| Courses/new/detail | Có; xem, tạo, xuất bản/gỡ xuất bản/lưu trữ |
| Content library | Có danh sách API thật |
| Exams/new/detail/reports | Có; tạo đề, thêm câu hỏi + đáp án, xuất bản, xóa, thống kê và lượt nộp |
| Games | Có; tạo, thêm quiz/cặp word-match, xuất bản, xóa và thống kê |
| Students/progress/feedback | Có; danh sách/tiến độ và gửi feedback + rating |
| Notifications | Có danh sách API thật |
| Classes | Chưa bật: page Vue hiện chưa gọi API lớp học |
| Class assignments | Chưa bật: backend đang vô hiệu module assignments do lỗi import |
| Online class | Chưa bật: frontend dùng `mockDetail`, backend chưa có API lịch/live thật |

## Quản trị

| Route frontend | Trạng thái Android |
|---|---|
| Dashboard/active users | Có dữ liệu API thật |
| Users/detail | Có danh sách/chi tiết JSON, tạo, khóa và mở khóa |
| Courses/approval/detail | Có danh sách, duyệt/từ chối/xuất bản/lưu trữ/khôi phục |
| System health/security | Có dữ liệu server thật |
| Revenue/user/learning/content reports | Có dữ liệu báo cáo API thật |
| Transactions | Có danh sách/chi tiết và đánh dấu tranh chấp |
| Notifications | Có dữ liệu API thật |
| Sessions | Chưa bật: endpoint Django đang trả session hard-code và revoke placeholder |
| Activity logs | Chưa bật: endpoint đang tổng hợp signup/last_login, chưa có nhật ký hoạt động thực tế |
| System config | Chưa bật chỉnh sửa: endpoint chỉ lưu Redis cache/default, chưa có dữ liệu cấu hình bền vững |
| Backups | Chưa bật: endpoint chỉ lưu metadata trong cache, chưa sao lưu PostgreSQL |
| Refund | Không cho thao tác: endpoint cũ chỉ đổi status DB, chưa gọi hoàn tiền MoMo |

## Nguyên tắc dữ liệu

- Android không có SQLite/Room và không chứa mảng khóa học, đề thi, game hay chứng
  chỉ mẫu.
- Khi API rỗng, app hiển thị trạng thái “chưa có dữ liệu”. Khi API/provider lỗi,
  app hiển thị nguyên nhân đã chuẩn hóa và ghi log, không thay bằng dữ liệu giả.
- Các mục chưa có backend thật vẫn được ghi rõ trong cổng giáo viên/quản trị để
  đối chiếu frontend, nhưng không mở một màn giả.
