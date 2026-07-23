package com.example.smartkid.ui.student;

import android.content.Intent;
import android.net.Uri;
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
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.ui.common.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;
import org.json.JSONArray;

/** Khởi tạo thanh toán MoMo thật; không sinh giao dịch hay số tiền mẫu. */
public class PaymentActivity extends BaseActivity {
    public static final String EXTRA_AMOUNT = "payment_amount";
    public static final String EXTRA_DESCRIPTION = "payment_description";
    public static final String EXTRA_COURSE_ID = "payment_course_id";
    public static final String EXTRA_COURSE_TITLE = "payment_course_title";
    public static final String EXTRA_COURSE_IDS_JSON = "payment_course_ids_json";
    public static final String EXTRA_COURSE_TITLES_JSON = "payment_course_titles_json";
    private TextInputEditText amountInput;
    private TextInputEditText descriptionInput;
    private Button payButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private StudentFeatureRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_payment);
            repository = new StudentFeatureRepository(this);
            MaterialToolbar toolbar = findViewById(R.id.toolbarPayment);
            amountInput = findViewById(R.id.inputPaymentAmount);
            descriptionInput = findViewById(R.id.inputPaymentDescription);
            payButton = findViewById(R.id.buttonPayMomo);
            progressBar = findViewById(R.id.progressPayment);
            statusText = findViewById(R.id.textPaymentStatus);
            if (toolbar == null || amountInput == null || descriptionInput == null
                    || payButton == null || progressBar == null || statusText == null) {
                throw new IllegalStateException("Giao diện thanh toán thiếu thành phần bắt buộc");
            }
            toolbar.setNavigationOnClickListener(view -> finish());
            payButton.setOnClickListener(view -> paySafely());
            findViewById(R.id.buttonPaymentHistory).setOnClickListener(view -> openHistory());
            prefillFromIntent();
        } catch (Exception exception) {
            AppLogger.error(this, "PaymentActivity", "Không thể tạo thanh toán", exception);
            showErrorDialog("Không thể mở chức năng thanh toán");
        }
    }

    private void prefillFromIntent() {
        if (getIntent() == null) return;
        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        String description = getIntent().getStringExtra(EXTRA_DESCRIPTION);
        if (amount > 0) amountInput.setText(String.valueOf(Math.round(amount)));
        if (description != null) descriptionInput.setText(description);
    }

    private void paySafely() {
        try {
            String rawAmount = textOf(amountInput).replaceAll("[^0-9]", "");
            if (rawAmount.isEmpty()) {
                showStatus("Vui lòng nhập số tiền");
                return;
            }
            double amount = Double.parseDouble(rawAmount);
            if (amount < 10_000) {
                showStatus("Số tiền tối thiểu là 10.000 ₫");
                return;
            }
            setLoading(true);
            statusText.setVisibility(View.GONE);
            JSONArray courseIds = courseIdsFromIntent();
            JSONArray courseTitles = courseTitlesFromIntent();
            repository.initiateMomo(amount, "", textOf(descriptionInput), courseIds, courseTitles,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            if (!isUsable()) return;
                            setLoading(false);
                            String target = SafeJson.string(data, "", "payUrl", "deeplink", "qrCodeUrl");
                            if (target.isEmpty()) {
                                showStatus(SafeJson.string(data,
                                        "Cổng thanh toán chưa trả về liên kết", "message", "detail"));
                                return;
                            }
                            openPaymentUrl(target);
                        }

                        @Override
                        public void onError(ApiError error) {
                            if (!isUsable()) return;
                            setLoading(false);
                            showStatus(error == null ? getString(R.string.unknown_error)
                                    : error.getMessage());
                        }
                    });
        } catch (NumberFormatException exception) {
            showStatus("Số tiền không hợp lệ");
        } catch (Exception exception) {
            AppLogger.error(this, "PaymentActivity", "Không thể thanh toán", exception);
            setLoading(false);
            showStatus(getString(R.string.unknown_error));
        }
    }

    private JSONArray courseIdsFromIntent() {
        JSONArray values = jsonArrayExtra(EXTRA_COURSE_IDS_JSON);
        if (values.length() == 0 && getIntent() != null) {
            String single = getIntent().getStringExtra(EXTRA_COURSE_ID);
            if (single != null && !single.trim().isEmpty()) values.put(single.trim());
        }
        return values;
    }

    private JSONArray courseTitlesFromIntent() {
        JSONArray values = jsonArrayExtra(EXTRA_COURSE_TITLES_JSON);
        if (values.length() == 0 && getIntent() != null) {
            String single = getIntent().getStringExtra(EXTRA_COURSE_TITLE);
            if (single != null && !single.trim().isEmpty()) values.put(single.trim());
        }
        return values;
    }

    private JSONArray jsonArrayExtra(String key) {
        try {
            String raw = getIntent() == null ? null : getIntent().getStringExtra(key);
            return raw == null || raw.trim().isEmpty() ? new JSONArray() : new JSONArray(raw);
        } catch (Exception exception) {
            AppLogger.error(this, "PaymentActivity", "Không thể đọc danh sách khóa học", exception);
            return new JSONArray();
        }
    }

    private void openPaymentUrl(String target) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target)));
            showStatus("Đã mở MoMo. Sau khi thanh toán, hãy quay lại và kiểm tra lịch sử.");
        } catch (Exception exception) {
            AppLogger.error(this, "PaymentActivity", "Không thể mở MoMo", exception);
            showErrorDialog("Thiết bị không thể mở liên kết thanh toán");
        }
    }

    private void openHistory() {
        try {
            Intent intent = new Intent(this, FeatureListActivity.class);
            intent.putExtra(FeatureListActivity.EXTRA_MODE, FeatureListActivity.MODE_PAYMENTS);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "PaymentActivity", "Không thể mở lịch sử", exception);
            showErrorDialog("Không thể mở lịch sử thanh toán");
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        payButton.setEnabled(!loading);
        amountInput.setEnabled(!loading);
        descriptionInput.setEnabled(!loading);
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
