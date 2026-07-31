package com.example.smartkid.feature.admin.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AdminDashboardDataTest {
    @Test
    public void unavailableHealthMetrics_areNotReportedAsZeroUsage() {
        AdminDashboardData.SystemHealth health =
                new AdminDashboardData.SystemHealth(-1, -1, -1, "", "");

        assertEquals(-1, health.getCpuPercent());
        assertEquals(-1, health.getRamPercent());
        assertEquals(-1, health.getDiskPercent());
    }

    @Test
    public void healthMetrics_stillClampValuesAboveOneHundred() {
        AdminDashboardData.SystemHealth health =
                new AdminDashboardData.SystemHealth(120, 88, 101, "", "");

        assertEquals(100, health.getCpuPercent());
        assertEquals(88, health.getRamPercent());
        assertEquals(100, health.getDiskPercent());
    }
}
