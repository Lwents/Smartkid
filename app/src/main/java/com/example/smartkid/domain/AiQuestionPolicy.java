package com.example.smartkid.domain;

import java.util.Arrays;

/** Giới hạn nghiệp vụ dùng chung khi giáo viên tạo câu hỏi bằng AI. */
public final class AiQuestionPolicy {
    public static final long MAX_DOCUMENT_BYTES = 20L * 1024L * 1024L;
    public static final int MAX_GENERATED_QUESTIONS = 50;
    private static final int[] ALLOWED_COUNTS = {5, 10, 20, 30, 50};

    private AiQuestionPolicy() {
    }

    /** Trả bản sao các mức số lượng câu hỏi được phép chọn trên giao diện. */
    public static int[] allowedCounts() {
        return Arrays.copyOf(ALLOWED_COUNTS, ALLOWED_COUNTS.length);
    }

    /** Kiểm tra số lượng yêu cầu có thuộc các mức sản phẩm hỗ trợ hay không. */
    public static boolean isAllowedCount(int count) {
        for (int allowed : ALLOWED_COUNTS) {
            if (count == allowed) return true;
        }
        return false;
    }

    /** Ép số câu AI trả về vào khoảng an toàn từ 0 đến giới hạn tối đa. */
    public static int clampGeneratedCount(int count) {
        return Math.max(0, Math.min(MAX_GENERATED_QUESTIONS, count));
    }

    /** Tính số câu còn có thể thêm mà không vượt giới hạn của form. */
    public static int remainingCapacity(int existingCount) {
        return Math.max(0, MAX_GENERATED_QUESTIONS - Math.max(0, existingCount));
    }

    /** Chấp nhận kích thước chưa xác định hoặc tệp không vượt quá 20 MB. */
    public static boolean acceptsDocumentSize(long bytes) {
        return bytes < 0 || bytes <= MAX_DOCUMENT_BYTES;
    }
}
