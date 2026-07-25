package com.example.smartkid.feature.teacher.course;

import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ContentFormKind;

/** Teacher-owned form for creating a module inside a course. */
public final class TeacherModuleCreateActivity extends ContentFormActivity {

    @Override
    protected ContentFormKind formKind() {
        return ContentFormKind.TEACHER_MODULE;
    }
}
