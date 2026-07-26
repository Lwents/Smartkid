package com.example.smartkid.common.ui;

import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.R;
import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.User;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.feature.shared.auth.LoginActivity;

/**
 * Shared session and logout actions for role-specific dashboards. This base is role-neutral: it
 * imports no {@code feature.admin} or {@code feature.teacher} screen. Management navigation and
 * feature listing are owned by each role's dashboard so Admin and Teacher stay decoupled.
 */
public abstract class RoleDashboardActivity extends BaseActivity {
    private SessionManager sessionManager;
    private AuthRepository authRepository;

    /** Người dùng của phiên đang đăng nhập. */
    protected final User currentUser() {
        return session().getUser();
    }

    /** Vai trò đã chuẩn hóa của phiên hiện tại. */
    protected final UserRole currentRole() {
        return UserRole.fromString(currentUser().getRole());
    }

    /** Chặn vào màn nếu sai vai trò; trả false thì màn hình phải dừng khởi tạo. */
    protected final boolean requireRole(UserRole... acceptedRoles) {
        UserRole role = currentRole();
        if (acceptedRoles != null) {
            for (UserRole accepted : acceptedRoles) {
                if (accepted != null && accepted == role) return true;
            }
        }
        showErrorDialog("Tài khoản không có quyền mở khu vực này");
        finish();
        return false;
    }

    /** Gắn nút đăng xuất kèm hộp xác nhận mặc định. */
    protected final void bindLogoutAction(int logoutButtonId) {
        findViewById(logoutButtonId).setOnClickListener(view -> confirmLogout());
    }

    /** Gắn nút đăng xuất với tiêu đề và nội dung xác nhận riêng. */
    protected final void bindLogoutAction(int logoutButtonId, int titleRes, int messageRes) {
        findViewById(logoutButtonId).setOnClickListener(view ->
                confirmLogout(getString(titleRes), getString(messageRes)));
    }

    private SessionManager session() {
        if (sessionManager == null) sessionManager = new SessionManager(this);
        return sessionManager;
    }

    private AuthRepository auth() {
        if (authRepository == null) authRepository = new AuthRepository(this);
        return authRepository;
    }

    /** Hỏi xác nhận rồi đăng xuất (gọi được từ menu tài khoản). */
    protected final void confirmLogout() {
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
}
