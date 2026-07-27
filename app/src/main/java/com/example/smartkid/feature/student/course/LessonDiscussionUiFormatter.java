package com.example.smartkid.feature.student.course;

import com.example.smartkid.common.util.SafeJson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Converts discussion API fields into short labels suitable for young students. */
final class LessonDiscussionUiFormatter {
    private LessonDiscussionUiFormatter() { }

    static String questionAuthor(JSONObject source) {
        return SafeJson.bool(source, false, "is_owner")
                ? "Câu hỏi của em" : "Câu hỏi của bạn học";
    }

    static String questionStatus(JSONObject source) {
        int teacherReplies = teacherReplyCount(SafeJson.array(source, "replies"));
        if (teacherReplies > 0) return "Thầy cô đã trả lời";
        int replies = SafeJson.array(source, "replies").length();
        return replies > 0 ? "Đang cùng trao đổi" : "Đang chờ thầy cô";
    }

    static String repliesTitle(JSONArray replies) {
        int teacherReplies = teacherReplyCount(replies);
        if (teacherReplies == 0) return "Phần trả lời";
        return teacherReplies == 1 ? "Thầy cô đã trả lời" : "Thầy cô đã trả lời "
                + teacherReplies + " lần";
    }

    static String replyAuthor(JSONObject reply) {
        if (SafeJson.bool(reply, false, "is_teacher")) return "Thầy/Cô";
        if (SafeJson.bool(reply, false, "is_owner")) return "Em";
        return "Bạn học";
    }

    static boolean isTeacherReply(JSONObject reply) {
        return SafeJson.bool(reply, false, "is_teacher");
    }

    static int teacherReplyCount(JSONArray replies) {
        if (replies == null) return 0;
        int count = 0;
        for (int index = 0; index < replies.length(); index++) {
            JSONObject reply = replies.optJSONObject(index);
            if (reply != null && isTeacherReply(reply)) count++;
        }
        return count;
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
