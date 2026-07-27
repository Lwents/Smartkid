package com.example.smartkid.feature.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdminUserActionsTest {
    @Test
    public void buildsUserManagementEndpoints() {
        assertEquals("account/admin/users/42/", AdminUserActions.userEndpoint(" 42 "));
        assertEquals("account/admin/password/set/42/",
                AdminUserActions.passwordEndpoint("42"));
        assertThrows(IllegalArgumentException.class,
                () -> AdminUserActions.passwordEndpoint(" "));
    }

    @Test
    public void acceptsOnlyRealApplicationRoles() {
        assertTrue(AdminUserActions.isSupportedRole("student"));
        assertTrue(AdminUserActions.isSupportedRole("instructor"));
        assertTrue(AdminUserActions.isSupportedRole("admin"));
        assertFalse(AdminUserActions.isSupportedRole("teacher"));
    }

    @Test
    public void validatesPasswordAndConfirmation() {
        assertEquals(AdminUserActions.PasswordIssue.REQUIRED,
                AdminUserActions.validatePassword("", ""));
        assertEquals(AdminUserActions.PasswordIssue.TOO_SHORT,
                AdminUserActions.validatePassword("Abc123", "Abc123"));
        assertEquals(AdminUserActions.PasswordIssue.MISMATCH,
                AdminUserActions.validatePassword("Secure123", "Secure124"));
        assertEquals(AdminUserActions.PasswordIssue.NONE,
                AdminUserActions.validatePassword("Secure123", "Secure123"));
    }
}
