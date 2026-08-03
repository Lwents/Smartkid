package com.example.smartkid.feature.admin;

/** Tập hợp endpoint và quy tắc kiểm tra cho các thao tác quản lý người dùng của Admin. */
public final class AdminUserActions {
    public static final String ROLE_STUDENT = "student";
    public static final String ROLE_INSTRUCTOR = "instructor";
    public static final String ROLE_ADMIN = "admin";

    private AdminUserActions() {
    }

    public static String userEndpoint(String userId) {
        return "account/admin/users/" + requiredId(userId) + "/";
    }

    public static String passwordEndpoint(String userId) {
        return "account/admin/password/set/" + requiredId(userId) + "/";
    }

    public static boolean isSupportedRole(String role) {
        return ROLE_STUDENT.equals(role)
                || ROLE_INSTRUCTOR.equals(role)
                || ROLE_ADMIN.equals(role);
    }

    public static PasswordIssue validatePassword(String password, String confirmation) {
        String value = password == null ? "" : password;
        String repeated = confirmation == null ? "" : confirmation;
        if (value.trim().isEmpty()) return PasswordIssue.REQUIRED;
        if (value.length() < 8) return PasswordIssue.TOO_SHORT;
        if (!value.equals(repeated)) return PasswordIssue.MISMATCH;
        return PasswordIssue.NONE;
    }

    private static String requiredId(String value) {
        String id = value == null ? "" : value.trim();
        if (id.isEmpty()) throw new IllegalArgumentException("Thiếu mã người dùng");
        return id;
    }

    public enum PasswordIssue {
        NONE,
        REQUIRED,
        TOO_SHORT,
        MISMATCH
    }
}
