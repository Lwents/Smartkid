package com.example.smartkid.feature.teacher.course;

import android.content.Intent;

import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ContentFormKind;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.feature.teacher.course.builder.TeacherCourseBuilderActivity;

import org.json.JSONObject;

/** Form Teacher tạo khóa học; mở trình xây dựng khóa học ngay sau khi tạo thành công. */
public final class TeacherCourseCreateActivity extends ContentFormActivity {

    @Override
    protected ContentFormKind formKind() {
        return ContentFormKind.TEACHER_COURSE;
    }

    @Override
    protected boolean onContentCreated(JSONObject data) {
        String createdCourseId = data == null ? "" : safeString(data.optString("id", ""));
        if (createdCourseId.isEmpty()) return false;
        try {
            Intent intent = new Intent(this, TeacherCourseBuilderActivity.class);
            intent.putExtra(TeacherCourseBuilderActivity.EXTRA_COURSE_ID, createdCourseId);
            intent.putExtra(TeacherCourseBuilderActivity.EXTRA_COURSE_TITLE, currentTitle());
            startActivity(intent);
            return true;
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherCourseCreateActivity",
                    "Không thể mở trình dựng khóa học vừa tạo", exception);
            return false;
        }
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
