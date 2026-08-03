package com.example.smartkid.feature.teacher.course;

import android.content.Intent;

import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ContentFormKind;
import com.example.smartkid.common.ui.form.ExerciseScope;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.feature.teacher.exercise.TeacherExerciseEditorActivity;

import org.json.JSONObject;

/**
 * Form Teacher tạo lesson. Nếu loại nội dung là bài tập, màn hình sẽ mở trình soạn bài tập cho
 * lesson vừa tạo ngay sau khi API thành công.
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
