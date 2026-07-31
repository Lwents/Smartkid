package com.example.smartkid.feature.admin;

import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.ui.FeatureSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed registry of Admin-owned management features and their real API endpoints. */
public final class AdminManagementSpec {
    private static final String NOTIFICATION_HISTORY_ENDPOINT =
            "admin/activity-logs/?action=notification.broadcast&page=1&pageSize=100";
    private static final Map<String, FeatureSpec> SPECS;

    static {
        Map<String, FeatureSpec> specs = new LinkedHashMap<>();
        add(specs, "admin_dashboard", "Bảng điều khiển", "admin/dashboard/", "");
        add(specs, "admin_active_users", "Người dùng đang hoạt động", "admin/dashboard/active-users/", "");
        add(specs, "admin_users", "Quản lý người dùng", "account/admin/users/?page=1&pageSize=100", "admin_users");
        add(specs, "admin_courses", "Khóa học và video", "admin/courses/?page=1&pageSize=100", "admin_courses");
        add(specs, "admin_health", "Sức khỏe hệ thống", "admin/system/health/", "");
        add(specs, "admin_activity", "Nhật ký hoạt động", "admin/activity-logs/", "");
        add(specs, "admin_security", "Bảo mật", "admin/security/policy/", "");
        add(specs, "admin_sessions", "Phiên đăng nhập", "admin/security/sessions/", "admin_sessions");
        add(specs, "admin_config", "Cấu hình hệ thống", "admin/system/config/", "");
        add(specs, "admin_backups", "Sao lưu hệ thống", "admin/system/backups/", "admin_backups");
        add(specs, "admin_report_learning", "Báo cáo học tập", "admin/reports/learning/?type=kpis", "");
        add(specs, "admin_report_content", "Báo cáo nội dung", "admin/reports/content/?type=kpis", "");
        add(specs, "admin_notifications", "Thông báo", "admin/notifications/?limit=100", "");
        SPECS = Collections.unmodifiableMap(specs);
    }

    private AdminManagementSpec() { }

    public static FeatureSpec get(String key) { return SPECS.get(key); }
    public static Map<String, FeatureSpec> all() { return SPECS; }
    public static String notificationHistoryEndpoint() {
        return NOTIFICATION_HISTORY_ENDPOINT;
    }

    public static boolean isRealtimeList(String key) {
        return "admin_active_users".equals(key);
    }

    private static void add(Map<String, FeatureSpec> target, String key, String title,
                            String endpoint, String actionKind) {
        target.put(key, new FeatureSpec(key, title, endpoint, actionKind, "", UserRole.ADMIN));
    }

}
