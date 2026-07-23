package com.example.smartkid.feature.auth;

import android.content.Intent;
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

/** Yêu cầu backend gửi email khôi phục mật khẩu. */
public class ForgotPasswordActivity extends BaseActivity {
    private TextInputEditText emailInput;
    private Button sendButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private AuthRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.auth_activity_forgot_password);
            repository = new AuthRepository(this);
            emailInput = findViewById(R.id.inputForgotEmail);
            sendButton = findViewById(R.id.buttonSendReset);
            progressBar = findViewById(R.id.progressForgot);
            statusText = findViewById(R.id.textForgotStatus);
            if (emailInput == null || sendButton == null || progressBar == null || statusText == null) {
                throw new IllegalStateException("Giao diện quên mật khẩu thiếu thành phần bắt buộc");
            }
            sendButton.setOnClickListener(view -> requestSafely());
            findViewById(R.id.buttonHaveResetToken).setOnClickListener(view -> openReset());
            findViewById(R.id.buttonForgotBack).setOnClickListener(view -> finish());
        } catch (Exception exception) {
            AppLogger.error(this, "ForgotPasswordActivity", "Không thể tạo màn hình", exception);
            showErrorDialog("Không thể mở màn hình quên mật khẩu");
        }
    }

    private void requestSafely() {
        try {
            String email = textOf(emailInput);
            String validation = BusinessRules.validateForgotPasswordEmail(email);
            if (!validation.isEmpty()) {
                showStatus(validation, true);
                return;
            }
            setLoading(true);
            showStatus("", false);
            repository.requestPasswordReset(email, new ApiCallback<String>() {
                @Override
                public void onSuccess(String message) {
                    if (!isUsable()) return;
                    setLoading(false);
                    showStatus(message, true);
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
            AppLogger.error(this, "ForgotPasswordActivity", "Không thể gửi yêu cầu", exception);
            setLoading(false);
            showStatus(getString(R.string.unknown_error), true);
        }
    }

    private void openReset() {
        try {
            Intent intent = new Intent(this, ResetPasswordActivity.class);
            intent.putExtra("email", textOf(emailInput));
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "ForgotPasswordActivity", "Không thể mở đặt lại mật khẩu", exception);
            showErrorDialog("Không thể mở màn hình đặt lại mật khẩu");
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!loading);
        emailInput.setEnabled(!loading);
    }

    private void showStatus(String message, boolean visible) {
        statusText.setText(message);
        statusText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }
}
