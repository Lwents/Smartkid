package com.example.smartkid.common.ui.form;

/**
 * The two scopes the exercise/exam editor supports, replacing the former
 * {@code teacher_exercises} / {@code teacher_exams} raw String kinds inside the form.
 */
public enum ExerciseScope {
    /** Exercise attached to a specific lesson (was {@code teacher_exercises}). */
    LESSON_EXERCISE(ContentFormKind.TEACHER_EXERCISE),
    /** Standalone exam bound to a course (was {@code teacher_exams}). */
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
