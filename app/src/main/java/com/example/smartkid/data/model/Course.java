package com.example.smartkid.data.model;

import com.example.smartkid.domain.BusinessRules;

public class Course {
    private final String id;
    private final String title;
    private final String grade;
    private final String subject;
    private final String teacherName;
    private final int lessonsCount;
    private final int progress;
    private final double price;
    private final String thumbnailUrl;
    private final String description;
    private final boolean enrolled;

    public Course(String id, String title, String grade, String subject, String teacherName,
                  int lessonsCount, int progress, double price, String thumbnailUrl,
                  String description, boolean enrolled) {
        this.id = safe(id);
        this.title = safe(title).isEmpty() ? "Khóa học chưa đặt tên" : safe(title);
        this.grade = safe(grade);
        this.subject = safe(subject);
        this.teacherName = safe(teacherName);
        this.lessonsCount = Math.max(0, lessonsCount);
        this.progress = BusinessRules.clampProgress(progress);
        this.price = Math.max(0, price);
        this.thumbnailUrl = safe(thumbnailUrl);
        this.description = safe(description);
        this.enrolled = enrolled;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getGrade() {
        return grade;
    }

    public String getSubject() {
        return subject;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public int getLessonsCount() {
        return lessonsCount;
    }

    public int getProgress() {
        return progress;
    }

    public double getPrice() {
        return price;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnrolled() {
        return enrolled;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
