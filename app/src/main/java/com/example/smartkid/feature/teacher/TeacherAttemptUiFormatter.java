package com.example.smartkid.feature.teacher;

import com.example.smartkid.common.util.SafeJson;

import org.json.JSONObject;

import java.util.Locale;

/** Chuyển dữ liệu attempt kỹ thuật thành nội dung dễ đọc cho giáo viên. */
final class TeacherAttemptUiFormatter {
    private TeacherAttemptUiFormatter() { }

    static boolean isSubmission(JSONObject source) {
        String status = SafeJson.string(source, "", "status", "state").toLowerCase(Locale.ROOT);
        return status.isEmpty() || "submitted".equals(status) || "completed".equals(status)
                || "finalized".equals(status) || "graded".equals(status);
    }

    static String studentKey(JSONObject source) {
        JSONObject student = nestedStudent(source);
        String value = SafeJson.string(source, "", "student_id", "studentId", "user_id", "userId",
                "learner_id", "learnerId");
        if (value.isEmpty()) {
            value = SafeJson.string(student, "", "id", "uuid", "user_id", "userId", "email",
                    "username");
        }
        return value;
    }

    static String studentName(JSONObject source, int position) {
        String name = SafeJson.string(source, "", "student_name", "studentName", "full_name",
                "fullName", "learner_name", "learnerName", "username", "email");
        JSONObject student = nestedStudent(source);
        if (name.isEmpty()) {
            name = SafeJson.string(student, "", "full_name", "fullName", "display_name",
                    "displayName", "name", "username", "email");
        }
        if (isTechnicalId(name)) name = "";
        return name.isEmpty() ? "Học sinh " + position : name;
    }

    static String detail(JSONObject source) {
        StringBuilder result = new StringBuilder(statusLabel(source));
        double score = SafeJson.decimal(source, Double.NaN, "score", "total_score", "totalScore",
                "percentage", "percent");
        if (!Double.isNaN(score)) {
            result.append(" • ").append(formatNumber(score)).append(" điểm");
        }
        String submittedAt = SafeJson.string(source, "", "submitted_at", "submittedAt",
                "completed_at", "completedAt", "updated_at", "updatedAt");
        String time = TeacherQuestionUiFormatter.timeLabel(submittedAt);
        if (!time.isEmpty()) result.append(" • ").append(time);
        return result.toString();
    }

    private static JSONObject nestedStudent(JSONObject source) {
        if (source == null) return null;
        JSONObject value = source.optJSONObject("student");
        if (value == null) value = source.optJSONObject("user");
        if (value == null) value = source.optJSONObject("learner");
        if (value == null) value = source.optJSONObject("profile");
        return value;
    }

    private static String statusLabel(JSONObject source) {
        String status = SafeJson.string(source, "submitted", "status", "state")
                .toLowerCase(Locale.ROOT);
        switch (status) {
            case "graded": return "Đã chấm";
            case "completed":
            case "finalized":
            case "submitted": return "Đã nộp";
            default: return "Đã ghi nhận";
        }
    }

    private static boolean isTechnicalId(String value) {
        if (value == null) return false;
        return value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || value.matches("[0-9]{8,}");
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value)
                : String.format(Locale.US, "%.1f", value);
    }
}
