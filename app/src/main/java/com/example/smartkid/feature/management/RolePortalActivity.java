package com.example.smartkid.feature.management;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.model.User;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.feature.auth.LoginActivity;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.ui.FeatureItemAdapter;
import com.example.smartkid.feature.profile.ChangePasswordActivity;
import com.example.smartkid.feature.profile.ProfileEditActivity;

import java.util.ArrayList;
import java.util.List;

/** Cổng chức năng giáo viên/quản trị, tách quyền khỏi cổng học viên. */
public class RolePortalActivity extends BaseActivity {
    private SessionManager sessionManager;
    private AuthRepository authRepository;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.management_activity_portal);
            sessionManager = new SessionManager(this);
            authRepository = new AuthRepository(this);
            user = sessionManager.getUser();
            TextView welcome = findViewById(R.id.textRoleWelcome);
            ListView list = findViewById(R.id.listRoleFeatures);
            if (welcome == null || list == null) {
                throw new IllegalStateException("Giao diện cổng quản lý chưa đầy đủ");
            }
            boolean admin = "admin".equalsIgnoreCase(user.getRole());
            welcome.setText(getString(R.string.role_welcome_format,
                    getString(admin ? R.string.role_admin : R.string.role_teacher),
                    user.getFullName().isEmpty() ? user.getUsername() : user.getFullName()));
            FeatureItemAdapter adapter = new FeatureItemAdapter(this);
            adapter.setItems(admin ? adminItems() : teacherItems());
            list.setAdapter(adapter);
            list.setOnItemClickListener((parent, row, position, id) ->
                    openFeature(adapter.getItem(position)));
            findViewById(R.id.buttonRoleProfile).setOnClickListener(view ->
                    openActivity(ProfileEditActivity.class));
            findViewById(R.id.buttonRolePassword).setOnClickListener(view ->
                    openActivity(ChangePasswordActivity.class));
            findViewById(R.id.buttonRoleLogout).setOnClickListener(view -> confirmLogout());
        } catch (Exception exception) {
            AppLogger.error(this, "RolePortalActivity", "Không thể tạo cổng vai trò", exception);
            showErrorDialog("Không thể mở cổng quản lý");
        }
    }

    private List<FeatureItem> teacherItems() {
        String[] keys = {
                "teacher_dashboard", "teacher_qa", "teacher_courses",
                "teacher_content_library", "teacher_classes", "teacher_assignments",
                "teacher_live", "teacher_exams", "teacher_exam_reports", "teacher_games",
                "teacher_students", "teacher_progress", "teacher_feedback", "teacher_notifications"
        };
        return items(keys);
    }

    private List<FeatureItem> adminItems() {
        String[] keys = {
                "admin_dashboard", "admin_active_users", "admin_users", "admin_courses",
                "admin_approval", "admin_health", "admin_activity", "admin_security",
                "admin_sessions", "admin_config", "admin_backups", "admin_report_revenue",
                "admin_report_users", "admin_report_learning", "admin_report_content",
                "admin_transactions", "admin_notifications"
        };
        return items(keys);
    }

    private List<FeatureItem> items(String[] keys) {
        List<FeatureItem> result = new ArrayList<>();
        for (String key : keys) {
            ManagementSpec spec = ManagementSpec.get(key);
            if (spec == null) continue;
            result.add(new FeatureItem(key, spec.getTitle(),
                    spec.isAvailable() ? "Dữ liệu API thật" : "Frontend chưa có API thật",
                    spec.isAvailable() ? "" : spec.getUnavailableReason(),
                    spec.isAvailable() ? "Sẵn sàng" : "Không dùng dữ liệu giả", null));
        }
        return result;
    }

    private void openFeature(FeatureItem item) {
        if (item == null) return;
        ManagementSpec spec = ManagementSpec.get(item.getId());
        if (spec == null) return;
        if (!spec.isAvailable()) {
            new AlertDialog.Builder(this).setTitle(spec.getTitle())
                    .setMessage(spec.getUnavailableReason()
                            + "\n\nỨng dụng cố ý không tạo mock để tránh hiển thị sai dữ liệu.")
                    .setPositiveButton("Đã hiểu", null).show();
            return;
        }
        try {
            Intent intent = new Intent(this, ManagementFeatureActivity.class);
            intent.putExtra(ManagementFeatureActivity.EXTRA_SPEC_KEY, spec.getKey());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "RolePortalActivity", "Không thể mở chức năng", exception);
            showErrorDialog("Không thể mở chức năng quản lý");
        }
    }

    private void openActivity(Class<?> destination) {
        try { startActivity(new Intent(this, destination)); }
        catch (Exception exception) {
            AppLogger.error(this, "RolePortalActivity", "Không thể chuyển trang", exception);
            showErrorDialog("Không thể mở chức năng");
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this).setTitle(R.string.logout)
                .setMessage(R.string.logout_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout, (dialog, which) -> logout())
                .show();
    }

    private void logout() {
        authRepository.logout(new ApiCallback<Boolean>() {
            @Override public void onSuccess(Boolean data) { openLogin(); }
            @Override public void onError(ApiError error) { sessionManager.clear(); openLogin(); }
        });
    }

    private void openLogin() {
        try {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception exception) {
            AppLogger.error(this, "RolePortalActivity", "Không thể về đăng nhập", exception);
        }
    }
}
