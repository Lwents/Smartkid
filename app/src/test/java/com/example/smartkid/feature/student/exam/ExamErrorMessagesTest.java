package com.example.smartkid.feature.student.exam;

import com.example.smartkid.data.remote.ApiError;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExamErrorMessagesTest {

    @Test
    public void attemptLimit_isRecognizedWithoutShowingTechnicalId() {
        ApiError error = new ApiError(400,
                "Bạn đã hoàn thành bài kiểm tra và đạt giới hạn số lần làm bài. "
                        + "Attempt ID: 8cd42e75-e7da-4133-9b3c-ca93a18868b0",
                false);

        assertTrue(ExamErrorMessages.isAttemptLimit(error));
        assertEquals(
                "Em đã dùng hết số lượt làm bài. Hãy xem lại kết quả và bảng xếp hạng nhé!",
                ExamErrorMessages.studentFriendlyMessage(error));
        assertFalse(ExamErrorMessages.studentFriendlyMessage(error).contains("8cd42e75"));
    }

    @Test
    public void otherErrors_onlyRemoveAttemptMetadata() {
        ApiError error = new ApiError(400,
                "Không thể mở kết quả. Attempt ID: 8cd42e75-e7da-4133-9b3c-ca93a18868b0",
                false);

        assertFalse(ExamErrorMessages.isAttemptLimit(error));
        assertEquals("Không thể mở kết quả.",
                ExamErrorMessages.studentFriendlyMessage(error));
    }
}
