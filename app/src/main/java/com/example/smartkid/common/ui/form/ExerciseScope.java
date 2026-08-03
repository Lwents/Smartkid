package com.example.smartkid.common.ui.form;

/**
 * Hai phạm vi mà form bài tập/bài thi hỗ trợ, thay cho các chuỗi thô
 * {@code teacher_exercises} / {@code teacher_exams} trước đây.
 */
public enum ExerciseScope {
    /** Bài tập gắn với một lesson cụ thể (trước đây là {@code teacher_exercises}). */
    LESSON_EXERCISE(ContentFormKind.TEACHER_EXERCISE),
    /** Bài thi độc lập gắn với khóa học (trước đây là {@code teacher_exams}). */
    STANDALONE_EXAM(ContentFormKind.TEACHER_EXAM);

    private final ContentFormKind formKind;

    ExerciseScope(ContentFormKind formKind) {
        this.formKind = formKind;
    }

    public ContentFormKind formKind() {
        return formKind;
    }

    public static ExerciseScope fromName(String name, ExerciseScope fallback) {
        if (name == null) return fallback;
        for (ExerciseScope scope : values()) {
            if (scope.name().equals(name)) return scope;
        }
        return fallback;
    }
}
