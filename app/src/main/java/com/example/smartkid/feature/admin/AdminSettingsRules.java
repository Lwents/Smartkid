package com.example.smartkid.feature.admin;

import java.util.regex.Pattern;

/** Các quy tắc kiểm tra và chọn chế độ dùng chung cho form cài đặt Admin. */
public final class AdminSettingsRules {
    public static final String MODE_SECURITY = "admin_security";
    public static final String MODE_SYSTEM = "admin_config";

    private static final Pattern TIME = Pattern.compile("(?:[01]\\d|2[0-3]):[0-5]\\d");
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERNAL_EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9-]+$",
            Pattern.CASE_INSENSITIVE);

    private AdminSettingsRules() { }

    /** Kiểm tra mode có phải màn bảo mật hoặc cấu hình hệ thống được hỗ trợ. */
    public static boolean supports(String mode) {
        return MODE_SECURITY.equals(mode) || MODE_SYSTEM.equals(mode);
    }

    /** Đọc số nguyên trong khoảng; trả null nếu sai định dạng hoặc vượt giới hạn. */
    public static Integer boundedInteger(String value, int min, int max) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Kiểm tra thời gian theo định dạng 24 giờ HH:mm. */
    public static boolean validTime(String value) {
        return value != null && TIME.matcher(value.trim()).matches();
    }

    /** Cho phép email trống, email Internet hợp lệ hoặc địa chỉ nội bộ dạng user@host. */
    public static boolean validOptionalEmail(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() || EMAIL.matcher(normalized).matches()
                || INTERNAL_EMAIL.matcher(normalized).matches();
    }
}
