package com.example.smartkid.common.navigation;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.feature.shared.auth.LoginActivity;

/**
 * Safe landing screen when the signed-in account has a role the app cannot map to a
 * Student, Teacher or Admin experience. It never falls through to the Student home and it
 * does not auto-redirect to Login, so it cannot create a Login → RoleNavigation → Login loop.
 */
public final class UnsupportedRoleActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(buildContent());
        } catch (Exception exception) {
            AppLogger.error(this, "UnsupportedRoleActivity",
                    "Không thể mở màn hình vai trò không hợp lệ", exception);
            returnToLogin();
        }
    }

    private LinearLayout buildContent() {
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.WHITE);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("Vai trò tài khoản không hợp lệ");
        title.setTextSize(20f);
        title.setTextColor(Color.parseColor("#0F172A"));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView message = new TextView(this);
        message.setText("Tài khoản của bạn không thuộc nhóm Học viên, Giáo viên hoặc "
                + "Quản trị viên. Vui lòng đăng nhập bằng tài khoản hợp lệ hoặc liên hệ "
                + "quản trị viên để được hỗ trợ.");
        message.setTextSize(14f);
        message.setTextColor(Color.parseColor("#475569"));
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = padding / 2;
        message.setLayoutParams(messageParams);
        root.addView(message);

        Button loginButton = new Button(this);
        loginButton.setText("Quay về đăng nhập");
        loginButton.setAllCaps(false);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = padding;
        loginButton.setLayoutParams(buttonParams);
        loginButton.setOnClickListener(view -> logoutAndReturn());
        root.addView(loginButton);
        return root;
    }

    private void logoutAndReturn() {
        try {
            new AuthRepository(this).logout(new ApiCallback<Boolean>() {
                @Override public void onSuccess(Boolean data) { returnToLogin(); }
                @Override public void onError(ApiError error) {
                    new SessionManager(UnsupportedRoleActivity.this).clear();
                    returnToLogin();
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "UnsupportedRoleActivity", "Không thể đăng xuất", exception);
            new SessionManager(this).clear();
            returnToLogin();
        }
    }

    private void returnToLogin() {
        try {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception exception) {
            AppLogger.error(this, "UnsupportedRoleActivity", "Không thể về đăng nhập", exception);
            finish();
        }
    }
}
