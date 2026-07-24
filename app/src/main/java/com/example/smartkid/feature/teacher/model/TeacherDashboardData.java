package com.example.smartkid.feature.teacher.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable data rendered by the native teacher dashboard. */
public final class TeacherDashboardData {
    private final int courseCount;
    private final int studentCount;
    private final int lessonCount;
    private final List<CourseItem> courses;

    public TeacherDashboardData(int courseCount, int studentCount, int lessonCount,
                                List<CourseItem> courses) {
        this.courseCount = courseCount;
        this.studentCount = studentCount;
        this.lessonCount = lessonCount;
        this.courses = Collections.unmodifiableList(courses == null
                ? new ArrayList<>() : new ArrayList<>(courses));
    }

    public int getCourseCount() { return courseCount; }
    public int getStudentCount() { return studentCount; }
    public int getLessonCount() { return lessonCount; }
    public List<CourseItem> getCourses() { return courses; }

    public static final class CourseItem {
        private final String id;
        private final String title;
        private final int enrolled;
        private final int lessons;
        private final String status;

        public CourseItem(String id, String title, int enrolled, int lessons, String status) {
            this.id = safe(id);
            this.title = safe(title);
            this.enrolled = enrolled;
            this.lessons = lessons;
            this.status = safe(status);
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public int getEnrolled() { return enrolled; }
        public int getLessons() { return lessons; }
        public String getStatus() { return status; }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
