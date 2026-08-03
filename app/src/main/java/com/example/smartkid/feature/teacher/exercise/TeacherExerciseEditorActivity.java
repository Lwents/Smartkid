package com.example.smartkid.feature.teacher.exercise;

import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ContentFormKind;
import com.example.smartkid.common.ui.form.ExerciseScope;

/**
 * Form Teacher soạn bài tập/bài thi. Một màn hình phục vụ hai phạm vi được chọn bằng extra có kiểu
 * {@link ExerciseScope}, không dùng chuỗi thô: {@link ExerciseScope#LESSON_EXERCISE} cho bài tập
 * gắn với lesson và {@link ExerciseScope#STANDALONE_EXAM} cho bài thi cấp khóa học.
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
