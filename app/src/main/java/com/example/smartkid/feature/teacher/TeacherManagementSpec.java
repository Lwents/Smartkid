package com.example.smartkid.feature.teacher;

import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.ui.FeatureSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Danh bạ chức năng Teacher cùng endpoint thật và loại hành động của từng mục. */
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
        add(specs, "teacher_feedback", "Phản hồi đã gửi", "teacher/students/feedback/", "teacher_feedback");
        add(specs, "teacher_notifications", "Thông báo", "teacher/notifications/?limit=100", "");
        SPECS = Collections.unmodifiableMap(specs);
    }

    private TeacherManagementSpec() { }

    /** Lấy cấu hình một chức năng Teacher theo key truyền qua Intent. */
    public static FeatureSpec get(String key) { return SPECS.get(key); }
    /** Trả toàn bộ danh bạ bất biến để dashboard có thể duyệt an toàn. */
    public static Map<String, FeatureSpec> all() { return SPECS; }

    /** Thêm một chức năng thuộc quyền Teacher vào danh bạ khởi tạo. */
    private static void add(Map<String, FeatureSpec> target, String key, String title,
                            String endpoint, String actionKind) {
        target.put(key, new FeatureSpec(key, title, endpoint, actionKind, "", UserRole.TEACHER));
    }
}
