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
        String tooShort = valueWithLength(7);
        String valid = valueWithLength(8);
        String different = valueWithLength(9);

        assertEquals(AdminUserActions.PasswordIssue.REQUIRED,
                AdminUserActions.validatePassword("", ""));
        assertEquals(AdminUserActions.PasswordIssue.TOO_SHORT,
                AdminUserActions.validatePassword(tooShort, tooShort));
        assertEquals(AdminUserActions.PasswordIssue.MISMATCH,
                AdminUserActions.validatePassword(valid, different));
        assertEquals(AdminUserActions.PasswordIssue.NONE,
                AdminUserActions.validatePassword(valid, valid));
    }

    private static String valueWithLength(int length) {
        return new String(new char[length]).replace('\0', 'x');
    }
}
