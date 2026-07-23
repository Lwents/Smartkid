package com.example.smartkid.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.domain.BusinessRules;
import com.example.smartkid.ui.common.BaseActivity;
import com.google.android.material.textfield.TextInputEditText;

/** Đăng ký tài khoản học viên bằng API thật của Django. */
public class RegisterActivity extends BaseActivity {
    private TextInputEditText usernameInput;
    private TextInputEditText emailInput;
    private TextInputEditText phoneInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmationInput;
    private Button registerButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private AuthRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_register);
            repository = new AuthRepository(this);
            bindViews();
            registerButton.setOnClickListener(view -> registerSafely());
            findViewById(R.id.buttonRegisterBack).setOnClickListener(view -> finish());
        } catch (Exception exception) {
            AppLogger.error(this, "RegisterActivity", "Không thể tạo màn hình đăng ký", exception);
            showErrorDialog("Không thể mở màn hình đăng ký");
        }
    }

    private void bindViews() {
        usernameInput = findViewById(R.id.inputRegisterUsername);
        emailInput = findViewById(R.id.inputRegisterEmail);
        phoneInput = findViewById(R.id.inputRegisterPhone);
        passwordInput = findViewById(R.id.inputRegisterPassword);
        confirmationInput = findViewById(R.id.inputRegisterConfirmation);
        registerButton = findViewById(R.id.buttonRegister);
        progressBar = findViewById(R.id.progressRegister);
        statusText = findViewById(R.id.textRegisterStatus);
        if (usernameInput == null || emailInput == null || phoneInput == null
                || passwordInput == null || confirmationInput == null || registerButton == null
                || progressBar == null || statusText == null) {
            throw new IllegalStateException("Giao diện đăng ký thiếu thành phần bắt buộc");
        }
    }

    private void registerSafely() {
        try {
            String username = textOf(usernameInput);
            String email = textOf(emailInput);
            String phone = textOf(phoneInput);
            String password = rawTextOf(passwordInput);
            String confirmation = rawTextOf(confirmationInput);
            String validation = BusinessRules.validateRegistration(
                    username, email, phone, password, confirmation);
            if (!validation.isEmpty()) {
                showStatus(validation, true);
                return;
            }
            setLoading(true);
            showStatus("", false);
            repository.register(username, email, phone, password, new ApiCallback<String>() {
                @Override
                public void onSuccess(String message) {
                    if (!isUsable()) return;
                    setLoading(false);
                    showShortMessage(message);
                    Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                    intent.putExtra("identifier", username);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
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
            AppLogger.error(this, "RegisterActivity", "Không thể xử lý đăng ký", exception);
            setLoading(false);
            showStatus(getString(R.string.unknown_error), true);
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!loading);
        usernameInput.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        phoneInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        confirmationInput.setEnabled(!loading);
    }

    private void showStatus(String message, boolean visible) {
        statusText.setText(message);
        statusText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private String textOf(TextInputEditText input) {
        return rawTextOf(input).trim();
    }

    private String rawTextOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }
}
