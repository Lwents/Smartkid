package com.example.smartkid.feature.admin;

/** API paths and small rules for the admin course-video workflow. */
public final class AdminCourseVideoActions {
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_PUBLISH = "publish";
    public static final String ACTION_UNPUBLISH = "unpublish";
    public static final String ACTION_ARCHIVE = "archive";
    public static final String ACTION_RESTORE = "restore";

    private AdminCourseVideoActions() {
    }

    public static String courseDetailEndpoint(String courseId) {
        return "admin/courses/" + requiredId(courseId) + "/";
    }

    public static String deleteVideoEndpoint(String courseId, String lessonId) {
        return "admin/courses/" + requiredId(courseId)
                + "/lessons/" + requiredId(lessonId) + "/video/";
    }

    public static String courseActionEndpoint(String courseId, String action) {
        String normalizedAction = action == null ? "" : action.trim();
        if (!isCourseAction(normalizedAction)) {
            throw new IllegalArgumentException("Thao tác khóa học không hợp lệ");
        }
        return "admin/courses/" + requiredId(courseId) + "/" + normalizedAction + "/";
    }

    public static boolean isCourseAction(String action) {
        return ACTION_APPROVE.equals(action)
                || ACTION_REJECT.equals(action)
                || ACTION_PUBLISH.equals(action)
                || ACTION_UNPUBLISH.equals(action)
                || ACTION_ARCHIVE.equals(action)
                || ACTION_RESTORE.equals(action);
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
