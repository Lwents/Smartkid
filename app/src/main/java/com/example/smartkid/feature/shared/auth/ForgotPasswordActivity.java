package com.example.smartkid.feature.shared.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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
    private View backButton;
    private View resendButton;
    private View successBackButton;
    private View successGroup;
    private ProgressBar progressBar;
    private TextView statusText;
    private AuthRepository repository;

    /** Khởi tạo form yêu cầu email đặt lại mật khẩu và trạng thái gửi lại. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.auth_activity_forgot_password);
            repository = new AuthRepository(this);
            emailInput = findViewById(R.id.inputForgotEmail);
            sendButton = findViewById(R.id.buttonSendReset);
            backButton = findViewById(R.id.buttonForgotBack);
            resendButton = findViewById(R.id.buttonForgotResend);
            successBackButton = findViewById(R.id.buttonForgotSuccessBack);
            successGroup = findViewById(R.id.groupForgotSuccess);
            progressBar = findViewById(R.id.progressForgot);
            statusText = findViewById(R.id.textForgotStatus);
            if (emailInput == null || sendButton == null || backButton == null
                    || resendButton == null || successBackButton == null
                    || successGroup == null || progressBar == null || statusText == null) {
                throw new IllegalStateException("Giao diện quên mật khẩu thiếu thành phần bắt buộc");
            }
            sendButton.setOnClickListener(view -> requestSafely());
            backButton.setOnClickListener(view -> finish());
            resendButton.setOnClickListener(view -> resendSafely());
            successBackButton.setOnClickListener(view -> finish());
            emailInput.setOnEditorActionListener((view, actionId, event) -> {
                boolean done = actionId == EditorInfo.IME_ACTION_DONE;
                boolean enterDown = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN;
                if (!done && !enterDown) return false;
                requestSafely();
                return true;
            });
            emailInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                    showStatus("", false);
                }
                @Override public void afterTextChanged(Editable editable) { }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "ForgotPasswordActivity", "Không thể tạo màn hình", exception);
            showErrorDialog("Không thể mở màn hình quên mật khẩu");
        }
    }

    /** Kiểm tra email và gửi yêu cầu cấp mã đặt lại mật khẩu. */
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
                    showSuccessState(message);
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

    /** Chuyển form sang trạng thái thành công sau khi server chấp nhận yêu cầu. */
    private void showSuccessState(String message) {
        emailInput.setEnabled(false);
        sendButton.setVisibility(View.GONE);
        backButton.setVisibility(View.GONE);
        successGroup.setVisibility(View.VISIBLE);
        showStatus(message == null || message.trim().isEmpty()
                ? getString(R.string.forgot_success_message) : message, true);
    }

    /** Hiển thị lại form để người dùng sửa email hoặc gửi lại yêu cầu. */
    private void showRequestForm() {
        successGroup.setVisibility(View.GONE);
        sendButton.setVisibility(View.VISIBLE);
        backButton.setVisibility(View.VISIBLE);
        emailInput.setEnabled(true);
        emailInput.requestFocus();
        showStatus("", false);
    }

    /** Gửi lại yêu cầu bằng email hiện tại và vẫn áp dụng kiểm tra đầu vào. */
    private void resendSafely() {
        showRequestForm();
        requestSafely();
    }

    /** Khóa các thao tác trong thời gian request quên mật khẩu đang chạy. */
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!loading);
        backButton.setEnabled(!loading);
        resendButton.setEnabled(!loading);
        successBackButton.setEnabled(!loading);
        emailInput.setEnabled(!loading);
    }

    /** Hiển thị hoặc ẩn thông báo trạng thái của request. */
    private void showStatus(String message, boolean visible) {
        statusText.setText(message);
        statusText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Lấy email đã loại khoảng trắng ở đầu và cuối. */
    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    /** Bảo vệ callback khỏi cập nhật một Activity đã đóng. */
    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }
}
