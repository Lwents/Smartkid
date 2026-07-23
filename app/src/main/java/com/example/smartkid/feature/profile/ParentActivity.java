package com.example.smartkid.feature.profile;

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
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.domain.BusinessRules;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

public class ParentActivity extends BaseActivity {
    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText phoneInput;
    private TextInputEditText relationInput;
    private Button saveButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private StudentFeatureRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.profile_activity_parent);
            repository = new StudentFeatureRepository(this);
            MaterialToolbar toolbar = findViewById(R.id.toolbarParent);
            nameInput = findViewById(R.id.inputParentName);
            emailInput = findViewById(R.id.inputParentEmail);
            phoneInput = findViewById(R.id.inputParentPhone);
            relationInput = findViewById(R.id.inputParentRelation);
            saveButton = findViewById(R.id.buttonSaveParent);
            progressBar = findViewById(R.id.progressParent);
            statusText = findViewById(R.id.textParentStatus);
            if (toolbar == null || nameInput == null || emailInput == null || phoneInput == null
                    || relationInput == null || saveButton == null || progressBar == null
                    || statusText == null) {
                throw new IllegalStateException("Giao diện phụ huynh chưa đầy đủ");
            }
            toolbar.setNavigationOnClickListener(view -> finish());
            saveButton.setOnClickListener(view -> saveSafely());
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "ParentActivity", "Không thể tạo màn hình", exception);
            showErrorDialog("Không thể mở thông tin phụ huynh");
        }
    }

    private void loadSafely() {
        setLoading(true);
        repository.loadParent(new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                setLoading(false);
                nameInput.setText(SafeJson.string(data, "", "name"));
                emailInput.setText(SafeJson.string(data, "", "email"));
                phoneInput.setText(SafeJson.string(data, "", "phone"));
                relationInput.setText(SafeJson.string(data, "", "relationship"));
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
        String email = textOf(emailInput);
        String phone = textOf(phoneInput);
        if (!email.isEmpty() && !BusinessRules.isEmail(email)) {
            showStatus("Email phụ huynh không đúng định dạng");
            return;
        }
        if (!phone.isEmpty() && !phone.matches("^[0-9+]{9,15}$")) {
            showStatus("Số điện thoại phụ huynh không đúng định dạng");
            return;
        }
        setLoading(true);
        repository.updateParent(textOf(nameInput), email, phone, textOf(relationInput),
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        setLoading(false);
                        showStatus(getString(R.string.parent_saved));
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false);
                        handleApiError(error);
                    }
                });
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
