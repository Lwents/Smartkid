package com.example.smartkid.feature.teacher.course;

import android.content.Intent;

import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ContentFormKind;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.feature.teacher.TeacherCourseContentActivity;

import org.json.JSONObject;

/** Teacher-owned form for creating a course. Opens content management right after creation. */
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
            Intent intent = new Intent(this, TeacherCourseContentActivity.class);
            intent.putExtra(TeacherCourseContentActivity.EXTRA_COURSE_ID, createdCourseId);
            intent.putExtra(TeacherCourseContentActivity.EXTRA_COURSE_TITLE, currentTitle());
            startActivity(intent);
            return true;
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherCourseCreateActivity",
                    "Không thể mở nội dung khóa học vừa tạo", exception);
            return false;
        }
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
