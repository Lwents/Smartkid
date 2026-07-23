package com.example.smartkid.ui.student;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.R;
import com.example.smartkid.core.AppConstants;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.core.SafeJson;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.ExamRepository;
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.ui.common.BaseActivity;
import com.example.smartkid.ui.common.FeatureItemAdapter;
import com.example.smartkid.ui.courses.CatalogActivity;
import com.example.smartkid.ui.courses.CourseDetailActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.List;

/** Một khung danh sách thống nhất cho các trang dữ liệu đọc từ API học viên. */
public class FeatureListActivity extends BaseActivity {
    public static final String EXTRA_MODE = "feature_mode";
    public static final String MODE_LEARNING_PATH = "learning_path";
    public static final String MODE_PAYMENTS = "payments";
    public static final String MODE_NOTIFICATIONS = "notifications";
    public static final String MODE_CERTIFICATES = "certificates";

    private String mode;
    private MaterialToolbar toolbar;
    private ProgressBar progressBar;
    private TextView emptyText;
    private Button actionButton;
    private FeatureItemAdapter adapter;
    private StudentFeatureRepository featureRepository;
    private ExamRepository examRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_feature_list);
            mode = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_MODE);
            if (!isModeValid(mode)) {
                showErrorDialog("Chức năng không hợp lệ");
                finish();
                return;
            }
            featureRepository = new StudentFeatureRepository(this);
            examRepository = new ExamRepository(this);
            bindViews();
            configureMode();
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "FeatureListActivity", "Không thể tạo danh sách", exception);
            showErrorDialog("Không thể mở chức năng");
        }
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarFeatureList);
        progressBar = findViewById(R.id.progressFeatureList);
        emptyText = findViewById(R.id.textFeatureListEmpty);
        actionButton = findViewById(R.id.buttonFeatureAction);
        TextInputEditText searchInput = findViewById(R.id.inputFeatureSearch);
        ListView listView = findViewById(R.id.listFeatures);
        if (toolbar == null || progressBar == null || emptyText == null || actionButton == null
                || searchInput == null || listView == null) {
            throw new IllegalStateException("Giao diện danh sách thiếu thành phần bắt buộc");
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        adapter = new FeatureItemAdapter(this);
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyText);
        listView.setOnItemClickListener((parent, view, position, id) ->
                openItemSafely(adapter.getItem(position)));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void configureMode() {
        if (MODE_LEARNING_PATH.equals(mode)) {
            toolbar.setTitle(R.string.learning_path);
            emptyText.setText(R.string.no_learning_path);
            actionButton.setText(R.string.learning_analysis);
            actionButton.setOnClickListener(view -> openActivity(LearningAnalysisActivity.class));
        } else if (MODE_PAYMENTS.equals(mode)) {
            toolbar.setTitle(R.string.payment_history);
            emptyText.setText(R.string.no_payments);
            actionButton.setText(R.string.make_payment);
            actionButton.setOnClickListener(view -> openActivity(PaymentActivity.class));
        } else if (MODE_NOTIFICATIONS.equals(mode)) {
            toolbar.setTitle(R.string.notifications);
            emptyText.setText(R.string.no_notifications);
            actionButton.setText(R.string.mark_all_read);
            actionButton.setOnClickListener(view -> markAllRead());
        } else {
            toolbar.setTitle(R.string.certificates);
            emptyText.setText(R.string.no_certificates);
            actionButton.setVisibility(View.GONE);
        }
    }

    private void loadSafely() {
        try {
            setLoading(true);
            ApiCallback<List<FeatureItem>> callback = listCallback();
            if (MODE_LEARNING_PATH.equals(mode)) featureRepository.loadLearningPath(callback);
            else if (MODE_PAYMENTS.equals(mode)) featureRepository.loadPayments(callback);
            else if (MODE_NOTIFICATIONS.equals(mode)) featureRepository.loadNotifications(callback);
            else examRepository.loadCertificates(callback);
        } catch (Exception exception) {
            AppLogger.error(this, "FeatureListActivity", "Không thể tải danh sách", exception);
            setLoading(false);
            showErrorDialog("Không thể tải dữ liệu");
        }
    }

    private ApiCallback<List<FeatureItem>> listCallback() {
        return new ApiCallback<List<FeatureItem>>() {
            @Override
            public void onSuccess(List<FeatureItem> data) {
                if (!isUsable()) return;
                setLoading(false);
                adapter.setItems(data);
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        };
    }

    private void openItemSafely(FeatureItem item) {
        if (item == null) return;
        try {
            if (MODE_LEARNING_PATH.equals(mode)) {
                Intent intent = new Intent(this, CourseDetailActivity.class);
                intent.putExtra(AppConstants.EXTRA_COURSE_ID, item.getId());
                intent.putExtra(AppConstants.EXTRA_COURSE_TITLE, item.getTitle());
                startActivity(intent);
            } else if (MODE_NOTIFICATIONS.equals(mode)) {
                showNotification(item);
            } else if (MODE_CERTIFICATES.equals(mode)) {
                showCertificate(item);
            } else {
                showInfo(item.getTitle(), item.getSubtitle() + "\n" + item.getDetail()
                        + "\n" + item.getStatus());
            }
        } catch (Exception exception) {
            AppLogger.error(this, "FeatureListActivity", "Không thể mở mục", exception);
            showErrorDialog("Không thể mở dữ liệu đã chọn");
        }
    }

    private void showNotification(FeatureItem item) {
        new AlertDialog.Builder(this)
                .setTitle(item.getTitle())
                .setMessage(item.getDetail())
                .setPositiveButton("Đã đọc", (dialog, which) -> {
                    if (!item.getId().isEmpty()) markRead(item.getId());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCertificate(FeatureItem item) {
        JSONObject source = item.getSource();
        String url = SafeJson.string(source, "", "pdf", "image");
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(item.getTitle())
                .setMessage(item.getSubtitle() + "\n" + item.getDetail())
                .setNegativeButton(R.string.cancel, null);
        if (!url.isEmpty()) {
            builder.setPositiveButton(R.string.open_certificate, (dialog, which) -> openUrl(url));
        } else {
            builder.setPositiveButton("Đã hiểu", null);
        }
        builder.show();
    }

    private void markRead(String id) {
        setLoading(true);
        featureRepository.markNotificationRead(id, new ApiCallback<Boolean>() {
            @Override public void onSuccess(Boolean data) { if (isUsable()) loadSafely(); }
            @Override public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void markAllRead() {
        setLoading(true);
        featureRepository.markAllNotificationsRead(new ApiCallback<Boolean>() {
            @Override public void onSuccess(Boolean data) { if (isUsable()) loadSafely(); }
            @Override public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("Đã hiểu", null).show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception exception) {
            AppLogger.error(this, "FeatureListActivity", "Không thể mở liên kết", exception);
            showErrorDialog("Thiết bị không có ứng dụng mở liên kết này");
        }
    }

    private void openActivity(Class<?> destination) {
        try { startActivity(new Intent(this, destination)); }
        catch (Exception exception) {
            AppLogger.error(this, "FeatureListActivity", "Không thể chuyển trang", exception);
            showErrorDialog("Không thể mở chức năng");
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        actionButton.setEnabled(!loading);
    }

    private boolean isModeValid(String value) {
        return MODE_LEARNING_PATH.equals(value) || MODE_PAYMENTS.equals(value)
                || MODE_NOTIFICATIONS.equals(value) || MODE_CERTIFICATES.equals(value);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
