package com.example.smartkid.data.model;

/**
 * Người dùng đang đăng nhập: id, tên, email, vai trò, lớp.
 * 
 * Các model trong package này đều bất biến (chỉ có getter) và dùng safe() để đổi null
 * thành chuỗi rỗng, nhờ vậy giao diện không phải kiểm tra null ở mọi chỗ.
 */
public class User {
    private final String id;
    private final String username;
    private final String fullName;
    private final String email;
    private final String role;
    private final String className;

    public User(String id, String username, String fullName, String email,
                String role, String className) {
        this.id = safe(id);
        this.username = safe(username);
        this.fullName = safe(fullName);
        this.email = safe(email);
        this.role = safe(role).isEmpty() ? "student" : safe(role);
        this.className = safe(className);
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName.isEmpty() ? username : fullName;
    }

    public String getEmail() {
        return email;
    }

    /** Vai trò thô từ server ('student', 'instructor', 'admin'); dùng UserRole.fromString để chuẩn hóa. */
    public String getRole() {
        return role;
    }

    /** Lớp đang học của học sinh, dùng để lọc bài kiểm tra theo khối. */
    public String getClassName() {
        return className;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
