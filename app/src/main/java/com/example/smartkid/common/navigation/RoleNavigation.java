package com.example.smartkid.common.navigation;

import android.content.Context;

import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.feature.admin.AdminDashboardActivity;
import com.example.smartkid.feature.student.shell.StudentHomeActivity;
import com.example.smartkid.feature.teacher.TeacherDashboardActivity;

/** Quyết định cổng giao diện theo role đã được backend ký trong JWT/login response. */
public final class RoleNavigation {
    private RoleNavigation() { }

    public static Class<?> destination(Context context) {
        String role = new SessionManager(context).getUser().getRole();
        return destinationForRole(role);
    }

    public static Class<?> destinationForRole(String role) {
        return destinationForRole(UserRole.fromString(role));
    }

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
                // An unrecognised role must never fall through to the Student home.
                return UnsupportedRoleActivity.class;
        }
    }
}
