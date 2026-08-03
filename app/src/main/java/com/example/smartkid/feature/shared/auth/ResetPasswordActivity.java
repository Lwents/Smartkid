package com.example.smartkid.feature.shared.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.domain.BusinessRules;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.textfield.TextInputEditText;

/** Xác nhận mã email và lưu mật khẩu mới qua API. */
public class ResetPasswordActivity extends BaseActivity {
    private TextInputEditText emailInput;
    private TextInputEditText tokenInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmationInput;
    private Button resetButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private AuthRepository repository;

    /** Khởi tạo form đặt lại mật khẩu và đọc email/token từ deep link hoặc Intent. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.auth_activity_reset_password);
            repository = new AuthRepository(this);
            bindViews();
            prefillFromIntent(getIntent());
            resetButton.setOnClickListener(view -> resetSafely());
            findViewById(R.id.buttonResetBack).setOnClickListener(view -> finish());
        } catch (Exception exception) {
            AppLogger.error(this, "ResetPasswordActivity", "Không thể tạo màn hình", exception);
            showErrorDialog("Không thể mở màn hình đặt lại mật khẩu");
        }
    }

    /** Ánh xạ các trường mã xác nhận, mật khẩu mới và nút gửi. */
    private void bindViews() {
        emailInput = findViewById(R.id.inputResetEmail);
        tokenInput = findViewById(R.id.inputResetToken);
        passwordInput = findViewById(R.id.inputResetPassword);
        confirmationInput = findViewById(R.id.inputResetConfirmation);
        resetButton = findViewById(R.id.buttonResetPassword);
        progressBar = findViewById(R.id.progressReset);
        statusText = findViewById(R.id.textResetStatus);
        if (emailInput == null || tokenInput == null || passwordInput == null
                || confirmationInput == null || resetButton == null || progressBar == null
                || statusText == null) {
            throw new IllegalStateException("Giao diện đặt lại mật khẩu thiếu thành phần bắt buộc");
        }
    }

    /** Điền sẵn email và token nhận từ liên kết đặt lại mật khẩu. */
    private void prefillFromIntent(Intent intent) {
        if (intent == null) return;
        String email = intent.getStringExtra("email");
        String token = intent.getStringExtra("token");
        Uri data = intent.getData();
        if (data != null) {
            if (email == null || email.isEmpty()) email = data.getQueryParameter("email");
            if (token == null || token.isEmpty()) token = data.getQueryParameter("token");
            if (token == null || token.isEmpty()) token = data.getQueryParameter("reset_token");
        }
        if (email != null) emailInput.setText(email);
        if (token != null) tokenInput.setText(token);
    }

    /** Kiểm tra token/mật khẩu rồi gọi API xác nhận đặt lại mật khẩu. */
    private void resetSafely() {
        try {
            String email = textOf(emailInput);
            String token = textOf(tokenInput);
            String password = rawTextOf(passwordInput);
            String confirmation = rawTextOf(confirmationInput);
            String validation = BusinessRules.validateResetPassword(
                    email, token, password, confirmation);
            if (!validation.isEmpty()) {
                showStatus(validation, true);
                return;
            }
            setLoading(true);
            showStatus("", false);
            repository.resetPassword(email, token, password, new ApiCallback<String>() {
                @Override
                public void onSuccess(String message) {
                    if (!isUsable()) return;
                    setLoading(false);
                    showShortMessage(message);
                    Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
                    intent.putExtra("identifier", email);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    showStatus(error == null ? getString(R.string.unknown_error)
                            : error.getMessage(), true);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "ResetPasswordActivity", "Không thể đặt lại mật khẩu", exception);
            setLoading(false);
            showStatus(getString(R.string.unknown_error), true);
        }
    }

    /** Khóa form trong lúc request đặt lại mật khẩu đang chạy. */
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        resetButton.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        tokenInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        confirmationInput.setEnabled(!loading);
    }

    /** Cập nhật thông báo lỗi hoặc kết quả của thao tác. */
    private void showStatus(String message, boolean visible) {
        statusText.setText(message);
        statusText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Lấy giá trị đã trim cho email và token. */
    private String textOf(TextInputEditText input) {
        return rawTextOf(input).trim();
    }

    /** Lấy nguyên văn mật khẩu mới để giữ đúng dữ liệu người dùng nhập. */
    private String rawTextOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    /** Xác nhận Activity còn hợp lệ trước khi callback thay đổi giao diện. */
    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }
}
