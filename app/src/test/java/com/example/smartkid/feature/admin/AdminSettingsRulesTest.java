package com.example.smartkid.feature.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdminSettingsRulesTest {
    @Test
    public void routesOnlyEditableAdminSettings() {
        assertTrue(AdminSettingsRules.supports(AdminSettingsRules.MODE_SECURITY));
        assertTrue(AdminSettingsRules.supports(AdminSettingsRules.MODE_SYSTEM));
        assertFalse(AdminSettingsRules.supports("admin_health"));
    }

    @Test
    public void parsesOnlyIntegersInsideTheAllowedRange() {
        assertEquals(Integer.valueOf(30), AdminSettingsRules.boundedInteger("30", 1, 60));
        assertNull(AdminSettingsRules.boundedInteger("0", 1, 60));
        assertNull(AdminSettingsRules.boundedInteger("61", 1, 60));
        assertNull(AdminSettingsRules.boundedInteger("3.5", 1, 60));
    }

    @Test
    public void validatesTimeAndOptionalEmailForTheForm() {
        assertTrue(AdminSettingsRules.validTime("01:30"));
        assertTrue(AdminSettingsRules.validTime("23:59"));
        assertFalse(AdminSettingsRules.validTime("25:00"));
        assertTrue(AdminSettingsRules.validOptionalEmail(""));
        assertTrue(AdminSettingsRules.validOptionalEmail("support@smartkid.vn"));
        assertTrue(AdminSettingsRules.validOptionalEmail("webmaster@localhost"));
        assertTrue(AdminSettingsRules.validOptionalEmail("no-reply@smartedu"));
        assertFalse(AdminSettingsRules.validOptionalEmail("support@"));
    }
}
