package com.example.smartkid.feature.admin;

import android.content.Intent;
import android.os.Bundle;

import com.example.smartkid.R;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.User;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.feature.admin.data.AdminDashboardRepository;
import com.example.smartkid.feature.admin.model.AdminDashboardData;
import com.example.smartkid.feature.auth.LoginActivity;
import com.example.smartkid.feature.management.ManagementFeatureActivity;
import com.example.smartkid.feature.management.ManagementSpec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

/** Hosts the Flutter dashboard while keeping authentication and data in the Android app. */
public final class AdminFlutterActivity extends FlutterActivity {
    private static final String CHANNEL_NAME = "com.example.smartkid/admin";

    private SessionManager sessionManager;
    private AdminDashboardRepository dashboardRepository;
    private AuthRepository authRepository;
    private ApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionManager = new SessionManager(this);
        dashboardRepository = new AdminDashboardRepository(this);
        authRepository = new AuthRepository(this);
        apiClient = ApiClient.getInstance(this);
    }

    @Override
    public String getInitialRoute() {
        return "/admin";
    }

    @Override
    public void configureFlutterEngine(FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL_NAME)
                .setMethodCallHandler(this::handleFlutterCall);
    }

    private void handleFlutterCall(MethodCall call, MethodChannel.Result result) {
        switch (call.method) {
            case "getSession":
                result.success(sessionPayload());
                break;
            case "loadDashboard":
                loadDashboard(result);
                break;
            case "loadActivityChart":
                loadActivityChart(call, result);
                break;
            case "openFeature":
                openFeature(call.argument("key"), result);
                break;
            case "logout":
                logout(result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private Map<String, Object> sessionPayload() {
        User user = sessionManager.getUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", user.getFullName());
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole());
        return payload;
    }

    private void loadDashboard(MethodChannel.Result result) {
        dashboardRepository.load(new ApiCallback<AdminDashboardData>() {
            @Override
            public void onSuccess(AdminDashboardData data) {
                runOnUiThread(() -> result.success(dashboardPayload(data)));
            }

            @Override
            public void onError(ApiError error) {
                runOnUiThread(() -> result.error(
                        "dashboard_error", error.getMessage(), error.getStatusCode()));
            }
        });
    }

    private Map<String, Object> dashboardPayload(AdminDashboardData data) {
        AdminDashboardData.Kpis kpis = data.getKpis();
        Map<String, Object> payload = new HashMap<>();
        payload.putAll(sessionPayload());

        Map<String, Object> kpiPayload = new HashMap<>();
        kpiPayload.put("dailyActiveUsers", kpis.getDailyActiveUsers());
        kpiPayload.put("signupsLastSevenDays", kpis.getSignupsLastSevenDays());
        kpiPayload.put("grossToday", kpis.getGrossToday());
        kpiPayload.put("transactionsToday", kpis.getTransactionsToday());
        kpiPayload.put("refundRate", kpis.getRefundRate());
        kpiPayload.put("approvalsPending", kpis.getApprovalsPending());
        payload.put("kpis", kpiPayload);

        Map<String, Object> activePayload = new HashMap<>();
        activePayload.put("count", data.getActiveUsers().getCount());
        activePayload.put("windowMinutes", data.getActiveUsers().getWindowMinutes());
        payload.put("activeUsers", activePayload);

        Map<String, Object> securityPayload = new HashMap<>();
        securityPayload.put("failedLogins", data.getSecurity().getFailedLogins());
        securityPayload.put("lockedAccounts", data.getSecurity().getLockedAccounts());
        securityPayload.put("sslDaysToExpire", data.getSecurity().getSslDaysToExpire());
        payload.put("security", securityPayload);

        Map<String, Object> systemPayload = new HashMap<>();
        systemPayload.put("cpuPercent", data.getSystemHealth().getCpuPercent());
        systemPayload.put("ramPercent", data.getSystemHealth().getRamPercent());
        systemPayload.put("diskPercent", data.getSystemHealth().getDiskPercent());
        systemPayload.put("backupLastRun", data.getSystemHealth().getBackupLastRun());
        systemPayload.put("backupStatus", data.getSystemHealth().getBackupStatus());
        payload.put("system", systemPayload);

        List<Map<String, Object>> courses = new ArrayList<>();
        for (AdminDashboardData.CourseItem course : data.getTopCourses()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", course.getId());
            item.put("title", course.getTitle());
            item.put("enrollments", course.getEnrollments());
            courses.add(item);
        }
        payload.put("topCourses", courses);

        List<Map<String, Object>> transactions = new ArrayList<>();
        for (AdminDashboardData.TransactionItem transaction : data.getRecentTransactions()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", transaction.getId());
            item.put("user", transaction.getUser());
            item.put("course", transaction.getCourse());
            item.put("amount", transaction.getAmount());
            item.put("status", transaction.getStatus());
            transactions.add(item);
        }
        payload.put("recentTransactions", transactions);
        return payload;
    }

    private void loadActivityChart(MethodCall call, MethodChannel.Result result) {
        String from = safe(call.argument("from"));
        String to = safe(call.argument("to"));
        String endpoint = "admin/reports/users/?type=timeseries"
                + (from.isEmpty() ? "" : "&from=" + from)
                + (to.isEmpty() ? "" : "&to=" + to);
        apiClient.getArray(endpoint, true, new ApiCallback<JSONArray>() {
            @Override
            public void onSuccess(JSONArray data) {
                List<Map<String, Object>> points = new ArrayList<>();
                for (int index = 0; index < data.length(); index++) {
                    JSONObject value = data.optJSONObject(index);
                    if (value == null) continue;
                    Map<String, Object> point = new HashMap<>();
                    point.put("date", value.optString("date", ""));
                    point.put("dau", value.optInt("dau", 0));
                    point.put("newUsers", value.optInt("newUsers", 0));
                    points.add(point);
                }
                runOnUiThread(() -> result.success(points));
            }

            @Override
            public void onError(ApiError error) {
                runOnUiThread(() -> result.error(
                        "chart_error", error.getMessage(), error.getStatusCode()));
            }
        });
    }

    private void openFeature(String key, MethodChannel.Result result) {
        ManagementSpec spec = ManagementSpec.get(key);
        if (spec == null) {
            result.success(actionResult(false, "Chức năng không tồn tại"));
            return;
        }
        if (!spec.isAllowedForRole(sessionManager.getUser().getRole())) {
            result.success(actionResult(false, "Tài khoản không có quyền mở chức năng này"));
            return;
        }
        if (!spec.isAvailable()) {
            result.success(actionResult(false, spec.getUnavailableReason()));
            return;
        }
        Intent intent = new Intent(this, ManagementFeatureActivity.class);
        intent.putExtra(ManagementFeatureActivity.EXTRA_SPEC_KEY, spec.getKey());
        startActivity(intent);
        overridePendingTransition(R.anim.common_slide_in_right, R.anim.common_slide_out_left);
        result.success(actionResult(true, ""));
    }

    private Map<String, Object> actionResult(boolean opened, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("opened", opened);
        payload.put("message", message == null ? "" : message);
        return payload;
    }

    private void logout(MethodChannel.Result result) {
        authRepository.logout(new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean ignored) {
                runOnUiThread(() -> {
                    result.success(true);
                    Intent intent = new Intent(AdminFlutterActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(ApiError error) {
                sessionManager.clear();
                onSuccess(true);
            }
        });
    }

    private static String safe(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
