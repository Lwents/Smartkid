package com.example.smartkid.feature.teacher.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable data rendered by the native teacher dashboard. */
public final class TeacherDashboardData {
    private final int courseCount;
    private final int studentCount;
    private final int lessonCount;
    private final int examCount;
    private final int attemptCount;
    private final int coursePublishedRate;
    private final int studentActiveRate;
    private final int lessonPublishedRate;
    private final int examPublishedRate;
    private final int attemptSubmittedRate;
    private final int completionRate;
    private final List<CourseItem> courses;

    public TeacherDashboardData(int courseCount, int studentCount, int lessonCount,
                                int examCount, int attemptCount,
                                int coursePublishedRate, int studentActiveRate,
                                int lessonPublishedRate, int examPublishedRate,
                                int attemptSubmittedRate, int completionRate,
                                List<CourseItem> courses) {
        this.courseCount = courseCount;
        this.studentCount = studentCount;
        this.lessonCount = lessonCount;
        this.examCount = examCount;
        this.attemptCount = attemptCount;
        this.coursePublishedRate = percentage(coursePublishedRate);
        this.studentActiveRate = percentage(studentActiveRate);
        this.lessonPublishedRate = percentage(lessonPublishedRate);
        this.examPublishedRate = percentage(examPublishedRate);
        this.attemptSubmittedRate = percentage(attemptSubmittedRate);
        this.completionRate = percentage(completionRate);
        this.courses = Collections.unmodifiableList(courses == null
                ? new ArrayList<>() : new ArrayList<>(courses));
    }

    public int getCourseCount() { return courseCount; }
    public int getStudentCount() { return studentCount; }
    public int getLessonCount() { return lessonCount; }
    public int getExamCount() { return examCount; }
    public int getAttemptCount() { return attemptCount; }
    public int getCoursePublishedRate() { return coursePublishedRate; }
    public int getStudentActiveRate() { return studentActiveRate; }
    public int getLessonPublishedRate() { return lessonPublishedRate; }
    public int getExamPublishedRate() { return examPublishedRate; }
    public int getAttemptSubmittedRate() { return attemptSubmittedRate; }
    public int getCompletionRate() { return completionRate; }
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

    private static int percentage(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
