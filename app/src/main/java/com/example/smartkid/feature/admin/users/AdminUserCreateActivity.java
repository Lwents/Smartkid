package com.example.smartkid.feature.admin.users;

import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ContentFormKind;

/** Form để Admin tạo một tài khoản người dùng mới. */
public final class AdminUserCreateActivity extends ContentFormActivity {

    @Override
    protected ContentFormKind formKind() {
        return ContentFormKind.ADMIN_USER;
    }
}
