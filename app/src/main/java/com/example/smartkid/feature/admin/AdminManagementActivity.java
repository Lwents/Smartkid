package com.example.smartkid.feature.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.example.smartkid.R;
import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.ui.FeatureItemAdapter;
import com.example.smartkid.common.ui.FeatureSpec;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.ManagementRepository;
import com.example.smartkid.feature.admin.users.AdminUserCreateActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import com.example.smartkid.common.util.SwipeRefreshFix;

/** Admin-owned management list backed by real APIs, with admin-only actions. */
public class AdminManagementActivity extends BaseActivity {
    public static final String EXTRA_SPEC_KEY = "admin_spec_key";
    private static final long REALTIME_REFRESH_MS = 30_000L;

    private FeatureSpec spec;
    private ManagementRepository repository;
    private FeatureItemAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyText;
    private View refreshButton;
    private SwipeRefreshLayout refreshLayout;
    private String currentSearchQuery = "";
    private boolean loadFailed;
    private boolean loading;
    private int loadGeneration;
    private final Handler realtimeHandler = new Handler(Looper.getMainLooper());
    private final Runnable realtimeRefreshTask = new Runnable() {
        @Override
        public void run() {
            if (!isUsable() || !supportsRealtimeRefresh()) return;
            if (!loading) loadSafely(true);
            realtimeHandler.postDelayed(this, REALTIME_REFRESH_MS);
        }
    };

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.common_slide_in_left, R.anim.common_slide_out_right);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (repository != null && spec != null && spec.isAvailable()) loadSafely();
    }

    @Override
    protected void onResume() {
        super.onResume();
        realtimeHandler.removeCallbacks(realtimeRefreshTask);
        if (supportsRealtimeRefresh()) {
            realtimeHandler.postDelayed(realtimeRefreshTask, REALTIME_REFRESH_MS);
        }
    }

    @Override
    protected void onPause() {
        realtimeHandler.removeCallbacks(realtimeRefreshTask);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        realtimeHandler.removeCallbacks(realtimeRefreshTask);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.common_activity_feature_list);
            String key = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_SPEC_KEY);
            spec = AdminManagementSpec.get(key);
            UserRole role = UserRole.fromString(new SessionManager(this).getUser().getRole());
            if (spec == null || !spec.isAvailable() || !spec.isAllowedForRole(role)) {
                showErrorDialog("Chức năng quản lý không hợp lệ");
                finish();
                return;
            }
            if (AdminSettingsRules.supports(key)) {
                startActivity(AdminSettingsActivity.createIntent(this, key));
                finish();
                return;
            }
            repository = new ManagementRepository(this);
            MaterialToolbar toolbar = findViewById(R.id.toolbarFeatureList);
            progressBar = findViewById(R.id.progressFeatureList);
            emptyText = findViewById(R.id.textFeatureListEmpty);
            refreshButton = findViewById(R.id.buttonFeatureAction);
            refreshLayout = findViewById(R.id.refreshFeatureList);
            SwipeRefreshFix.attach(refreshLayout);
            TextInputEditText search = findViewById(R.id.inputFeatureSearch);
            ListView list = findViewById(R.id.listFeatures);
            if (toolbar == null || progressBar == null || emptyText == null || refreshButton == null
                    || refreshLayout == null || search == null || list == null) {
                throw new IllegalStateException("Giao diện quản lý chưa đầy đủ");
            }
            toolbar.setTitle(spec.getTitle());
            toolbar.setNavigationOnClickListener(view -> finish());
            refreshLayout.setOnRefreshListener(this::loadSafely);
            configurePrimaryAction(toolbar);
            adapter = new FeatureItemAdapter(this);
            list.setAdapter(adapter);
            list.setEmptyView(emptyText);
            list.setOnItemClickListener((parent, row, position, id) -> showItem(adapter.getItem(position)));
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s == null ? "" : s.toString();
                    adapter.filter(currentSearchQuery);
                    updateEmptyState();
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "AdminManagementActivity", "Không thể tạo chức năng", exception);
            showErrorDialog("Không thể mở dữ liệu quản lý");
        }
    }

    private void configurePrimaryAction(MaterialToolbar toolbar) {
        if (supportsCreate()) {
            ((TextView) refreshButton).setText(R.string.admin_create_new);
            refreshButton.setOnClickListener(view -> openCreate());
            addRefreshAction(toolbar);
        } else if (supportsBackup()) {
            ((TextView) refreshButton).setText(R.string.admin_create_backup);
            refreshButton.setOnClickListener(view -> confirmCreateBackup());
            addRefreshAction(toolbar);
        } else if (supportsNotifications()) {
            ((TextView) refreshButton).setText(R.string.admin_send_notification);
            refreshButton.setOnClickListener(view -> showSendNotification());
            addReadAllAction(toolbar);
            addRefreshAction(toolbar);
        } else {
            ((TextView) refreshButton).setText(R.string.refresh);
            refreshButton.setOnClickListener(view -> loadSafely());
        }
    }

    private void addRefreshAction(MaterialToolbar toolbar) {
        android.view.MenuItem refresh = toolbar.getMenu().add(R.string.refresh);
        refresh.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        refresh.setOnMenuItemClickListener(item -> {
            loadSafely();
            return true;
        });
    }

    private void addReadAllAction(MaterialToolbar toolbar) {
        android.view.MenuItem readAll = toolbar.getMenu().add(R.string.admin_notification_read_all);
        readAll.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        readAll.setOnMenuItemClickListener(item -> {
            markAllNotificationsRead();
            return true;
        });
    }

    private boolean supportsCreate() {
        return "admin_users".equals(spec == null ? "" : spec.getActionKind());
    }

    private boolean supportsBackup() {
        return "admin_backups".equals(spec == null ? "" : spec.getActionKind());
    }

    private boolean supportsNotifications() {
        return "admin_notifications".equals(spec == null ? "" : spec.getKey());
    }

    private boolean supportsRealtimeRefresh() {
        return AdminManagementSpec.isRealtimeList(spec == null ? "" : spec.getKey());
    }

    private void openCreate() {
        try {
            startActivity(new Intent(this, AdminUserCreateActivity.class));
        } catch (Exception exception) {
            AppLogger.error(this, "AdminManagementActivity", "Không thể mở tạo mới", exception);
            showErrorDialog("Không thể mở biểu mẫu tạo mới");
        }
    }

    private void loadSafely() {
        loadSafely(false);
    }

    private void loadSafely(boolean quiet) {
        int generation = ++loadGeneration;
        loadFailed = false;
        if (quiet) {
            loading = true;
        } else {
            setLoading(true);
        }
        if (supportsNotifications()) {
            loadNotifications(generation);
            return;
        }
        repository.load(spec.getEndpoint(), new ApiCallback<List<FeatureItem>>() {
            @Override
            public void onSuccess(List<FeatureItem> data) {
                if (!isCurrentLoad(generation)) return;
                showLoadedItems(data);
            }

            @Override
            public void onError(ApiError error) {
                if (!isCurrentLoad(generation)) return;
                showLoadError(error, quiet);
            }
        });
    }

    /** Hộp thư admin và lịch sử gửi nằm ở hai API thật, nên ghép chúng thành một danh sách. */
    private void loadNotifications(int generation) {
        NotificationLoadResult result = new NotificationLoadResult(generation);
        loadNotificationSource(spec.getEndpoint(), result);
        loadNotificationSource(AdminManagementSpec.notificationHistoryEndpoint(), result);
    }

    private void loadNotificationSource(String endpoint, NotificationLoadResult result) {
        repository.load(endpoint, new ApiCallback<List<FeatureItem>>() {
            @Override
            public void onSuccess(List<FeatureItem> data) {
                result.complete(data, null);
            }

            @Override
            public void onError(ApiError error) {
                result.complete(null, error);
            }
        });
    }

    private void showLoadedItems(List<FeatureItem> data) {
        loadFailed = false;
        setLoading(false);
        adapter.setItems(data);
        adapter.filter(currentSearchQuery);
        updateEmptyState();
    }

    private void showLoadError(ApiError error) {
        showLoadError(error, false);
    }

    private void showLoadError(ApiError error, boolean quiet) {
        if (quiet && adapter != null && adapter.getCount() > 0) {
            loadFailed = false;
            setLoading(false);
            return;
        }
        loadFailed = true;
        setLoading(false);
        updateEmptyState();
        if (!quiet) handleApiError(error);
    }

    private boolean isCurrentLoad(int generation) {
        return generation == loadGeneration && isUsable();
    }

    private void showItem(FeatureItem item) {
        if (item == null) return;
        try {
            if ("admin_courses".equals(spec.getActionKind())) {
                openCourseVideos(item);
                return;
            }
            if (supportsNotifications() && item.getSource().has("is_read")) {
                markNotificationRead(item);
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(item.getTitle())
                    .setMessage(friendlyDetail(item))
                    .setNegativeButton("Đóng", null);
            if ("admin_users".equals(spec.getActionKind()) && !item.getId().isEmpty()) {
                builder.setPositiveButton("Thao tác", (dialog, which) -> showActions(item));
            } else if ("admin_sessions".equals(spec.getActionKind())
                    && !item.getId().isEmpty()) {
                builder.setPositiveButton("Thu hồi phiên",
                        (dialog, which) -> confirmSessionRevoke(item));
            }
            builder.show();
        } catch (Exception exception) {
            AppLogger.error(this, "AdminManagementActivity", "Không thể hiện chi tiết", exception);
            showErrorDialog("Không thể đọc chi tiết dữ liệu");
        }
    }

    private void openCourseVideos(FeatureItem course) {
        if (course == null || course.getId().isEmpty()) {
            showErrorDialog(getString(R.string.admin_course_video_invalid_course));
            return;
        }
        try {
            Intent intent = new Intent(this, AdminCourseVideosActivity.class);
            intent.putExtra(AdminCourseVideosActivity.EXTRA_COURSE_ID, course.getId());
            intent.putExtra(AdminCourseVideosActivity.EXTRA_COURSE_TITLE, course.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "AdminManagementActivity",
                    "Không thể mở video khóa học", exception);
            showErrorDialog(getString(R.string.admin_course_video_open_error));
        }
    }

    private void markNotificationRead(FeatureItem item) {
        if (item == null || item.getId().isEmpty()
                || SafeJson.bool(item.getSource(), false, "is_read", "isRead")) return;
        repository.action(Request.Method.PATCH,
                "admin/notifications/" + item.getId() + "/read/", new JSONObject(),
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (isUsable()) loadSafely();
                    }

                    @Override public void onError(ApiError error) { }
                });
    }

    /** Chi tiết dạng "Nhãn: giá trị" thay vì dump JSON thô. */
    private String friendlyDetail(FeatureItem item) {
        JSONObject source = item.getSource();
        StringBuilder detail = new StringBuilder();
        String specKey = spec == null ? "" : spec.getKey();
        if ("admin_courses".equals(specKey)) {
            appendLine(detail, "Giáo viên", SafeJson.string(source, "", "teacherName", "teacherId"));
            appendLine(detail, "Khối lớp", SafeJson.string(source, "", "grade"));
            appendLine(detail, "Số bài học", SafeJson.string(source, "", "lessonsCount"));
            appendLine(detail, "Lượt ghi danh", SafeJson.string(source, "", "enrollments"));
            appendLine(detail, "Trạng thái", statusLabel(SafeJson.string(source, "", "status")));
            appendLine(detail, "Ngày tạo", shortTime(SafeJson.string(source, "", "createdAt", "created_on")));
        } else if ("admin_users".equals(specKey)) {
            appendLine(detail, "Email", SafeJson.string(source, "", "email"));
            appendLine(detail, "Vai trò", roleLabel(SafeJson.string(source, "", "role")));
            appendLine(detail, "Trạng thái", item.getStatus());
            appendLine(detail, "Ngày tạo", shortTime(SafeJson.string(source, "", "created_on", "createdAt", "date_joined")));
        } else if (specKey.startsWith("admin_report")) {
            appendLine(detail, "Giá trị", item.getSubtitle());
        } else if ("admin_notifications".equals(specKey)) {
            if ("notification.broadcast".equals(SafeJson.string(source, "", "action"))) {
                JSONObject history = source.optJSONObject("details");
                appendLine(detail, getString(R.string.admin_notification_history_sender),
                        SafeJson.string(source, "", "userEmail"));
                appendLine(detail, getString(R.string.admin_notification_history_audience),
                        notificationAudienceDisplay(
                                SafeJson.string(history, "all", "audience")));
                appendLine(detail, getString(R.string.admin_notification_history_recipients),
                        getString(R.string.admin_notification_history_recipient_count,
                                SafeJson.integer(history, 0, "recipientCount")));
                appendLine(detail, "Trạng thái", item.getStatus());
                appendLine(detail, "Thời gian",
                        shortTime(SafeJson.string(source, "", "timestamp")));
            } else {
                appendLine(detail, "", SafeJson.string(source, item.getDetail(), "message"));
                appendLine(detail, "Nhóm", item.getSubtitle());
                appendLine(detail, "Trạng thái",
                        SafeJson.bool(source, false, "is_read", "isRead")
                                ? "Đã đọc" : "Chưa đọc");
                appendLine(detail, "Thời gian",
                        shortTime(SafeJson.string(source, "", "created_at")));
            }
        } else if ("admin_activity".equals(specKey)) {
            appendLine(detail, "Người thực hiện", SafeJson.string(source, "", "userEmail"));
            appendLine(detail, "Thời gian", shortTime(SafeJson.string(source, "", "timestamp")));
            appendLine(detail, "Trạng thái", item.getStatus());
            appendLine(detail, "Địa chỉ IP", SafeJson.string(source, "", "ip"));
            appendLine(detail, "Thiết bị", SafeJson.string(source, "", "userAgent"));
            appendLine(detail, "Chi tiết", readableJson(source.optJSONObject("details")));
        } else if ("admin_sessions".equals(specKey)) {
            appendLine(detail, "Tài khoản", SafeJson.string(source, "", "userEmail"));
            appendLine(detail, "Thiết bị", SafeJson.string(source, item.getTitle(), "device"));
            appendLine(detail, "Địa chỉ IP", SafeJson.string(source, "", "ip"));
            appendLine(detail, "Hoạt động gần nhất",
                    shortTime(SafeJson.string(source, "", "lastActiveAt")));
            appendLine(detail, "Hết hạn", shortTime(SafeJson.string(source, "", "expiresAt")));
        } else if ("admin_backups".equals(specKey)) {
            appendLine(detail, "Tệp", SafeJson.string(source, "", "fileName"));
            appendLine(detail, "Dung lượng", item.getSubtitle());
            appendLine(detail, "Trạng thái", item.getStatus());
            appendLine(detail, "Thời gian", shortTime(SafeJson.string(source, "", "createdAt")));
        } else {
            appendLine(detail, "", item.getSubtitle());
            appendLine(detail, "", item.getDetail());
            appendLine(detail, "", item.getStatus());
        }
        return detail.length() == 0
                ? (item.getSubtitle() + "\n" + item.getDetail()).trim() : detail.toString();
    }

    private void appendLine(StringBuilder target, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (target.length() > 0) target.append('\n');
        if (!label.isEmpty()) target.append(label).append(": ");
        target.append(value.trim());
    }

    private String readableJson(JSONObject source) {
        if (source == null || source.length() == 0) return "";
        StringBuilder result = new StringBuilder();
        java.util.Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = source.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            if (result.length() > 0) result.append(" • ");
            result.append(key.replace('_', ' ')).append(": ").append(String.valueOf(value));
        }
        return result.toString();
    }

    private String statusLabel(String status) {
        switch (status) {
            case "published": return "Đã xuất bản";
            case "draft": return "Bản nháp";
            case "archived": return "Đã lưu trữ";
            default: return status;
        }
    }

    private String roleLabel(String role) {
        switch (role) {
            case "admin": return "Quản trị viên";
            case "teacher":
            case "instructor": return "Giáo viên";
            case "student": return "Học sinh";
            default: return role;
        }
    }

    /** "2026-07-26T10:42:59...+00:00" (UTC) -> "26/07/2026 17:42" theo giờ máy. */
    private String shortTime(String isoValue) {
        if (isoValue == null || isoValue.trim().isEmpty()) return "";
        String raw = isoValue.trim();
        try {
            java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date parsed = parser.parse(raw.substring(0, Math.min(19, raw.length())));
            java.text.SimpleDateFormat printer = new java.text.SimpleDateFormat(
                    "dd/MM/yyyy HH:mm", java.util.Locale.US);
            printer.setTimeZone(java.util.TimeZone.getDefault());
            return parsed == null ? raw : printer.format(parsed);
        } catch (Exception ignored) {
            return raw.replace('T', ' ');
        }
    }

    private void showActions(FeatureItem item) {
        if (item == null || !"admin_users".equals(spec.getActionKind())) return;
        boolean active = SafeJson.bool(item.getSource(), true, "is_active", "isActive");
        List<String> labels = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        labels.add(getString(active ? R.string.admin_user_lock : R.string.admin_user_unlock));
        actions.add(active ? "lock" : "unlock");
        labels.add(getString(R.string.admin_user_change_role));
        actions.add("role");
        labels.add(getString(R.string.admin_user_reset_password));
        actions.add("password");

        new AlertDialog.Builder(this).setTitle(R.string.admin_user_actions_title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if ("role".equals(action)) {
                        showRoleChooser(item);
                    } else if ("password".equals(action)) {
                        showPasswordReset(item);
                    } else {
                        confirmStatusChange(item, "unlock".equals(action));
                    }
                })
                .show();
    }

    private void showRoleChooser(FeatureItem item) {
        String[] roles = {
                AdminUserActions.ROLE_STUDENT,
                AdminUserActions.ROLE_INSTRUCTOR,
                AdminUserActions.ROLE_ADMIN,
        };
        String[] labels = {
                getString(R.string.admin_user_role_student),
                getString(R.string.admin_user_role_instructor),
                getString(R.string.admin_user_role_admin),
        };
        String current = normalizeRole(SafeJson.string(item.getSource(), "student", "role"));
        int selected = 0;
        for (int index = 0; index < roles.length; index++) {
            if (roles[index].equals(current)) selected = index;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_user_role_title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (roles[which].equals(current)) {
                        showShortMessage(getString(R.string.admin_user_role_unchanged));
                    } else {
                        confirmRoleChange(item, roles[which], labels[which]);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmRoleChange(FeatureItem item, String role, String roleName) {
        String message = getString(R.string.admin_user_role_confirm, item.getTitle(), roleName);
        if (AdminUserActions.ROLE_ADMIN.equals(role)) {
            message += "\n\n" + getString(R.string.admin_user_role_admin_warning);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_user_role_confirm_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.admin_confirm_action,
                        (dialog, which) -> updateUserRole(item, role))
                .show();
    }

    private void updateUserRole(FeatureItem item, String role) {
        if (!AdminUserActions.isSupportedRole(role)) return;
        try {
            JSONObject body = new JSONObject();
            body.put("role", role);
            setLoading(true);
            repository.action(Request.Method.PATCH,
                    AdminUserActions.userEndpoint(item.getId()), body,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            if (!isUsable()) return;
                            showShortMessage(getString(R.string.admin_user_role_updated));
                            loadSafely();
                        }

                        @Override
                        public void onError(ApiError error) {
                            handleUserActionError(error);
                        }
                    });
        } catch (Exception exception) {
            failUserAction("Không thể chuẩn bị thay đổi vai trò", exception);
        }
    }

    private void showPasswordReset(FeatureItem item) {
        View form = LayoutInflater.from(this).inflate(
                R.layout.admin_dialog_reset_password, null, false);
        TextInputLayout newPasswordLayout = form.findViewById(R.id.layoutAdminNewPassword);
        TextInputLayout confirmPasswordLayout = form.findViewById(R.id.layoutAdminConfirmPassword);
        TextInputEditText newPasswordInput = form.findViewById(R.id.inputAdminNewPassword);
        TextInputEditText confirmPasswordInput = form.findViewById(R.id.inputAdminConfirmPassword);
        if (newPasswordLayout == null || confirmPasswordLayout == null
                || newPasswordInput == null || confirmPasswordInput == null) {
            showErrorDialog(getString(R.string.admin_user_action_error));
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.admin_user_password_title, item.getTitle()))
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.admin_user_password_submit, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String password = inputText(newPasswordInput);
                    String confirmation = inputText(confirmPasswordInput);
                    AdminUserActions.PasswordIssue issue =
                            AdminUserActions.validatePassword(password, confirmation);
                    newPasswordLayout.setError(null);
                    confirmPasswordLayout.setError(null);
                    if (issue == AdminUserActions.PasswordIssue.REQUIRED) {
                        newPasswordLayout.setError(
                                getString(R.string.admin_user_password_required));
                        return;
                    }
                    if (issue == AdminUserActions.PasswordIssue.TOO_SHORT) {
                        newPasswordLayout.setError(
                                getString(R.string.admin_user_password_short));
                        return;
                    }
                    if (issue == AdminUserActions.PasswordIssue.MISMATCH) {
                        confirmPasswordLayout.setError(
                                getString(R.string.admin_user_password_mismatch));
                        return;
                    }
                    dialog.dismiss();
                    resetUserPassword(item, password);
                }));
        dialog.show();
    }

    private void resetUserPassword(FeatureItem item, String password) {
        try {
            JSONObject body = new JSONObject();
            body.put("new_password", password);
            setLoading(true);
            repository.action(Request.Method.POST,
                    AdminUserActions.passwordEndpoint(item.getId()), body,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            if (!isUsable()) return;
                            setLoading(false);
                            showShortMessage(getString(R.string.admin_user_password_updated));
                        }

                        @Override
                        public void onError(ApiError error) {
                            handleUserActionError(error);
                        }
                    });
        } catch (Exception exception) {
            failUserAction("Không thể chuẩn bị đặt lại mật khẩu", exception);
        }
    }

    private void confirmCreateBackup() {
        new AlertDialog.Builder(this)
                .setTitle("Tạo bản sao lưu")
                .setMessage("Hệ thống sẽ tạo một tệp sao lưu dữ liệu thật. Quá trình có thể mất một lúc.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Tạo sao lưu", (dialog, which) -> createBackup())
                .show();
    }

    private void showSendNotification() {
        View form = LayoutInflater.from(this).inflate(
                R.layout.admin_dialog_send_notification, null, false);
        MaterialAutoCompleteTextView audience = form.findViewById(
                R.id.inputAdminNotificationAudience);
        TextInputLayout titleLayout = form.findViewById(R.id.layoutAdminNotificationTitle);
        TextInputLayout messageLayout = form.findViewById(R.id.layoutAdminNotificationMessage);
        TextInputEditText title = form.findViewById(R.id.inputAdminNotificationTitle);
        TextInputEditText message = form.findViewById(R.id.inputAdminNotificationMessage);
        if (audience == null || titleLayout == null || messageLayout == null
                || title == null || message == null) {
            showErrorDialog("Không thể mở biểu mẫu thông báo");
            return;
        }
        String[] audiences = {
                getString(R.string.admin_notification_audience_all),
                getString(R.string.admin_notification_audience_students),
                getString(R.string.admin_notification_audience_teachers),
        };
        audience.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, audiences));
        audience.setText(audiences[0], false);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.admin_notification_send_title)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.admin_send_notification, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String titleValue = inputText(title);
                    String messageValue = inputText(message);
                    titleLayout.setError(titleValue.isEmpty()
                            ? getString(R.string.admin_notification_required) : null);
                    messageLayout.setError(messageValue.isEmpty()
                            ? getString(R.string.admin_notification_required) : null);
                    if (titleValue.isEmpty() || messageValue.isEmpty()) return;
                    String audienceLabel = audience.getText() == null
                            ? "" : audience.getText().toString().trim();
                    dialog.dismiss();
                    sendNotification(titleValue, messageValue,
                            notificationAudienceCode(audienceLabel));
                }));
        dialog.show();
    }

    private String notificationAudienceCode(String label) {
        if (getString(R.string.admin_notification_audience_students).equals(label)) {
            return "student";
        }
        if (getString(R.string.admin_notification_audience_teachers).equals(label)) {
            return "instructor";
        }
        return "all";
    }

    private String notificationAudienceDisplay(String audience) {
        if ("student".equals(audience)) {
            return getString(R.string.admin_notification_audience_students);
        }
        if ("instructor".equals(audience) || "teacher".equals(audience)) {
            return getString(R.string.admin_notification_audience_teachers);
        }
        return getString(R.string.admin_notification_audience_all);
    }

    private void sendNotification(String title, String message, String audience) {
        try {
            JSONObject body = new JSONObject()
                    .put("title", title)
                    .put("message", message)
                    .put("audience", audience)
                    .put("type", "info");
            setLoading(true);
            repository.action(Request.Method.POST, "admin/notifications/", body,
                    new ApiCallback<JSONObject>() {
                        @Override public void onSuccess(JSONObject data) {
                            if (!isUsable()) return;
                            int count = SafeJson.integer(data, 0, "created_count");
                            showShortMessage(count > 0
                                    ? getString(R.string.admin_notification_sent, count)
                                    : getString(R.string.admin_notification_sent_none));
                            loadSafely();
                        }

                        @Override public void onError(ApiError error) {
                            if (!isUsable()) return;
                            setLoading(false);
                            handleApiError(error);
                        }
                    });
        } catch (Exception exception) {
            AppLogger.error(this, "AdminManagementActivity",
                    "Không thể chuẩn bị thông báo", exception);
            setLoading(false);
            showErrorDialog(getString(R.string.admin_notification_send_error));
        }
    }

    private void markAllNotificationsRead() {
        setLoading(true);
        repository.action(Request.Method.PATCH, "admin/notifications/read-all/",
                new JSONObject(), new ApiCallback<JSONObject>() {
                    @Override public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        int count = SafeJson.integer(data, 0, "updated_count");
                        showShortMessage(getString(count > 0
                                ? R.string.admin_notification_read_all_done
                                : R.string.admin_notification_read_all_none));
                        loadSafely();
                    }

                    @Override public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false);
                        handleApiError(error);
                    }
                });
    }

    private void createBackup() {
        setLoading(true);
        JSONObject body = new JSONObject();
        try { body.put("notes", "Sao lưu từ ứng dụng Android"); }
        catch (Exception ignored) { }
        repository.action(Request.Method.POST, "admin/system/backups/", body,
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        showShortMessage("Đã tạo bản sao lưu");
                        loadSafely();
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false);
                        handleApiError(error);
                    }
                });
    }

    private void confirmSessionRevoke(FeatureItem item) {
        JSONObject source = item == null ? new JSONObject() : item.getSource();
        String account = SafeJson.string(source, "", "userEmail");
        String ip = SafeJson.string(source, "", "ip");
        String device = SafeJson.string(source, item == null ? "" : item.getTitle(), "device");
        String target = (account + "\n" + device + (ip.isEmpty() ? "" : " • IP " + ip)).trim();
        new AlertDialog.Builder(this)
                .setTitle("Thu hồi phiên đăng nhập")
                .setMessage(target + "\n\nThiết bị này sẽ không thể làm mới phiên đăng nhập sau khi bị thu hồi.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Thu hồi", (dialog, which) -> revokeSession(item))
                .show();
    }

    private void revokeSession(FeatureItem item) {
        setLoading(true);
        repository.action(Request.Method.DELETE,
                "admin/security/sessions/" + item.getId() + "/", new JSONObject(),
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        showShortMessage("Đã thu hồi phiên đăng nhập");
                        loadSafely();
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false);
                        handleApiError(error);
                    }
                });
    }

    private void confirmStatusChange(FeatureItem item, boolean activate) {
        String label = getString(activate
                ? R.string.admin_user_unlock : R.string.admin_user_lock);
        new AlertDialog.Builder(this).setTitle(label)
                .setMessage(getString(R.string.admin_user_status_confirm,
                        label, item.getTitle()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.admin_confirm_action,
                        (dialog, which) -> updateUserStatus(item, activate))
                .show();
    }

    private void updateUserStatus(FeatureItem item, boolean activate) {
        try {
            if (!"admin_users".equals(spec.getActionKind())) return;
            JSONObject body = new JSONObject();
            body.put("is_active", activate);
            setLoading(true);
            repository.action(Request.Method.PATCH,
                    AdminUserActions.userEndpoint(item.getId()), body,
                    new ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject data) {
                    if (!isUsable()) return;
                    showShortMessage(getString(R.string.admin_user_status_updated));
                    loadSafely();
                }

                @Override
                public void onError(ApiError error) {
                    handleUserActionError(error);
                }
            });
        } catch (Exception exception) {
            failUserAction("Không thể chuẩn bị thay đổi trạng thái", exception);
        }
    }

    private void handleUserActionError(ApiError error) {
        if (!isUsable()) return;
        setLoading(false);
        if (error == null) {
            showErrorDialog(getString(R.string.admin_user_action_error));
        } else {
            handleApiError(error);
        }
    }

    private void failUserAction(String logMessage, Exception exception) {
        AppLogger.error(this, "AdminManagementActivity", logMessage, exception);
        setLoading(false);
        showErrorDialog(getString(R.string.admin_user_action_error));
    }

    private static String inputText(TextInputEditText input) {
        Editable value = input == null ? null : input.getText();
        return value == null ? "" : value.toString();
    }

    private static String normalizeRole(String role) {
        if ("teacher".equalsIgnoreCase(role)) return AdminUserActions.ROLE_INSTRUCTOR;
        return role == null ? "" : role.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void updateEmptyState() {
        if (emptyText == null || adapter == null) return;
        if (loading) {
            emptyText.setVisibility(View.GONE);
            return;
        }
        int message = emptyMessage();
        if (loadFailed) {
            message = R.string.admin_empty_load_error;
        } else if (!currentSearchQuery.trim().isEmpty()
                && adapter.getUnfilteredCount() > 0 && adapter.getCount() == 0) {
            message = R.string.admin_empty_search;
        }
        emptyText.setText(message);
        emptyText.setVisibility(adapter.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private int emptyMessage() {
        String key = spec == null ? "" : spec.getKey();
        switch (key) {
            case "admin_active_users": return R.string.admin_empty_active_users;
            case "admin_users": return R.string.admin_empty_users;
            case "admin_courses": return R.string.admin_empty_courses;
            case "admin_activity": return R.string.admin_empty_activity;
            case "admin_sessions": return R.string.admin_empty_sessions;
            case "admin_backups": return R.string.admin_empty_backups;
            case "admin_notifications": return R.string.admin_empty_notifications;
            case "admin_health": return R.string.admin_empty_health;
            case "admin_report_learning": return R.string.admin_empty_learning_report;
            case "admin_report_content": return R.string.admin_empty_content_report;
            case "admin_dashboard": return R.string.admin_empty_dashboard;
            default: return R.string.admin_empty_generic;
        }
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        if (!loading && refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
        boolean swiping = loading && refreshLayout != null && refreshLayout.isRefreshing();
        progressBar.setVisibility(loading && !swiping ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!loading);
        if (loading && adapter != null && adapter.getCount() == 0 && emptyText != null) {
            emptyText.setVisibility(View.GONE);
        }
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }

    private final class NotificationLoadResult {
        private final int generation;
        private final List<FeatureItem> items = new ArrayList<>();
        private int remaining = 2;
        private int successes;
        private ApiError error;

        NotificationLoadResult(int generation) {
            this.generation = generation;
        }

        void complete(List<FeatureItem> data, ApiError sourceError) {
            if (!isCurrentLoad(generation)) return;
            if (sourceError == null) {
                successes++;
                if (data != null) items.addAll(data);
            } else if (error == null) {
                error = sourceError;
            }
            remaining--;
            if (remaining > 0) return;

            if (successes == 0 || (items.isEmpty() && error != null)) {
                showLoadError(error);
                return;
            }
            items.sort((first, second) -> notificationTimestamp(second)
                    .compareTo(notificationTimestamp(first)));
            showLoadedItems(items);
            if (error != null) {
                showShortMessage(getString(R.string.admin_notification_partial_load));
            }
        }
    }

    private String notificationTimestamp(FeatureItem item) {
        JSONObject source = item == null ? null : item.getSource();
        return SafeJson.string(source, "", "created_at", "timestamp", "createdAt");
    }
}
