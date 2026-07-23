package com.example.smartkid.core;

import android.content.Context;

import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.ui.home.HomeActivity;
import com.example.smartkid.ui.management.RolePortalActivity;

/** Quyết định cổng giao diện theo role đã được backend ký trong JWT/login response. */
public final class RoleNavigation {
    private RoleNavigation() { }

    public static Class<?> destination(Context context) {
        String role = new SessionManager(context).getUser().getRole();
        return "student".equalsIgnoreCase(role) ? HomeActivity.class : RolePortalActivity.class;
    }
}
