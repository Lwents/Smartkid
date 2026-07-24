package com.example.smartkid.data.repository;

import android.content.Context;

import com.android.volley.Request;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Đọc và thao tác các API quản lý vốn có nhiều kiểu response khác nhau. */
public class ManagementRepository {
    private static final String[] ARRAY_KEYS = {
            "results", "items", "courses", "users", "students", "transactions",
            "logs", "notifications", "backups", "sessions", "data", "questions", "feedback"
    };

    private final Context appContext;
    private final ApiClient apiClient;

    public ManagementRepository(Context context) {
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
    }

    public void load(String endpoint, ApiCallback<List<FeatureItem>> callback) {
        apiClient.getValue(endpoint, true, new ApiCallback<Object>() {
            @Override
            public void onSuccess(Object data) {
                try {
                    callback.onSuccess(parse(endpoint, data));
                } catch (Exception exception) {
                    AppLogger.error(appContext, "ManagementRepository", "Không thể đọc API", exception);
                    callback.onError(new ApiError(0, "Dữ liệu quản lý không hợp lệ", false));
                }
            }

            @Override public void onError(ApiError error) { callback.onError(error); }
        });
    }

    public void action(int method, String endpoint, JSONObject body,
                       ApiCallback<JSONObject> callback) {
        apiClient.request(method, endpoint, body, true, callback);
    }

    public void loadObject(String endpoint, ApiCallback<JSONObject> callback) {
        apiClient.get(endpoint, true, callback);
    }

    private List<FeatureItem> parse(String endpoint, Object data) {
        List<FeatureItem> result = new ArrayList<>();
        if (data instanceof JSONArray) {
            appendArray(result, (JSONArray) data);
            return result;
        }
        JSONObject object = data instanceof JSONObject ? (JSONObject) data : new JSONObject();
        if (isSystemHealthEndpoint(endpoint)) {
            return parseSystemHealth(object);
        }
        JSONArray array = firstArray(object);
        if (array != null) {
            appendArray(result, array);
            return result;
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = object.opt(key);
            if (value instanceof JSONObject) {
                JSONObject child = (JSONObject) value;
                result.add(itemFromObject(key, child));
            } else if (value instanceof JSONArray) {
                appendArray(result, (JSONArray) value);
            } else {
                result.add(new FeatureItem(key, readable(key), display(value), "", "", object));
            }
        }
        return result;
    }

    private boolean isSystemHealthEndpoint(String endpoint) {
        return endpoint != null && endpoint.startsWith("admin/system/health/");
    }

    private List<FeatureItem> parseSystemHealth(JSONObject response) {
        List<FeatureItem> result = new ArrayList<>();
        appendHealthMetric(result, response.optJSONObject("cpu"), "cpu", "CPU",
                "Mức sử dụng bộ xử lý", false);
        appendHealthMetric(result, response.optJSONObject("ram"), "ram", "Bộ nhớ RAM",
                "Bộ nhớ đang được sử dụng", false);
        appendHealthMetric(result, response.optJSONObject("disk"), "disk", "Ổ đĩa",
                "Dung lượng lưu trữ đã sử dụng", true);
        appendBackupHealth(result, response.optJSONObject("backup"));
        return result;
    }

    private void appendHealthMetric(List<FeatureItem> target, JSONObject metric, String id,
                                    String title, String description, boolean disk) {
        JSONObject source = metric == null ? new JSONObject() : metric;
        double current = SafeJson.decimal(source, -1, "current");
        double p95 = SafeJson.decimal(source, -1, "p95");
        String subtitle = current < 0 ? "Chưa có dữ liệu" : percent(current) + " hiện tại";
        String detail = description;
        if (p95 >= 0) detail += " • P95 " + percent(p95);
        String status = current < 0 ? "Không có dữ liệu"
                : disk ? diskStatus(current) : resourceStatus(current);
        target.add(new FeatureItem(id, title, subtitle, detail, status, source));
    }

