package com.example.smartkid.common.ui.form;

import com.example.smartkid.common.navigation.UserRole;

/**
 * Enum có kiểu rõ ràng thay cho chuỗi {@code kind} cũ từng điều khiển form tạo dùng chung.
 * Mỗi giá trị chỉ thuộc đúng một role, nên một màn hình không còn phục vụ lẫn Admin và Teacher
 * bằng cách rẽ nhánh trên chuỗi.
 */
public enum ContentFormKind {
    ADMIN_USER(UserRole.ADMIN),
    TEACHER_COURSE(UserRole.TEACHER),
    TEACHER_MODULE(UserRole.TEACHER),
    TEACHER_LESSON(UserRole.TEACHER),
    TEACHER_EXERCISE(UserRole.TEACHER),
    TEACHER_EXAM(UserRole.TEACHER);

    private final UserRole owner;

    ContentFormKind(UserRole owner) {
        this.owner = owner;
    }

    public UserRole owner() {
        return owner;
    }

    public boolean isAllowedFor(UserRole role) {
        return role == owner;
    }
}
