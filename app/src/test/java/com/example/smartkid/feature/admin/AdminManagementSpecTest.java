package com.example.smartkid.feature.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.smartkid.common.ui.FeatureSpec;

import org.junit.Test;

public class AdminManagementSpecTest {
    @Test
    public void sessionsAndBackupsExposeRealManagementActions() {
        FeatureSpec sessions = AdminManagementSpec.get("admin_sessions");
        FeatureSpec backups = AdminManagementSpec.get("admin_backups");
        FeatureSpec courses = AdminManagementSpec.get("admin_courses");

        assertEquals("admin_sessions", sessions.getActionKind());
        assertEquals("admin/security/sessions/", sessions.getEndpoint());
        assertEquals("admin_backups", backups.getActionKind());
        assertEquals("admin/system/backups/", backups.getEndpoint());
        assertEquals("admin_courses", courses.getActionKind());
        assertEquals("admin/courses/?page=1&pageSize=100", courses.getEndpoint());
        assertEquals("admin/activity-logs/?action=notification.broadcast&page=1&pageSize=100",
                AdminManagementSpec.notificationHistoryEndpoint());
        assertTrue(AdminManagementSpec.isRealtimeList("admin_active_users"));
        assertFalse(AdminManagementSpec.isRealtimeList("admin_users"));
    }
}
