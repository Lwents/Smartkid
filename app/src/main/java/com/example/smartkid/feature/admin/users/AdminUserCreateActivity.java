package com.example.smartkid.feature.admin.users;

import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ContentFormKind;

/** Admin-owned form for creating a user account. */
public final class AdminUserCreateActivity extends ContentFormActivity {

    @Override
    protected ContentFormKind formKind() {
        return ContentFormKind.ADMIN_USER;
    }
}
