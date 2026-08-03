package com.example.smartkid.common.ui.form;

/** Giới hạn số bộ nhập câu hỏi được dựng đồng thời để tránh tăng RAM với đề lớn. */
public final class QuestionRenderPolicy {
    public static final int MAX_EXPANDED = 10;

    private QuestionRenderPolicy() {
    }

    /** Tính số câu được dựng đầy đủ, không âm và không vượt giới hạn RAM. */
    public static int expandedCount(int totalQuestions) {
        return Math.min(Math.max(0, totalQuestions), MAX_EXPANDED);
    }

    /** Tính số câu còn lại chỉ giữ dạng JSON gọn thay vì dựng toàn bộ view. */
    public static int compactCount(int totalQuestions) {
        return Math.max(0, totalQuestions - expandedCount(totalQuestions));
    }
}
