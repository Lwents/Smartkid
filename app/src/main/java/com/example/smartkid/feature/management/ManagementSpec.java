package com.example.smartkid.feature.management;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Ánh xạ route frontend quản lý sang API thật tương ứng. */
public final class ManagementSpec {
    private static final Map<String, ManagementSpec> SPECS;

    static {
        Map<String, ManagementSpec> specs = new LinkedHashMap<>();
        add(specs, "teacher_dashboard", "Bảng điều khiển", "teacher/dashboard/", "");
        add(specs, "teacher_qa", "Hỏi đáp bài học", "teacher/lesson-questions/", "teacher_qa");
        add(specs, "teacher_courses", "Khóa học", "content/courses/?page=1&pageSize=100", "teacher_courses");
        add(specs, "teacher_content_library", "Thư viện nội dung", "content/content-library/", "");
        addUnavailable(specs, "teacher_classes", "Lớp học",
                "Trang web hiện chỉ có giao diện rỗng và chưa gọi API lớp học.");
        addUnavailable(specs, "teacher_assignments", "Bài tập theo lớp",
                "Backend đang tắt module assignments vì lỗi import; Android không tạo dữ liệu giả.");
        addUnavailable(specs, "teacher_live", "Lớp học trực tuyến",
                "Frontend đang dùng mockDetail và backend chưa có API lịch học trực tuyến.");
        add(specs, "teacher_exams", "Bài kiểm tra", "activities/exercises/?page=1&pageSize=100&include_stats=true", "teacher_exams");
        add(specs, "teacher_exam_reports", "Báo cáo bài kiểm tra", "activities/exercises/?page=1&pageSize=100&include_stats=true", "teacher_exam_reports");
        add(specs, "teacher_students", "Học viên", "teacher/students/?page=1&pageSize=100", "teacher_students");
        add(specs, "teacher_progress", "Tiến độ học viên", "teacher/students/?page=1&pageSize=100", "");
        add(specs, "teacher_feedback", "Phản hồi học viên", "teacher/students/feedback/list/?page=1&pageSize=100", "");
        add(specs, "teacher_notifications", "Thông báo", "teacher/notifications/?limit=100", "");

        add(specs, "admin_dashboard", "Bảng điều khiển", "admin/dashboard/", "");
        add(specs, "admin_active_users", "Người dùng đang hoạt động", "admin/dashboard/active-users/", "");
        add(specs, "admin_users", "Quản lý người dùng", "account/admin/users/?page=1&pageSize=100", "admin_users");
        add(specs, "admin_courses", "Quản lý khóa học", "admin/courses/?page=1&pageSize=100", "admin_courses");
        add(specs, "admin_approval", "Duyệt khóa học", "admin/courses/?status=pending_review&page=1&pageSize=100", "admin_courses");
        add(specs, "admin_health", "Sức khỏe hệ thống", "admin/system/health/", "");
        addUnavailable(specs, "admin_activity", "Nhật ký hoạt động",
                "Backend hiện tổng hợp signup/last_login thành log thay vì đọc nhật ký hoạt động thực tế.");
        add(specs, "admin_security", "Bảo mật", "admin/security/policy/", "");
        addUnavailable(specs, "admin_sessions", "Phiên đăng nhập",
                "Backend hiện trả danh sách session hard-code và thao tác revoke chỉ là placeholder.");
        addUnavailable(specs, "admin_config", "Cấu hình hệ thống",
                "Backend hiện chỉ lưu cấu hình trong Redis cache và trả bộ mặc định khi cache mất; chưa phải cấu hình bền vững.");
        addUnavailable(specs, "admin_backups", "Sao lưu hệ thống",
                "Backend hiện chỉ tạo metadata backup trong cache, chưa sao lưu PostgreSQL thật.");
        add(specs, "admin_report_revenue", "Báo cáo doanh thu", "admin/reports/revenue/?type=timeseries", "");
        add(specs, "admin_report_users", "Báo cáo người dùng", "admin/reports/users/?type=kpis", "");
        add(specs, "admin_report_learning", "Báo cáo học tập", "admin/reports/learning/?type=kpis", "");
        add(specs, "admin_report_content", "Báo cáo nội dung", "admin/reports/content/?type=kpis", "");
        add(specs, "admin_transactions", "Giao dịch", "admin/transactions/?page=1&pageSize=100", "admin_transactions");
        add(specs, "admin_notifications", "Thông báo", "admin/notifications/?limit=100", "");
        SPECS = Collections.unmodifiableMap(specs);
    }

    private final String key;
    private final String title;
    private final String endpoint;
    private final String actionKind;
    private final String unavailableReason;

    private ManagementSpec(String key, String title, String endpoint,
                           String actionKind, String unavailableReason) {
        this.key = key;
        this.title = title;
        this.endpoint = endpoint;
        this.actionKind = actionKind;
        this.unavailableReason = unavailableReason;
    }

    public static ManagementSpec get(String key) { return SPECS.get(key); }
    public static Map<String, ManagementSpec> all() { return SPECS; }
    public String getKey() { return key; }
    public String getTitle() { return title; }
    public String getEndpoint() { return endpoint; }
    public String getActionKind() { return actionKind; }
    public String getUnavailableReason() { return unavailableReason; }
    public boolean isAvailable() { return unavailableReason.isEmpty(); }

    public boolean isAllowedForRole(String role) {
        String normalized = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (key.startsWith("admin_")) return "admin".equals(normalized);
        if (key.startsWith("teacher_")) {
            return "teacher".equals(normalized) || "instructor".equals(normalized);
        }
        return false;
    }

    private static void add(Map<String, ManagementSpec> target, String key, String title,
                            String endpoint, String actionKind) {
        target.put(key, new ManagementSpec(key, title, endpoint, actionKind, ""));
    }

    private static void addUnavailable(Map<String, ManagementSpec> target, String key,
                                       String title, String reason) {
        target.put(key, new ManagementSpec(key, title, "", "", reason));
    }
}
