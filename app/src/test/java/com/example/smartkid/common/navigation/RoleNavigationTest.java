package com.example.smartkid.common.navigation;

import static org.junit.Assert.assertEquals;

import com.example.smartkid.feature.admin.AdminDashboardActivity;
import com.example.smartkid.feature.home.HomeActivity;
import com.example.smartkid.feature.teacher.TeacherDashboardActivity;

import org.junit.Test;

public class RoleNavigationTest {

    @Test
    public void adminUsesNativeDashboard() {
        assertEquals(AdminDashboardActivity.class, RoleNavigation.destinationForRole("admin"));
    }

    @Test
    public void teacherRolesUseNativeDashboard() {
        assertEquals(TeacherDashboardActivity.class, RoleNavigation.destinationForRole("teacher"));
        assertEquals(TeacherDashboardActivity.class, RoleNavigation.destinationForRole("instructor"));
    }

    @Test
    public void studentAndUnknownRolesUseNativeHome() {
        assertEquals(HomeActivity.class, RoleNavigation.destinationForRole("student"));
        assertEquals(HomeActivity.class, RoleNavigation.destinationForRole(null));
    }
}
