package com.example.smartkid.feature.student.exam;

import com.example.smartkid.data.remote.ApiError;

import java.util.Locale;

/** Chuẩn hóa lỗi bài thi thành thông báo ngắn, thân thiện với học viên. */
final class ExamErrorMessages {
    private static final String ATTEMPT_LIMIT_MESSAGE =
            "Em đã dùng hết số lượt làm bài. Hãy xem lại kết quả và bảng xếp hạng nhé!";

    private ExamErrorMessages() {
    }

    /** Nhận diện lỗi hết lượt làm từ cả thông báo tiếng Việt và tiếng Anh. */
    static boolean isAttemptLimit(ApiError error) {
        if (error == null) return false;
        String message = error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("hết số lượt làm bài")
                || message.contains("giới hạn số lần làm bài")
                || message.contains("attempt limit")
                || message.contains("maximum number of attempts");
    }

    /** Loại thông tin kỹ thuật như attempt ID trước khi hiển thị cho học viên. */
    static String studentFriendlyMessage(ApiError error) {
        if (error == null) return "Có lỗi xảy ra, em hãy thử lại nhé!";
        if (isAttemptLimit(error)) return ATTEMPT_LIMIT_MESSAGE;

        return error.getMessage()
                .replaceAll("(?i)\\s*Attempt\\s*ID\\s*:\\s*[0-9a-f-]+", "")
                .trim();
    }
}
