package com.example.smartkid.feature.shared.notification;

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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.common.ui.FeatureItemAdapter;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.ExamRepository;
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.feature.student.course.CourseDetailActivity;
import com.example.smartkid.feature.student.course.LessonPlayerActivity;
import com.example.smartkid.feature.student.ai.LearningAnalysisActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.List;
import com.example.smartkid.common.util.SwipeRefreshFix;

/** Một khung danh sách thống nhất cho các trang dữ liệu đọc từ API học viên. */
public class FeatureListActivity extends BaseActivity {
    public static final String EXTRA_MODE = "feature_mode";
    public static final String MODE_LEARNING_PATH = "learning_path";
    public static final String MODE_NOTIFICATIONS = "notifications";
    public static final String MODE_CERTIFICATES = "certificates";

    private String mode;
    private MaterialToolbar toolbar;
    private ProgressBar progressBar;
    private TextView emptyText;
    private Button actionButton;
    private SwipeRefreshLayout refreshLayout;
    private FeatureItemAdapter adapter;
    private NotificationItemAdapter notificationAdapter;
    private TextView notificationSummary;
    private StudentFeatureRepository featureRepository;
    private ExamRepository examRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            mode = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_MODE);
            if (!isModeValid(mode)) {
                setContentView(R.layout.common_activity_feature_list);
                showErrorDialog("Chức năng không hợp lệ");
                finish();
                return;
            }
            setContentView(MODE_NOTIFICATIONS.equals(mode)
                    ? R.layout.notification_activity_list
                    : R.layout.common_activity_feature_list);
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
        refreshLayout = findViewById(R.id.refreshFeatureList);
        notificationSummary = findViewById(R.id.textNotificationSummary);
        SwipeRefreshFix.attach(refreshLayout);
        TextInputEditText searchInput = findViewById(R.id.inputFeatureSearch);
        ListView listView = findViewById(R.id.listFeatures);
        if (toolbar == null || progressBar == null || emptyText == null || actionButton == null
                || refreshLayout == null || searchInput == null || listView == null) {
            throw new IllegalStateException("Giao diện danh sách thiếu thành phần bắt buộc");
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        refreshLayout.setOnRefreshListener(this::loadSafely);
        if (MODE_NOTIFICATIONS.equals(mode)) {
            notificationAdapter = new NotificationItemAdapter(this);
            listView.setAdapter(notificationAdapter);
        } else {
            adapter = new FeatureItemAdapter(this);
            listView.setAdapter(adapter);
        }
        listView.setEmptyView(emptyText);
        listView.setOnItemClickListener((parent, view, position, id) ->
                openItemSafely(itemAt(position)));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterItems(s == null ? "" : s.toString());
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
        } else if (MODE_NOTIFICATIONS.equals(mode)) {
            toolbar.setTitle(R.string.notifications);
            emptyText.setText(R.string.no_notifications);
            actionButton.setText(R.string.notification_mark_all_read);
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
                setItems(data);
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
        JSONObject source = item.getSource();
        boolean wasUnread = !NotificationUiFormatter.isRead(source);
        if (wasUnread && !item.getId().isEmpty()) markReadOptimistically(item);

        BottomSheetDialog sheet = new BottomSheetDialog(
                this, R.style.ThemeOverlay_Smartkid_NotificationSheet);
        View content = getLayoutInflater().inflate(R.layout.notification_sheet_detail, null);
        sheet.setContentView(content);

        ((TextView) content.findViewById(R.id.textNotificationSheetCategory))
                .setText(NotificationUiFormatter.categoryLabel(source));
        ((TextView) content.findViewById(R.id.textNotificationSheetTime))
                .setText(NotificationUiFormatter.timeLabel(source));
        ((TextView) content.findViewById(R.id.textNotificationSheetTitle))
                .setText(NotificationUiFormatter.displayTitle(source, item.getTitle()));
        ((TextView) content.findViewById(R.id.textNotificationSheetMessage))
                .setText(item.getDetail().isEmpty()
                        ? getString(R.string.notification_no_content)
                        : item.getDetail());

        String context = NotificationUiFormatter.contextLabel(source, item.getTitle());
        TextView contextView = content.findViewById(R.id.textNotificationSheetContext);
        View contextCard = content.findViewById(R.id.layoutNotificationSheetContext);
        contextView.setText(context);
        contextCard.setVisibility(context.isEmpty() ? View.GONE : View.VISIBLE);

        View openLesson = content.findViewById(R.id.buttonNotificationOpenLesson);
        String lessonId = NotificationUiFormatter.lessonId(source);
        String courseId = NotificationUiFormatter.courseId(source);
        openLesson.setVisibility(lessonId.isEmpty() || courseId.isEmpty()
                ? View.GONE : View.VISIBLE);
        openLesson.setOnClickListener(view -> {
            sheet.dismiss();
            openNotificationLesson(source, courseId, lessonId);
        });
        content.findViewById(R.id.buttonNotificationClose)
                .setOnClickListener(view -> sheet.dismiss());
        sheet.show();
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

    private void markReadOptimistically(FeatureItem item) {
        try {
            item.getSource().put("is_read", true);
            refreshNotificationPresentation();
        } catch (Exception ignored) { }
        featureRepository.markNotificationRead(item.getId(), new ApiCallback<Boolean>() {
            @Override public void onSuccess(Boolean data) { }
            @Override public void onError(ApiError error) {
                if (!isUsable()) return;
                try { item.getSource().put("is_read", false); }
                catch (Exception ignored) { }
                refreshNotificationPresentation();
            }
        });
    }

    private void markAllRead() {
        if (notificationAdapter != null && notificationAdapter.getUnreadCount() == 0) return;
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

    private void openNotificationLesson(JSONObject source, String courseId, String lessonId) {
        try {
            Intent intent = new Intent(this, LessonPlayerActivity.class);
            intent.putExtra(AppConstants.EXTRA_COURSE_ID, courseId);
            intent.putExtra(AppConstants.EXTRA_LESSON_ID, lessonId);
            String title = NotificationUiFormatter.lessonTitle(source);
            if (!title.isEmpty()) intent.putExtra(AppConstants.EXTRA_LESSON_TITLE, title);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "FeatureListActivity", "Không thể mở bài học", exception);
            showErrorDialog("Không thể mở bài học từ thông báo này");
        }
    }

    private FeatureItem itemAt(int position) {
        return notificationAdapter != null
                ? notificationAdapter.getItem(position)
                : adapter == null ? null : adapter.getItem(position);
    }

    private void setItems(List<FeatureItem> data) {
        if (notificationAdapter != null) {
            notificationAdapter.setItems(data);
            refreshNotificationPresentation();
        } else if (adapter != null) {
            adapter.setItems(data);
        }
    }

    private void filterItems(String query) {
        if (notificationAdapter != null) notificationAdapter.filter(query);
        else if (adapter != null) adapter.filter(query);
    }

    private void refreshNotificationPresentation() {
        if (notificationAdapter == null) return;
        notificationAdapter.notifyDataSetChanged();
        int unread = notificationAdapter.getUnreadCount();
        if (notificationSummary != null) {
            notificationSummary.setText(unread == 0
                    ? getString(R.string.notification_all_caught_up)
                    : getResources().getQuantityString(
                            R.plurals.notification_unread_count, unread, unread));
        }
        actionButton.setEnabled(unread > 0);
        actionButton.setText(unread > 0
                ? R.string.notification_mark_all_read
                : R.string.notification_all_read);
    }

    private void setLoading(boolean loading) {
        if (loading && refreshLayout != null && refreshLayout.isRefreshing()) {
            actionButton.setEnabled(false);
            return;
        }
        if (!loading && refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        actionButton.setEnabled(!loading);
    }

    private boolean isModeValid(String value) {
        return MODE_LEARNING_PATH.equals(value)
                || MODE_NOTIFICATIONS.equals(value) || MODE_CERTIFICATES.equals(value);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
