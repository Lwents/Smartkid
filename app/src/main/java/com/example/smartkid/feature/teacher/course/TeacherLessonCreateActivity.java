package com.example.smartkid.feature.teacher.course;

import android.content.Intent;

import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ContentFormKind;
import com.example.smartkid.common.ui.form.ExerciseScope;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.feature.teacher.exercise.TeacherExerciseEditorActivity;

import org.json.JSONObject;

/**
 * Teacher-owned form for creating a lesson. When the lesson content type is an exercise, it opens
 * the exercise editor for the freshly created lesson right after creation.
 */
public final class TeacherLessonCreateActivity extends ContentFormActivity {

    @Override
    protected ContentFormKind formKind() {
        return ContentFormKind.TEACHER_LESSON;
    }

    @Override
    protected boolean onContentCreated(JSONObject data) {
        if (!"exercise".equals(selectedContentType())) return false;
        String lessonId = data == null ? "" : safeString(data.optString("id", ""));
        if (lessonId.isEmpty()) return false;
        try {
            Intent intent = new Intent(this, TeacherExerciseEditorActivity.class);
            intent.putExtra(TeacherExerciseEditorActivity.EXTRA_SCOPE,
                    ExerciseScope.LESSON_EXERCISE.name());
            intent.putExtra(EXTRA_PARENT_ID, lessonId);
            intent.putExtra(EXTRA_PARENT_TITLE, currentTitle());
            intent.putExtra(EXTRA_COURSE_ID, linkedCourseId());
            startActivity(intent);
            return true;
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherLessonCreateActivity",
                    "Không thể mở biểu mẫu bài luyện tập", exception);
            return false;
        }
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
