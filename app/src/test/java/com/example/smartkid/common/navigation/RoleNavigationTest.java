package com.example.smartkid.common.navigation;

import static org.junit.Assert.assertEquals;

import com.example.smartkid.feature.admin.AdminDashboardActivity;
import com.example.smartkid.feature.student.shell.StudentHomeActivity;
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
        assertEquals(TeacherDashboardActivity.class,
                RoleNavigation.destinationForRole("instructor"));
    }

    @Test
    public void studentUsesNativeHome() {
        assertEquals(StudentHomeActivity.class, RoleNavigation.destinationForRole("student"));
    }

    @Test
    public void unknownRolesNeverFallThroughToStudentHome() {
        assertEquals(UnsupportedRoleActivity.class,
                RoleNavigation.destinationForRole((String) null));
        assertEquals(UnsupportedRoleActivity.class, RoleNavigation.destinationForRole(""));
        assertEquals(UnsupportedRoleActivity.class,
                RoleNavigation.destinationForRole("parent"));
    }

    @Test
    public void roleMappingIsCaseInsensitiveAndTrimmed() {
        assertEquals(UserRole.ADMIN, UserRole.fromString(" Admin "));
        assertEquals(UserRole.TEACHER, UserRole.fromString("INSTRUCTOR"));
        assertEquals(UserRole.STUDENT, UserRole.fromString("Student"));
        assertEquals(UserRole.UNKNOWN, UserRole.fromString(""));
        assertEquals(UserRole.UNKNOWN, UserRole.fromString(null));
    }
}
