package com.example.smartkid.feature.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.List;

/** Admin-owned management list backed by real APIs, with admin-only actions. */
public class AdminManagementActivity extends BaseActivity {
    public static final String EXTRA_SPEC_KEY = "admin_spec_key";

    private FeatureSpec spec;
    private ManagementRepository repository;
    private FeatureItemAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyText;
    private View refreshButton;
    private SwipeRefreshLayout refreshLayout;

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
            repository = new ManagementRepository(this);
            MaterialToolbar toolbar = findViewById(R.id.toolbarFeatureList);
            progressBar = findViewById(R.id.progressFeatureList);
            emptyText = findViewById(R.id.textFeatureListEmpty);
            refreshButton = findViewById(R.id.buttonFeatureAction);
            refreshLayout = findViewById(R.id.refreshFeatureList);
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
            emptyText.setText(R.string.no_server_data);
            adapter = new FeatureItemAdapter(this);
            list.setAdapter(adapter);
            list.setEmptyView(emptyText);
            list.setOnItemClickListener((parent, row, position, id) -> showItem(adapter.getItem(position)));
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.filter(s == null ? "" : s.toString());
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
            ((TextView) refreshButton).setText("Tạo mới");
            refreshButton.setOnClickListener(view -> openCreate());
            android.view.MenuItem refresh = toolbar.getMenu().add(R.string.refresh);
            refresh.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
            refresh.setOnMenuItemClickListener(item -> {
                loadSafely();
                return true;
            });
        } else {
            ((TextView) refreshButton).setText(R.string.refresh);
            refreshButton.setOnClickListener(view -> loadSafely());
        }
    }

    private boolean supportsCreate() {
        return "admin_users".equals(spec == null ? "" : spec.getActionKind());
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
        setLoading(true);
        repository.load(spec.getEndpoint(), new ApiCallback<List<FeatureItem>>() {
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
        });
    }

    private void showItem(FeatureItem item) {
        if (item == null) return;
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(item.getTitle())
                    .setMessage(friendlyDetail(item))
                    .setNegativeButton("Đóng", null);
            if ("admin_users".equals(spec.getActionKind()) && !item.getId().isEmpty()) {
                builder.setPositiveButton("Thao tác", (dialog, which) -> showActions(item));
            }
            builder.show();
        } catch (Exception exception) {
            AppLogger.error(this, "AdminManagementActivity", "Không thể hiện chi tiết", exception);
            showErrorDialog("Không thể đọc chi tiết dữ liệu");
        }
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
            appendLine(detail, "", SafeJson.string(source, item.getDetail(), "message"));
            appendLine(detail, "Thời gian", shortTime(SafeJson.string(source, "", "created_at")));
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
        String[] labels = new String[]{"Khóa tài khoản", "Mở khóa tài khoản"};
        new AlertDialog.Builder(this).setTitle("Chọn thao tác")
                .setItems(labels, (dialog, which) -> confirmAction(item, labels[which]))
                .show();
    }

    private void confirmAction(FeatureItem item, String label) {
        new AlertDialog.Builder(this).setTitle(label)
                .setMessage("Thực hiện “" + label + "” với “" + item.getTitle() + "”? Dữ liệu sẽ được cập nhật trên server.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Xác nhận", (dialog, which) -> performAction(item, label))
                .show();
    }

    private void performAction(FeatureItem item, String label) {
        try {
            if (!"admin_users".equals(spec.getActionKind())) return;
            String endpoint = "account/admin/users/" + item.getId() + "/";
            JSONObject body = new JSONObject();
            body.put("is_active", label.startsWith("Mở"));
            setLoading(true);
            repository.action(Request.Method.PATCH, endpoint, body, new ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject data) {
                    if (!isUsable()) return;
                    showShortMessage("Đã cập nhật trên server");
                    loadSafely();
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "AdminManagementActivity", "Không thể thao tác", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị thao tác quản lý");
        }
    }

    private void setLoading(boolean loading) {
        if (!loading && refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
        boolean swiping = loading && refreshLayout != null && refreshLayout.isRefreshing();
        progressBar.setVisibility(loading && !swiping ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!loading);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
