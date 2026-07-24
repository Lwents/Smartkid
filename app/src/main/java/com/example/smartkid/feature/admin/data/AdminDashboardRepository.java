package com.example.smartkid.feature.admin.data;

import android.content.Context;

import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.feature.admin.model.AdminDashboardData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Loads the structured data used by the admin dashboard instead of flattening JSON. */
public final class AdminDashboardRepository {
    private final Context appContext;
    private final ApiClient apiClient;

    public AdminDashboardRepository(Context context) {
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
    }

    public void load(ApiCallback<AdminDashboardData> callback) {
        apiClient.get("admin/dashboard/", true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    callback.onSuccess(parse(response));
                } catch (Exception exception) {
                    AppLogger.error(appContext, "AdminDashboardRepository",
                            "Không thể đọc dashboard quản trị", exception);
                    callback.onError(new ApiError(0,
                            "Dữ liệu dashboard quản trị không hợp lệ", false));
                }
            }

            @Override
            public void onError(ApiError error) {
                callback.onError(error);
            }
        });
    }

    private AdminDashboardData parse(JSONObject response) {
        JSONObject root = response == null ? new JSONObject() : response;
        JSONObject kpis = object(root, "kpis");
        JSONObject active = object(root, "activeUsers");
        JSONObject security = object(root, "security");
        JSONObject system = object(root, "system");
        JSONObject backup = object(system, "backup");

        AdminDashboardData.Kpis parsedKpis = new AdminDashboardData.Kpis(
                SafeJson.integer(kpis, 0, "dau"),
                SafeJson.integer(kpis, 0, "signups7d"),
                SafeJson.decimal(kpis, 0, "gmvToday"),
                SafeJson.integer(kpis, 0, "txToday"),
                SafeJson.decimal(kpis, 0, "refundRate7d"),
                SafeJson.integer(kpis, 0, "approvalsPending"));

        AdminDashboardData.ActiveUsers parsedActive = new AdminDashboardData.ActiveUsers(
                SafeJson.integer(active, 0, "count"),
                SafeJson.integer(active, 10, "windowMinutes"));

        AdminDashboardData.Security parsedSecurity = new AdminDashboardData.Security(
                SafeJson.integer(security, 0, "failedLogins24h"),
                SafeJson.integer(security, 0, "lockedAccounts"),
                SafeJson.integer(security, 0, "sslDaysToExpire"));

        AdminDashboardData.SystemHealth parsedSystem = new AdminDashboardData.SystemHealth(
                rounded(system, "cpuP95"), rounded(system, "ramP95"), rounded(system, "disk"),
                SafeJson.string(backup, "Chưa có", "lastRun"),
                SafeJson.string(backup, "Chưa có bản sao lưu", "status"));

        return new AdminDashboardData(parsedKpis, parseCourses(root),
                parseTransactions(root), parsedActive, parsedSecurity, parsedSystem);
    }

    private List<AdminDashboardData.CourseItem> parseCourses(JSONObject root) {
        List<AdminDashboardData.CourseItem> result = new ArrayList<>();
        JSONArray values = SafeJson.array(root, "topCourses");
        for (int index = 0; index < values.length(); index++) {
            JSONObject item = values.optJSONObject(index);
            if (item == null) continue;
            result.add(new AdminDashboardData.CourseItem(
                    SafeJson.string(item, "", "id"),
                    SafeJson.string(item, "Khóa học", "title"),
                    SafeJson.integer(item, 0, "enrollments")));
        }
        return result;
    }

    private List<AdminDashboardData.TransactionItem> parseTransactions(JSONObject root) {
        List<AdminDashboardData.TransactionItem> result = new ArrayList<>();
        JSONArray values = SafeJson.array(root, "recentTransactions");
        for (int index = 0; index < values.length(); index++) {
            JSONObject item = values.optJSONObject(index);
            if (item == null) continue;
            result.add(new AdminDashboardData.TransactionItem(
                    SafeJson.string(item, "", "id"),
                    SafeJson.string(item, "N/A", "user"),
                    SafeJson.string(item, "N/A", "course"),
                    SafeJson.decimal(item, 0, "amount"),
                    SafeJson.string(item, "", "status")));
        }
        return result;
    }

    private static JSONObject object(JSONObject parent, String key) {
        JSONObject value = parent == null ? null : parent.optJSONObject(key);
        return value == null ? new JSONObject() : value;
    }

    private static int rounded(JSONObject object, String key) {
        return (int) Math.round(SafeJson.decimal(object, 0, key));
    }
}
