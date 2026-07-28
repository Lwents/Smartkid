package com.example.smartkid.feature.student.exam;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.TimeZone;

public class ExamTimingTest {
    @Test
    public void remainingSecondsUsesServerDeadlineWithMicroseconds() {
        assertEquals(480, ExamTiming.remainingSeconds(
                "2026-07-28T12:10:00.123456+00:00",
                1785240120123L,
                600));
    }

    @Test
    public void invalidDeadlineFallsBackToConfiguredDuration() {
        assertEquals(600, ExamTiming.remainingSeconds("invalid", 0, 600));
    }

    @Test
    public void expiredDeadlineTriggersImmediateCountdownFinish() {
        assertEquals(1, ExamTiming.remainingSeconds(
                "2026-07-28T12:00:00Z",
                1785240060000L,
                600));
    }

    @Test
    public void formatsExamDeadlineInVietnamTime() {
        assertEquals("15:30 28/07/2026", ExamTiming.formatLocalDateTime(
                "2026-07-28T08:30:00Z",
                TimeZone.getTimeZone("Asia/Ho_Chi_Minh")));
    }
}
