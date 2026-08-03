package com.example.smartkid.feature.teacher.course;

import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ContentFormKind;

/** Form để giáo viên tạo một chương mới bên trong khóa học. */
public final class TeacherModuleCreateActivity extends ContentFormActivity {

    @Override
    protected ContentFormKind formKind() {
        return ContentFormKind.TEACHER_MODULE;
    }
}
