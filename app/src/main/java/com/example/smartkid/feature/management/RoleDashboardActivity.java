package com.example.smartkid.feature.management;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.smartkid.R;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.User;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.feature.auth.LoginActivity;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

/** Shared navigation and session actions for role-specific dashboards. */
public abstract class RoleDashboardActivity extends BaseActivity {
    private SessionManager sessionManager;
    private AuthRepository authRepository;

    protected final User currentUser() {
        return session().getUser();
    }

    protected final boolean requireRole(String... acceptedRoles) {
        String role = currentUser().getRole();
        if (acceptedRoles != null) {
            for (String accepted : acceptedRoles) {
                if (accepted != null && accepted.equalsIgnoreCase(role)) return true;
            }
        }
        showErrorDialog("Tài khoản không có quyền mở khu vực này");
        finish();
        return false;
    }

    protected final void bindLogoutAction(int logoutButtonId) {
        findViewById(logoutButtonId).setOnClickListener(view -> confirmLogout());
    }

    protected final void bindLogoutAction(int logoutButtonId, int titleRes, int messageRes) {
        findViewById(logoutButtonId).setOnClickListener(view ->
                confirmLogout(getString(titleRes), getString(messageRes)));
    }

    protected final void openManagementFeature(String key) {
        ManagementSpec spec = ManagementSpec.get(key);
        if (spec == null || !spec.isAllowedForRole(currentUser().getRole())) {
            showErrorDialog("Tài khoản không có quyền mở chức năng này");
            return;
        }
        if (!spec.isAvailable()) {
            new AlertDialog.Builder(this)
                    .setTitle(spec.getTitle())
                    .setMessage(spec.getUnavailableReason()
                            + "\n\nỨng dụng không tạo dữ liệu giả khi backend chưa sẵn sàng.")
                    .setPositiveButton("Đã hiểu", null)
                    .show();
            return;
        }
        try {
            Intent intent = new Intent(this, ManagementFeatureActivity.class);
            intent.putExtra(ManagementFeatureActivity.EXTRA_SPEC_KEY, key);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "RoleDashboardActivity", "Không thể mở chức năng", exception);
            showErrorDialog("Không thể mở chức năng quản lý");
        }
    }

    protected final void openCreate(String kind) {
        if (!isCreateAllowed(kind, currentUser().getRole())) {
            showErrorDialog("Tài khoản không có quyền tạo dữ liệu này");
            return;
        }
        try {
            Intent intent = new Intent(this, ManagementCreateActivity.class);
            intent.putExtra(ManagementCreateActivity.EXTRA_KIND, kind);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "RoleDashboardActivity", "Không thể mở biểu mẫu", exception);
            showErrorDialog("Không thể mở biểu mẫu tạo mới");
        }
    }

    protected final void populateManagementActions(LinearLayout container, String[] keys) {
        if (container == null) return;
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String key : keys) {
            ManagementSpec spec = ManagementSpec.get(key);
            if (spec == null || !spec.isAllowedForRole(currentUser().getRole())) continue;
            View row = inflater.inflate(R.layout.management_item_dashboard_action, container, false);
            MaterialCardView card = row.findViewById(R.id.cardManagementAction);
            ImageView icon = row.findViewById(R.id.imageManagementActionIcon);
            TextView title = row.findViewById(R.id.textManagementActionTitle);
            TextView subtitle = row.findViewById(R.id.textManagementActionSubtitle);
            boolean isAdmin = "admin".equalsIgnoreCase(currentUser().getRole());
            icon.setImageResource(isAdmin ? adminIconFor(key) : iconFor(key));
            int tint = ContextCompat.getColor(this,
                    isAdmin
                            ? R.color.admin_header_end : R.color.teacher_header_end);
            icon.setImageTintList(ColorStateList.valueOf(tint));
            title.setText(spec.getTitle());
            subtitle.setText(spec.isAvailable()
                    ? "Dữ liệu trực tiếp từ hệ thống"
                    : "Chưa có API thật • chạm để xem lý do");
            card.setAlpha(spec.isAvailable() ? 1f : 0.65f);
            card.setOnClickListener(view -> openManagementFeature(key));
            container.addView(row);
        }
    }

    protected final void openManagementKeyFromView(View view) {
        Object tag = view == null ? null : view.getTag();
        if (tag != null) openManagementFeature(String.valueOf(tag));
    }

    private SessionManager session() {
        if (sessionManager == null) sessionManager = new SessionManager(this);
        return sessionManager;
    }

    private AuthRepository auth() {
        if (authRepository == null) authRepository = new AuthRepository(this);
        return authRepository;
    }

    private void confirmLogout() {
        confirmLogout(getString(R.string.logout), getString(R.string.logout_confirmation));
    }

    private void confirmLogout(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout, (dialog, which) -> logout())
                .show();
    }

    private void logout() {
        auth().logout(new ApiCallback<Boolean>() {
            @Override public void onSuccess(Boolean data) { openLogin(); }
            @Override public void onError(ApiError error) { session().clear(); openLogin(); }
        });
    }

    private void openLogin() {
        try {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception exception) {
            AppLogger.error(this, "RoleDashboardActivity", "Không thể về đăng nhập", exception);
        }
    }

    private static boolean isCreateAllowed(String kind, String role) {
        String safeKind = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        String safeRole = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (safeKind.startsWith("admin_")) return "admin".equals(safeRole);
        return safeKind.startsWith("teacher_")
                && ("teacher".equals(safeRole) || "instructor".equals(safeRole));
    }

    private static int iconFor(String key) {
        String value = key == null ? "" : key;
        if (value.contains("security")) return R.drawable.role_ic_security;
        if (value.contains("health")) return R.drawable.role_ic_health;
        if (value.contains("config") || value.contains("backup")) return R.drawable.role_ic_settings;
        if (value.contains("transaction")) return R.drawable.role_ic_payment;
        if (value.contains("notification") || value.contains("feedback")) return R.drawable.role_ic_bell;
        if (value.contains("report") || value.contains("progress")) return R.drawable.role_ic_chart;
        if (value.contains("exam") || value.contains("assignment")) return R.drawable.role_ic_exam;
        if (value.contains("course") || value.contains("content")) return R.drawable.role_ic_course;
        if (value.contains("class") || value.contains("live")) return R.drawable.role_ic_classroom;
        if (value.contains("qa")) return R.drawable.role_ic_question;
        return R.drawable.role_ic_users;
    }

    private static int adminIconFor(String key) {
        String value = key == null ? "" : key;
        if (value.contains("security")) return R.drawable.admin_ic_approval;
        if (value.contains("health")) return R.drawable.admin_ic_health;
        if (value.contains("config") || value.contains("backup")) {
            return R.drawable.admin_ic_settings;
        }
        if (value.contains("transaction")) return R.drawable.admin_ic_card;
        if (value.contains("notification")) return R.drawable.admin_ic_bell;
        if (value.contains("report") || value.contains("activity")) {
            return R.drawable.admin_ic_chart;
        }
        if (value.contains("course") || value.contains("content")) {
            return R.drawable.admin_ic_course;
        }
        if (value.contains("approval")) return R.drawable.admin_ic_approval;
        return R.drawable.admin_ic_users;
    }
}
