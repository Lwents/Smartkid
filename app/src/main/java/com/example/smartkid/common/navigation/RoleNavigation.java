package com.example.smartkid.common.navigation;

import android.content.Context;

import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.feature.admin.AdminDashboardActivity;
import com.example.smartkid.feature.student.shell.StudentHomeActivity;
import com.example.smartkid.feature.teacher.TeacherDashboardActivity;

/** Quyết định cổng giao diện theo role đã được backend ký trong JWT/login response. */
public final class RoleNavigation {
    private RoleNavigation() { }

    /** Màn hình chính ứng với vai trò của phiên đang đăng nhập. */
    public static Class<?> destination(Context context) {
        String role = new SessionManager(context).getUser().getRole();
        return destinationForRole(role);
    }

    /** Ánh xạ từ chuỗi vai trò thô do server trả về. */
    public static Class<?> destinationForRole(String role) {
        return destinationForRole(UserRole.fromString(role));
    }

    /** Ánh xạ chính: admin/giáo viên/học sinh, vai trò lạ thì mở màn thông báo. */
    public static Class<?> destinationForRole(UserRole role) {
        switch (role) {
            case ADMIN:
                return AdminDashboardActivity.class;
            case TEACHER:
                return TeacherDashboardActivity.class;
            case STUDENT:
                return StudentHomeActivity.class;
            case UNKNOWN:
            default:
                // Role không nhận diện được tuyệt đối không được rơi mặc định vào màn Student.
                return UnsupportedRoleActivity.class;
        }
    }
}
