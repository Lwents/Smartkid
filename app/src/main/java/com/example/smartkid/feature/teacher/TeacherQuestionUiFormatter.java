package com.example.smartkid.feature.teacher;

import com.example.smartkid.common.util.SafeJson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Tạo nhãn dễ đọc cho quy trình Teacher xử lý câu hỏi bài học. */
final class TeacherQuestionUiFormatter {
    private TeacherQuestionUiFormatter() { }

    static String studentName(JSONObject source) {
        String name = SafeJson.string(source, "", "student_name", "studentName", "student");
        return name.isEmpty() ? "Học sinh" : name;
    }

    static String studentInitial(JSONObject source) {
        String name = studentName(source).trim();
        if (name.isEmpty()) return "HS";
        return name.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    static int teacherReplyCount(JSONArray replies) {
        if (replies == null) return 0;
        int count = 0;
        for (int index = 0; index < replies.length(); index++) {
            JSONObject reply = replies.optJSONObject(index);
            if (reply != null && SafeJson.bool(reply, false, "is_teacher")) count++;
        }
        return count;
    }

    static String statusLabel(JSONObject source) {
        int count = teacherReplyCount(SafeJson.array(source, "replies"));
        if (count == 0) return "Chưa trả lời";
        return count == 1 ? "Đã trả lời" : "Đã trả lời " + count + " lần";
    }

    static String contextLabel(JSONObject source) {
        String course = SafeJson.string(source, "", "course_title");
        String lesson = SafeJson.string(source, "", "lesson_title");
        if (!course.isEmpty() && !lesson.isEmpty()) return course + "\n" + lesson;
        return course.isEmpty() ? lesson : course;
    }

    static String timeLabel(JSONObject source) {
        return timeLabel(SafeJson.string(source, "", "created_at", "createdAt"));
    }

    static String timeLabel(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String value = raw.trim();
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = parser.parse(value.substring(0, Math.min(19, value.length())));
            if (parsed == null) return "";
            long elapsed = Math.max(0, System.currentTimeMillis() - parsed.getTime());
            long minute = 60_000L;
            long hour = 60L * minute;
            long day = 24L * hour;
            if (elapsed < minute) return "Vừa xong";
            if (elapsed < hour) return (elapsed / minute) + " phút trước";
            if (elapsed < day) return (elapsed / hour) + " giờ trước";
            if (elapsed < 2L * day) return "Hôm qua";
            SimpleDateFormat printer = new SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.US);
            printer.setTimeZone(TimeZone.getDefault());
            return printer.format(parsed);
        } catch (Exception ignored) {
            return "";
        }
    }
}
