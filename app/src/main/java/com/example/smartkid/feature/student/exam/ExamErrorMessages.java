package com.example.smartkid.feature.student.exam;

import com.example.smartkid.data.remote.ApiError;

import java.util.Locale;

final class ExamErrorMessages {
    private static final String ATTEMPT_LIMIT_MESSAGE =
            "Em đã dùng hết số lượt làm bài. Hãy xem lại kết quả và bảng xếp hạng nhé!";

    private ExamErrorMessages() {
    }

    static boolean isAttemptLimit(ApiError error) {
        if (error == null) return false;
        String message = error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("hết số lượt làm bài")
                || message.contains("giới hạn số lần làm bài")
                || message.contains("attempt limit")
                || message.contains("maximum number of attempts");
    }

    static String studentFriendlyMessage(ApiError error) {
        if (error == null) return "Có lỗi xảy ra, em hãy thử lại nhé!";
        if (isAttemptLimit(error)) return ATTEMPT_LIMIT_MESSAGE;

        return error.getMessage()
                .replaceAll("(?i)\\s*Attempt\\s*ID\\s*:\\s*[0-9a-f-]+", "")
                .trim();
    }
}
