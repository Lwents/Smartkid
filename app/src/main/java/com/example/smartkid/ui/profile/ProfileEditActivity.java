package com.example.smartkid.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.core.SafeJson;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.domain.BusinessRules;
import com.example.smartkid.ui.common.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

/** Chỉnh sửa các trường hồ sơ mà frontend web hỗ trợ qua /account/profile/. */
public class ProfileEditActivity extends BaseActivity {
    private TextInputEditText fullNameInput;
    private TextInputEditText emailInput;
    private TextInputEditText phoneInput;
    private TextInputEditText classInput;
    private TextInputEditText bioInput;
    private TextInputEditText addressInput;
    private Button saveButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private AuthRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_profile_edit);
            repository = new AuthRepository(this);
            bindViews();
            MaterialToolbar toolbar = findViewById(R.id.toolbarProfileEdit);
            if (toolbar == null) throw new IllegalStateException("Thiếu thanh tiêu đề hồ sơ");
            toolbar.setNavigationOnClickListener(view -> finish());
            saveButton.setOnClickListener(view -> saveSafely());
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "ProfileEditActivity", "Không thể tạo hồ sơ", exception);
            showErrorDialog("Không thể mở chỉnh sửa hồ sơ");
        }
    }

    private void bindViews() {
        fullNameInput = findViewById(R.id.inputProfileFullName);
        emailInput = findViewById(R.id.inputProfileEmail);
        phoneInput = findViewById(R.id.inputProfilePhone);
        classInput = findViewById(R.id.inputProfileClass);
        bioInput = findViewById(R.id.inputProfileBio);
        addressInput = findViewById(R.id.inputProfileAddress);
        saveButton = findViewById(R.id.buttonSaveProfile);
        progressBar = findViewById(R.id.progressProfileEdit);
        statusText = findViewById(R.id.textProfileEditStatus);
        if (fullNameInput == null || emailInput == null || phoneInput == null
                || classInput == null || bioInput == null || addressInput == null
                || saveButton == null || progressBar == null || statusText == null) {
            throw new IllegalStateException("Giao diện chỉnh hồ sơ chưa đầy đủ");
        }
    }

    private void loadSafely() {
        setLoading(true);
        repository.loadAccountProfile(new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                setLoading(false);
                JSONObject metadata = data.optJSONObject("metadata");
                fullNameInput.setText(SafeJson.string(data, "", "full_name", "fullName", "display_name"));
                emailInput.setText(SafeJson.string(data, "", "email"));
                phoneInput.setText(SafeJson.string(data, "", "phone"));
                classInput.setText(value(data, metadata, "class_name"));
                bioInput.setText(value(data, metadata, "bio"));
                addressInput.setText(value(data, metadata, "address"));
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void saveSafely() {
        try {
            String email = textOf(emailInput);
            if (!BusinessRules.isEmail(email)) {
                showStatus("Email không đúng định dạng");
                return;
            }
            String phone = textOf(phoneInput);
            if (!phone.isEmpty() && !phone.matches("^[0-9+]{9,15}$")) {
                showStatus("Số điện thoại không đúng định dạng");
                return;
            }
            JSONObject body = new JSONObject();
            body.put("full_name", textOf(fullNameInput));
            body.put("email", email);
            body.put("phone", phone);
            body.put("class_name", textOf(classInput));
            body.put("bio", textOf(bioInput));
            body.put("address", textOf(addressInput));
            setLoading(true);
            repository.updateAccountProfile(body, new ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject data) {
                    if (!isUsable()) return;
                    setLoading(false);
                    showStatus(getString(R.string.profile_saved));
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "ProfileEditActivity", "Không thể lưu hồ sơ", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị dữ liệu hồ sơ");
        }
    }

    private String value(JSONObject data, JSONObject metadata, String key) {
        String direct = SafeJson.string(data, "", key);
        return direct.isEmpty() ? SafeJson.string(metadata, "", key) : direct;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!loading);
    }

    private void showStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
