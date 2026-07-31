package com.example.smartkid.data.repository;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.TimeZone;

public class ManagementRepositoryActiveUserTest {
    @Test
    public void activeStudentFields_becomeReadableListItem() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try {
            ManagementRepository.ActiveUserPresentation item =
                    ManagementRepository.activeUserPresentation(
                            "Nguyễn An",
                            "Học sinh",
                            "an@example.com",
                            "2026-07-30T10:15:00+00:00");

            assertEquals("Nguyễn An", item.title);
            assertEquals("Học sinh • an@example.com", item.subtitle);
            assertEquals("Hoạt động: 30/07/2026 17:15", item.detail);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void activeTeacherWithoutEmail_hasNoDanglingSeparator() {
        ManagementRepository.ActiveUserPresentation item =
                ManagementRepository.activeUserPresentation(
                        "Cô Bình", "Giáo viên", "", "gần đây");

        assertEquals("Cô Bình", item.title);
        assertEquals("Giáo viên", item.subtitle);
        assertEquals("Hoạt động: gần đây", item.detail);
    }
}
