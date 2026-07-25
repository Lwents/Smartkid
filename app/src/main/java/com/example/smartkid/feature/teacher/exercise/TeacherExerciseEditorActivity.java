package com.example.smartkid.feature.teacher.exercise;

import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ContentFormKind;
import com.example.smartkid.common.ui.form.ExerciseScope;

/**
 * Teacher-owned exercise/exam authoring form. A single screen serves two scopes selected via a
 * typed {@link ExerciseScope} extra (never a raw String): {@link ExerciseScope#LESSON_EXERCISE}
 * for an exercise attached to a lesson, and {@link ExerciseScope#STANDALONE_EXAM} for a
 * course-level exam.
 */
public final class TeacherExerciseEditorActivity extends ContentFormActivity {
    public static final String EXTRA_SCOPE = "teacher_exercise_scope";

    @Override
    protected ContentFormKind formKind() {
        return scope() == ExerciseScope.STANDALONE_EXAM
                ? ContentFormKind.TEACHER_EXAM
                : ContentFormKind.TEACHER_EXERCISE;
    }

    private ExerciseScope scope() {
        String raw = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_SCOPE);
        return ExerciseScope.fromName(raw, ExerciseScope.LESSON_EXERCISE);
    }
}
