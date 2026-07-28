package com.example.smartkid.feature.shared.notification;

import com.example.smartkid.common.util.SafeJson;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class NotificationUiFormatter {
    private NotificationUiFormatter() { }

    static boolean isRead(JSONObject source) {
        return SafeJson.bool(source, false, "is_read", "isRead");
    }

    static String categoryLabel(JSONObject source) {
        String category = SafeJson.string(source, "", "category");
        switch (category) {
            case "lesson_question_reply": return "Tin từ thầy cô";
            case "lesson_question": return "Học sinh cần hỗ trợ";
            case "lesson_question_report": return "Báo cáo hỏi đáp";
            case "feedback":
            case "teacher_feedback": return "Lời nhắn từ thầy cô";
            case "course": return "Khóa học";
            case "exam": return "Bài kiểm tra";
            case "achievement": return "Thành tích";
            case "system_health": return "Tình trạng hệ thống";
            case "learning_report": return "Báo cáo học tập";
            case "system": return "Hệ thống";
            default: return "Thông báo học tập";
        }
    }

    static String displayTitle(JSONObject source, String fallback) {
        String category = SafeJson.string(source, "", "category");
        if ("lesson_question_reply".equals(category)) return "Thầy cô đã trả lời em";
        if ("lesson_question".equals(category)) return "Có học sinh vừa hỏi bài";
        if ("system_health".equals(category)) return "Hệ thống cần được kiểm tra";
        if ("learning_report".equals(category)) return "Báo cáo học tập mới";
        return fallback == null || fallback.trim().isEmpty() ? "Thông báo mới" : fallback.trim();
    }

    static String timeLabel(JSONObject source) {
        String raw = SafeJson.string(source, "", "created_at", "createdAt");
        if (raw.isEmpty()) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = parser.parse(raw.substring(0, Math.min(19, raw.length())));
            if (parsed == null) return raw;
            long elapsed = Math.max(0, System.currentTimeMillis() - parsed.getTime());
            long minute = 60_000L;
            long hour = 60 * minute;
            long day = 24 * hour;
            if (elapsed < minute) return "Vừa xong";
            if (elapsed < hour) return (elapsed / minute) + " phút trước";
            if (elapsed < day) return (elapsed / hour) + " giờ trước";
            if (elapsed < 2 * day) return "Hôm qua";
            SimpleDateFormat printer = new SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.US);
            printer.setTimeZone(TimeZone.getDefault());
            return printer.format(parsed);
        } catch (Exception ignored) {
            return raw.replace('T', ' ');
        }
    }

    static String contextLabel(JSONObject source) {
        return contextLabel(source, "");
    }

    static String contextLabel(JSONObject source, String fallbackTitle) {
        JSONObject metadata = source == null ? null : source.optJSONObject("metadata");
        String course = SafeJson.string(metadata, "", "course_title");
        String lesson = SafeJson.string(metadata, "", "lesson_title");
        String exam = SafeJson.string(metadata, "", "exam_title", "exercise_title");
        if (!exam.isEmpty()) {
            return course.isEmpty() ? exam : course + "\n" + exam;
        }
        if (lesson.isEmpty()) lesson = lessonFromLegacyTitle(fallbackTitle);
        if (!course.isEmpty() && !lesson.isEmpty()) return course + "\n" + lesson;
        return course.isEmpty() ? lesson : course;
    }

    private static String lessonFromLegacyTitle(String title) {
        if (title == null) return "";
        String value = title.trim();
        String[] prefixes = {"Giáo viên trả lời:", "Thầy cô đã trả lời:"};
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) return value.substring(prefix.length()).trim();
        }
        return "";
    }

    static String lessonId(JSONObject source) {
        JSONObject metadata = source == null ? null : source.optJSONObject("metadata");
        return SafeJson.string(metadata, "", "lesson_id");
    }

    static String courseId(JSONObject source) {
        JSONObject metadata = source == null ? null : source.optJSONObject("metadata");
        return SafeJson.string(metadata, "", "course_id");
    }

    static String lessonTitle(JSONObject source) {
        JSONObject metadata = source == null ? null : source.optJSONObject("metadata");
        return SafeJson.string(metadata, "", "lesson_title");
    }

    static String examId(JSONObject source) {
        JSONObject metadata = source == null ? null : source.optJSONObject("metadata");
        return SafeJson.string(metadata, "", "exam_id", "exercise_id");
    }

    static String examTitle(JSONObject source) {
        JSONObject metadata = source == null ? null : source.optJSONObject("metadata");
        return SafeJson.string(metadata, "", "exam_title", "exercise_title");
    }
}
