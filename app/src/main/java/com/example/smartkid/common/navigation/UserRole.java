package com.example.smartkid.common.navigation;

import java.util.Locale;

/** Vai trò người dùng đã chuẩn hóa từ chuỗi role do backend cấp. */
public enum UserRole {
    STUDENT,
    TEACHER,
    ADMIN,
    UNKNOWN;

    /**
     * Ánh xạ chuỗi role thô sang {@link UserRole}.
     *
     * <ul>
     *   <li>{@code student} → {@link #STUDENT}</li>
     *   <li>{@code teacher}, {@code instructor} → {@link #TEACHER}</li>
     *   <li>{@code admin} → {@link #ADMIN}</li>
     *   <li>null, rỗng hoặc giá trị khác → {@link #UNKNOWN}</li>
     * </ul>
     */
    public static UserRole fromString(String role) {
        if (role == null) return UNKNOWN;
        switch (role.trim().toLowerCase(Locale.ROOT)) {
            case "student":
                return STUDENT;
            case "teacher":
            case "instructor":
                return TEACHER;
            case "admin":
                return ADMIN;
            default:
                return UNKNOWN;
        }
    }

    public boolean isTeacher() {
        return this == TEACHER;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public boolean isStudent() {
        return this == STUDENT;
    }
}
