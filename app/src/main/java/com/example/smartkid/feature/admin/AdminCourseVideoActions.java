package com.example.smartkid.feature.admin;

/** API paths and small rules for the admin course-video workflow. */
public final class AdminCourseVideoActions {
    private AdminCourseVideoActions() {
    }

    public static String courseDetailEndpoint(String courseId) {
        return "admin/courses/" + requiredId(courseId) + "/";
    }

    public static String deleteCourseEndpoint(String courseId) {
        return courseDetailEndpoint(courseId);
    }

    public static String deleteVideoEndpoint(String courseId, String lessonId) {
        return "admin/courses/" + requiredId(courseId)
                + "/lessons/" + requiredId(lessonId) + "/video/";
    }

    public static boolean canManageVideo(boolean hasVideo, String lessonId) {
        return hasVideo && lessonId != null && !lessonId.trim().isEmpty();
    }

    private static String requiredId(String value) {
        String id = value == null ? "" : value.trim();
        if (id.isEmpty()) throw new IllegalArgumentException("Thiếu mã dữ liệu");
        return id;
    }
}
