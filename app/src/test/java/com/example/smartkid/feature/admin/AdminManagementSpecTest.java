package com.example.smartkid.feature.admin;

import static org.junit.Assert.assertEquals;

import com.example.smartkid.common.ui.FeatureSpec;

import org.junit.Test;

public class AdminManagementSpecTest {
    @Test
    public void sessionsAndBackupsExposeRealManagementActions() {
        FeatureSpec sessions = AdminManagementSpec.get("admin_sessions");
        FeatureSpec backups = AdminManagementSpec.get("admin_backups");

        assertEquals("admin_sessions", sessions.getActionKind());
        assertEquals("admin/security/sessions/", sessions.getEndpoint());
        assertEquals("admin_backups", backups.getActionKind());
        assertEquals("admin/system/backups/", backups.getEndpoint());
    }
}
