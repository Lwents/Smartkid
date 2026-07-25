package com.example.smartkid.feature.teacher;

import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.ui.FeatureSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed registry of Teacher-owned management features and their real API endpoints. */
public final class TeacherManagementSpec {
    private static final Map<String, FeatureSpec> SPECS;

    static {
        Map<String, FeatureSpec> specs = new LinkedHashMap<>();
        add(specs, "teacher_dashboard", "Bảng điều khiển", "teacher/dashboard/", "");
        add(specs, "teacher_qa", "Hỏi đáp bài học", "teacher/lesson-questions/", "teacher_qa");
        add(specs, "teacher_courses", "Khóa học", "content/courses/?page=1&pageSize=100", "teacher_courses");
        add(specs, "teacher_exams", "Bài kiểm tra", "activities/exercises/?page=1&pageSize=100&include_stats=true", "teacher_exams");
        add(specs, "teacher_exam_reports", "Báo cáo bài kiểm tra", "activities/exercises/?page=1&pageSize=100&include_stats=true", "teacher_exam_reports");
        add(specs, "teacher_students", "Học viên", "teacher/students/?page=1&pageSize=100", "teacher_students");
        add(specs, "teacher_progress", "Tiến độ học viên", "teacher/students/?page=1&pageSize=100", "");
        add(specs, "teacher_feedback", "Phản hồi học viên", "teacher/students/feedback/list/?page=1&pageSize=100", "");
        add(specs, "teacher_notifications", "Thông báo", "teacher/notifications/?limit=100", "");
        SPECS = Collections.unmodifiableMap(specs);
    }

    private TeacherManagementSpec() { }

    public static FeatureSpec get(String key) { return SPECS.get(key); }
    public static Map<String, FeatureSpec> all() { return SPECS; }

    private static void add(Map<String, FeatureSpec> target, String key, String title,
                            String endpoint, String actionKind) {
        target.put(key, new FeatureSpec(key, title, endpoint, actionKind, "", UserRole.TEACHER));
    }
}
