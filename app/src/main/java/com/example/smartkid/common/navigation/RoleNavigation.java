package com.example.smartkid.common.navigation;

import android.content.Context;

import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.feature.admin.AdminFlutterActivity;
import com.example.smartkid.feature.home.HomeActivity;
import com.example.smartkid.feature.teacher.TeacherDashboardActivity;

/** Quyết định cổng giao diện theo role đã được backend ký trong JWT/login response. */
public final class RoleNavigation {
    private RoleNavigation() { }

    public static Class<?> destination(Context context) {
        String role = new SessionManager(context).getUser().getRole();
        if ("admin".equalsIgnoreCase(role)) return AdminFlutterActivity.class;
        if ("teacher".equalsIgnoreCase(role) || "instructor".equalsIgnoreCase(role)) {
            return TeacherDashboardActivity.class;
        }
        return HomeActivity.class;
    }
}
