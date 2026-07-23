package com.example.smartkid.data.model;

public class DashboardSummary {
    private final Course resumeCourse;
    private final int featuredCourseCount;
    private final int previewExamCount;

    public DashboardSummary(Course resumeCourse, int featuredCourseCount, int previewExamCount) {
        this.resumeCourse = resumeCourse;
        this.featuredCourseCount = Math.max(0, featuredCourseCount);
        this.previewExamCount = Math.max(0, previewExamCount);
    }

    public Course getResumeCourse() {
        return resumeCourse;
    }

    public int getFeaturedCourseCount() {
        return featuredCourseCount;
    }

    public int getPreviewExamCount() {
        return previewExamCount;
    }
}
