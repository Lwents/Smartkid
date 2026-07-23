package com.example.smartkid.feature.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

public class ChangePasswordActivity extends BaseActivity {
    private TextInputEditText oldInput;
    private TextInputEditText newInput;
    private TextInputEditText confirmationInput;
    private Button saveButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private StudentFeatureRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.profile_activity_change_password);
            repository = new StudentFeatureRepository(this);
            MaterialToolbar toolbar = findViewById(R.id.toolbarChangePassword);
            oldInput = findViewById(R.id.inputOldPassword);
            newInput = findViewById(R.id.inputNewPassword);
            confirmationInput = findViewById(R.id.inputNewPasswordConfirmation);
            saveButton = findViewById(R.id.buttonChangePassword);
            progressBar = findViewById(R.id.progressChangePassword);
            statusText = findViewById(R.id.textChangePasswordStatus);
            if (toolbar == null || oldInput == null || newInput == null
                    || confirmationInput == null || saveButton == null || progressBar == null
                    || statusText == null) {
                throw new IllegalStateException("Giao diện đổi mật khẩu chưa đầy đủ");
            }
            toolbar.setNavigationOnClickListener(view -> finish());
            saveButton.setOnClickListener(view -> changeSafely());
        } catch (Exception exception) {
            AppLogger.error(this, "ChangePasswordActivity", "Không thể tạo màn hình", exception);
            showErrorDialog("Không thể mở đổi mật khẩu");
        }
    }

    private void changeSafely() {
        try {
            String oldPassword = rawText(oldInput);
            String newPassword = rawText(newInput);
            String confirmation = rawText(confirmationInput);
            if (oldPassword.isEmpty()) {
                showStatus("Vui lòng nhập mật khẩu hiện tại");
                return;
            }
            if (newPassword.length() < 6) {
                showStatus("Mật khẩu mới phải có ít nhất 6 ký tự");
                return;
            }
            if (!newPassword.equals(confirmation)) {
                showStatus("Mật khẩu nhập lại không khớp");
                return;
            }
            if (oldPassword.equals(newPassword)) {
                showStatus("Mật khẩu mới phải khác mật khẩu hiện tại");
                return;
            }
            setLoading(true);
            repository.changePassword(oldPassword, newPassword, new ApiCallback<String>() {
                @Override
                public void onSuccess(String data) {
                    if (!isUsable()) return;
                    setLoading(false);
                    oldInput.setText("");
                    newInput.setText("");
                    confirmationInput.setText("");
                    showStatus(data);
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "ChangePasswordActivity", "Không thể đổi mật khẩu", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị yêu cầu đổi mật khẩu");
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!loading);
        oldInput.setEnabled(!loading);
        newInput.setEnabled(!loading);
        confirmationInput.setEnabled(!loading);
    }

    private void showStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    private String rawText(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
