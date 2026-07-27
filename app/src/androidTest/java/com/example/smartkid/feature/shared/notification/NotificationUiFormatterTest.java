package com.example.smartkid.feature.shared.notification;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NotificationUiFormatterTest {
    @Test
    public void examNotificationReadsNavigationMetadata() throws Exception {
        JSONObject source = new JSONObject()
                .put("category", "exam")
                .put("metadata", new JSONObject()
                        .put("exam_id", "exam-123")
                        .put("exam_title", "Kiểm tra phép cộng")
                        .put("course_id", "course-456")
                        .put("course_title", "Toán lớp 4"));

        assertEquals("Bài kiểm tra", NotificationUiFormatter.categoryLabel(source));
        assertEquals("exam-123", NotificationUiFormatter.examId(source));
        assertEquals("Kiểm tra phép cộng", NotificationUiFormatter.examTitle(source));
        assertEquals("course-456", NotificationUiFormatter.courseId(source));
        assertEquals("Toán lớp 4\nKiểm tra phép cộng",
                NotificationUiFormatter.contextLabel(source));
    }
}
