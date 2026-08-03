package com.example.smartkid.feature.shared.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

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
    private Button forgotPasswordButton;
    private Button registerButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private AuthRepository authRepository;
    private boolean otpRequired;

    private static final String STATE_OTP_REQUIRED = "state_otp_required";

    /** Khởi tạo form đăng nhập, Repository và các hành động chuyển sang đăng ký/quên mật khẩu. */
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
            if (savedInstanceState != null
                    && savedInstanceState.getBoolean(STATE_OTP_REQUIRED, false)) {
                otpRequired = true;
                otpLayout.setVisibility(View.VISIBLE);
            }
            loginButton.setOnClickListener(view -> safelyLogin());
            forgotPasswordButton.setOnClickListener(view ->
                    openSafely(ForgotPasswordActivity.class));
            registerButton.setOnClickListener(view ->
                    openSafely(RegisterActivity.class));
            bindKeyboardActions();
            clearStatusWhenEditing(identifierInput, passwordInput, otpInput);
        } catch (Exception exception) {
            AppLogger.error(this, "LoginActivity", "Không thể tạo màn hình đăng nhập", exception);
            showErrorDialog("Không thể mở màn hình đăng nhập: " + exception.getMessage());
        }
    }

    /** Mở màn hình xác thực khác và chuyển mọi lỗi điều hướng thành thông báo dễ hiểu. */
    private void openSafely(Class<?> destination) {
        try {
            startActivity(new Intent(this, destination));
        } catch (Exception exception) {
            AppLogger.error(this, "LoginActivity", "Không thể mở màn hình xác thực", exception);
            showErrorDialog("Không thể mở màn hình yêu cầu");
        }
    }

    /** Ánh xạ các ô nhập, nút và vùng trạng thái; dừng sớm nếu layout không đầy đủ. */
    private void bindViews() {
        identifierInput = findViewById(R.id.inputIdentifier);
        passwordInput = findViewById(R.id.inputPassword);
        otpInput = findViewById(R.id.inputOtp);
        otpLayout = findViewById(R.id.layoutOtp);
        loginButton = findViewById(R.id.buttonLogin);
        forgotPasswordButton = findViewById(R.id.buttonForgotPassword);
        registerButton = findViewById(R.id.buttonOpenRegister);
        progressBar = findViewById(R.id.progressLogin);
        statusText = findViewById(R.id.textLoginStatus);

        if (identifierInput == null || passwordInput == null || otpInput == null
                || otpLayout == null || loginButton == null || progressBar == null
                || forgotPasswordButton == null || registerButton == null
                || statusText == null) {
            throw new IllegalStateException("Giao diện đăng nhập thiếu thành phần bắt buộc");
        }
    }

    /** Cho phép phím Next/Done trên bàn phím chuyển ô hoặc gửi form đăng nhập. */
    private void bindKeyboardActions() {
        passwordInput.setOnEditorActionListener((view, actionId, event) ->
                handleLoginEditorAction(actionId, event));
        otpInput.setOnEditorActionListener((view, actionId, event) ->
                handleLoginEditorAction(actionId, event));
    }

    /** Xác định sự kiện bàn phím có phải yêu cầu đăng nhập hay không. */
    private boolean handleLoginEditorAction(int actionId, KeyEvent event) {
        boolean imeDone = actionId == EditorInfo.IME_ACTION_DONE;
        boolean enterDown = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && event.getAction() == KeyEvent.ACTION_DOWN;
        if (!imeDone && !enterDown) return false;
        safelyLogin();
        return true;
    }

    /** Xóa thông báo lỗi cũ khi người dùng bắt đầu sửa dữ liệu. */
    private void clearStatusWhenEditing(TextInputEditText... inputs) {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                statusText.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(Editable editable) { }
        };
        for (TextInputEditText input : inputs) input.addTextChangedListener(watcher);
    }

    /** Bao luồng đăng nhập bằng try/catch để lỗi giao diện không làm ứng dụng dừng. */
    private void safelyLogin() {
        try {
            performLogin();
        } catch (Exception exception) {
            AppLogger.error(this, "LoginActivity", "Lỗi xử lý nút đăng nhập", exception);
            setLoading(false);
            showErrorDialog("Không thể xử lý đăng nhập");
        }
    }

    /** Kiểm tra dữ liệu, gọi AuthRepository và điều hướng theo role khi đăng nhập thành công. */
    private void performLogin() {
        String identifier = textOf(identifierInput);
        String password = rawTextOf(passwordInput);
        String otp = textOf(otpInput);

        String validationError = BusinessRules.validateLogin(identifier, password);
        if (!validationError.isEmpty()) {
            showStatus(validationError, true);
            return;
        }
        if (otpLayout.getVisibility() == View.VISIBLE
                && otp.length() != 6) {
            showStatus(getString(R.string.otp_length_error), true);
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
                    otpRequired = true;
                    otpLayout.setVisibility(View.VISIBLE);
                    showStatus(result.getMessage(), false);
                    otpInput.requestFocus();
                    showKeyboard(otpInput);
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
                showStatus(error == null ? "Không thể đăng nhập, vui lòng thử lại"
                        : error.getMessage(), true);
            }
        });
    }

    /** Lưu trạng thái đang yêu cầu OTP để xoay màn hình không làm mất bước xác thực. */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_OTP_REQUIRED, otpRequired);
    }

    /** Khóa/mở form và nút trong lúc request đăng nhập đang chạy. */
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        forgotPasswordButton.setEnabled(!loading);
        registerButton.setEnabled(!loading);
        identifierInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        otpInput.setEnabled(!loading);
    }

    /** Hiển thị thông báo thành công hoặc lỗi với màu tương ứng. */
    private void showStatus(String message, boolean error) {
        statusText.setText(message == null ? "" : message);
        statusText.setTextColor(ContextCompat.getColor(this,
                error ? R.color.smartkid_error : R.color.smartkid_primary));
        statusText.setVisibility(View.VISIBLE);
    }

    /** Lấy chuỗi đã trim cho username, email hoặc OTP. */
    private String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    /** Lấy nguyên văn mật khẩu để không vô tình thay đổi khoảng trắng hợp lệ. */
    private String rawTextOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    /** Ẩn bàn phím trước khi gửi request để người dùng thấy trạng thái tải. */
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

    /** Đưa focus và mở bàn phím cho ô OTP khi server yêu cầu xác thực bước hai. */
    private void showKeyboard(View target) {
        if (target == null) return;
        target.post(() -> {
            try {
                InputMethodManager manager =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (manager != null) manager.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception exception) {
                AppLogger.error(this, "LoginActivity", "Không thể mở bàn phím OTP", exception);
            }
        });
    }
}