    private void appendBackupHealth(List<FeatureItem> target, JSONObject backup) {
        JSONObject source = backup == null ? new JSONObject() : backup;
        String rawStatus = SafeJson.string(source, "unknown", "status");
        String lastBackup = SafeJson.string(source, "", "lastBackup", "last_backup");
        String subtitle;
        String detail;
        String status;
        if ("no_backup".equalsIgnoreCase(rawStatus)) {
            subtitle = "Chưa có bản sao lưu";
            detail = "Hãy cấu hình sao lưu định kỳ để bảo vệ dữ liệu hệ thống";
            status = "Cần thiết lập";
        } else if ("failed".equalsIgnoreCase(rawStatus) || "error".equalsIgnoreCase(rawStatus)) {
            subtitle = "Lần sao lưu gần nhất thất bại";
            detail = lastBackup.isEmpty() ? "Chưa ghi nhận thời gian sao lưu" : "Thời gian: " + lastBackup;
            status = "Có lỗi";
        } else {
            subtitle = lastBackup.isEmpty() ? "Sao lưu đã được cấu hình" : "Gần nhất: " + lastBackup;
            detail = "Trạng thái máy chủ sao lưu";
            status = "Hoạt động";
        }
        target.add(new FeatureItem("backup", "Sao lưu hệ thống", subtitle, detail, status, source));
    }

    private String resourceStatus(double value) {
        if (value >= 85) return "Mức sử dụng cao";
        if (value >= 70) return "Cần theo dõi";
        return "Ổn định";
    }

    private String diskStatus(double value) {
        if (value >= 90) return "Gần hết dung lượng";
        if (value >= 75) return "Sắp đầy";
        return "Còn đủ dung lượng";
    }

    private String percent(double value) {
        return String.format(Locale.getDefault(), "%.1f%%", value);
    }

    private void appendArray(List<FeatureItem> target, JSONArray array) {
        for (int index = 0; index < array.length(); index++) {
            Object value = array.opt(index);
            if (value instanceof JSONObject) target.add(itemFromObject(String.valueOf(index), (JSONObject) value));
            else target.add(new FeatureItem(String.valueOf(index), display(value), "", "", "", new JSONObject()));
        }
    }

    private FeatureItem itemFromObject(String fallbackId, JSONObject item) {
        String id = SafeJson.string(item, fallbackId, "id", "uuid", "user_id", "course_id", "jti");
        String title = SafeJson.string(item, "", "title", "name", "full_name", "display_name",
                "username", "studentName", "student", "player_name", "email", "date");
        if (title.isEmpty()) title = "Mục " + id;
        String subtitle = SafeJson.string(item, "", "email", "role", "subject", "game_type_display",
                "course_title", "plan_name", "category", "type");
        String detail = SafeJson.string(item, "", "description", "message", "content", "bio",
                "teacherName", "status_message", "gross", "net");
        String status = SafeJson.string(item, "", "status", "state", "role");
        if (item.has("is_active")) status = SafeJson.bool(item, false, "is_active") ? "Đang hoạt động" : "Đã khóa";
        if (item.has("published")) status = SafeJson.bool(item, false, "published") ? "Đã xuất bản" : "Bản nháp";
        return new FeatureItem(id, title, subtitle, detail, status, item);
    }

    private JSONArray firstArray(JSONObject object) {
        for (String key : ARRAY_KEYS) {
            JSONArray array = object.optJSONArray(key);
            if (array != null) return array;
        }
        return null;
    }

    private String display(Object value) {
        if (value == null || value == JSONObject.NULL) return "—";
        String raw = String.valueOf(value);
        return raw.length() > 400 ? raw.substring(0, 400) + "…" : raw;
    }

    private String readable(String key) {
        if (key == null) return "Thông tin";
        return key.replace('_', ' ').trim();
    }
}
