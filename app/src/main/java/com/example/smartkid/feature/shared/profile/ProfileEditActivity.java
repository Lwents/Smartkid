package com.example.smartkid.feature.shared.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.domain.BusinessRules;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

/** Chỉnh sửa các trường hồ sơ mà frontend web hỗ trợ qua /account/profile/. */
public class ProfileEditActivity extends BaseActivity {
    private TextInputEditText fullNameInput;
    private TextInputEditText emailInput;
    private TextInputEditText phoneInput;
    private Button saveButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private AuthRepository repository;
    // Chỉ học sinh mới có lớp đang học và địa chỉ.
    private boolean studentProfile;
    private View studentFields;
    private android.widget.Spinner classSpinner;
    private TextInputEditText addressInput;
    private static final String[] CLASS_VALUES = {"", "1", "2", "3", "4", "5"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.profile_activity_profile_edit);
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
        saveButton = findViewById(R.id.buttonSaveProfile);
        progressBar = findViewById(R.id.progressProfileEdit);
        statusText = findViewById(R.id.textProfileEditStatus);
        studentFields = findViewById(R.id.groupProfileStudentFields);
        classSpinner = findViewById(R.id.spinnerProfileClass);
        addressInput = findViewById(R.id.inputProfileAddress);
        studentProfile = com.example.smartkid.common.navigation.UserRole.STUDENT
                == com.example.smartkid.common.navigation.UserRole.fromString(
                new com.example.smartkid.data.local.SessionManager(this).getUser().getRole());
        if (studentFields != null) {
            studentFields.setVisibility(studentProfile ? View.VISIBLE : View.GONE);
        }
        if (studentProfile && classSpinner != null) {
            classSpinner.setAdapter(new android.widget.ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"Chưa chọn lớp", "Lớp 1", "Lớp 2", "Lớp 3", "Lớp 4", "Lớp 5"}));
        }
        if (fullNameInput == null || emailInput == null || phoneInput == null
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
                fullNameInput.setText(SafeJson.string(data, "", "full_name", "fullName", "display_name"));
                emailInput.setText(SafeJson.string(data, "", "email"));
                phoneInput.setText(SafeJson.string(data, "", "phone"));
                if (studentProfile) {
                    JSONObject metadata = data.optJSONObject("metadata");
                    selectClass(value(data, metadata, "class_name"));
                    addressInput.setText(value(data, metadata, "address"));
                }
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    /** "3" hoặc "Lớp 3" -> chọn đúng mục trong danh sách lớp. */
    private void selectClass(String rawValue) {
        String digits = rawValue == null ? "" : rawValue.replaceAll("[^1-5]", "");
        for (int index = 1; index < CLASS_VALUES.length; index++) {
            if (CLASS_VALUES[index].equals(digits)) {
                classSpinner.setSelection(index);
                return;
            }
        }
        classSpinner.setSelection(0);
    }

    /** Giá trị có thể nằm ở gốc response hoặc trong metadata. */
    private String value(JSONObject data, JSONObject metadata, String key) {
        String direct = SafeJson.string(data, "", key);
        return direct.isEmpty() ? SafeJson.string(metadata, "", key) : direct;
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
            if (studentProfile) {
                int position = classSpinner.getSelectedItemPosition();
                body.put("class_name", position >= 0 && position < CLASS_VALUES.length
                        ? CLASS_VALUES[position] : "");
                body.put("address", textOf(addressInput));
            }
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
