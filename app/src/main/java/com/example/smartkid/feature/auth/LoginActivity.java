package com.example.smartkid.feature.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.navigation.RoleNavigation;
import com.example.smartkid.data.model.AuthResult;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.domain.BusinessRules;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends BaseActivity {
    private TextInputEditText identifierInput;
    private TextInputEditText passwordInput;
    private TextInputEditText otpInput;
    private TextInputLayout otpLayout;
    private Button loginButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.auth_activity_login);
            authRepository = new AuthRepository(this);
            bindViews();
            String identifier = getIntent() == null ? null : getIntent().getStringExtra("identifier");
            if (identifier != null && !identifier.trim().isEmpty()) {
                identifierInput.setText(identifier.trim());
            }
            loginButton.setOnClickListener(view -> safelyLogin());
            findViewById(R.id.buttonForgotPassword).setOnClickListener(view ->
                    openSafely(ForgotPasswordActivity.class));
            findViewById(R.id.buttonOpenRegister).setOnClickListener(view ->
                    openSafely(RegisterActivity.class));
        } catch (Exception exception) {
            AppLogger.error(this, "LoginActivity", "Không thể tạo màn hình đăng nhập", exception);
            showErrorDialog("Không thể mở màn hình đăng nhập: " + exception.getMessage());
        }
    }

    private void openSafely(Class<?> destination) {
        try {
            startActivity(new Intent(this, destination));
        } catch (Exception exception) {
            AppLogger.error(this, "LoginActivity", "Không thể mở màn hình xác thực", exception);
            showErrorDialog("Không thể mở màn hình yêu cầu");
        }
    }

    private void bindViews() {
        identifierInput = findViewById(R.id.inputIdentifier);
        passwordInput = findViewById(R.id.inputPassword);
        otpInput = findViewById(R.id.inputOtp);
        otpLayout = findViewById(R.id.layoutOtp);
        loginButton = findViewById(R.id.buttonLogin);
        progressBar = findViewById(R.id.progressLogin);
        statusText = findViewById(R.id.textLoginStatus);

        if (identifierInput == null || passwordInput == null || otpInput == null
                || otpLayout == null || loginButton == null || progressBar == null
                || statusText == null) {
            throw new IllegalStateException("Giao diện đăng nhập thiếu thành phần bắt buộc");
        }
    }

    private void safelyLogin() {
        try {
            performLogin();
        } catch (Exception exception) {
            AppLogger.error(this, "LoginActivity", "Lỗi xử lý nút đăng nhập", exception);
            setLoading(false);
            showErrorDialog("Không thể xử lý đăng nhập");
        }
    }

    private void performLogin() {
        String identifier = textOf(identifierInput);
        String password = textOf(passwordInput);
        String otp = textOf(otpInput);

        String validationError = BusinessRules.validateLogin(identifier, password);
        if (!validationError.isEmpty()) {
            statusText.setText(validationError);
            statusText.setVisibility(View.VISIBLE);
            return;
        }
        if (otpLayout.getVisibility() == View.VISIBLE
                && (otp.length() < 4 || otp.length() > 8)) {
            statusText.setText(R.string.otp_length_error);
            statusText.setVisibility(View.VISIBLE);
            return;
        }

        hideKeyboard();
        setLoading(true);
        statusText.setVisibility(View.GONE);
        authRepository.login(identifier, password, otp, new ApiCallback<AuthResult>() {
            @Override
            public void onSuccess(AuthResult result) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                if (result.isRequiresOtp()) {
                    otpLayout.setVisibility(View.VISIBLE);
                    statusText.setText(result.getMessage());
                    statusText.setVisibility(View.VISIBLE);
                    otpInput.requestFocus();
                    return;
                }
                Intent intent = new Intent(LoginActivity.this,
                        RoleNavigation.destination(LoginActivity.this));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(ApiError error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                statusText.setText(error.getMessage());
                statusText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        identifierInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        otpInput.setEnabled(!loading);
    }

    private String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View focused = getCurrentFocus();
            if (manager != null && focused != null) {
                manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
        } catch (Exception exception) {
            AppLogger.error(this, "LoginActivity", "Không thể đóng bàn phím", exception);
        }
    }
}
