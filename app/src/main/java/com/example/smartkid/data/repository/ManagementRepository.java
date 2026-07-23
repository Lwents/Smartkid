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

/** Đọc và thao tác các API quản lý vốn có nhiều kiểu response khác nhau. */
public class ManagementRepository {
    private static final String[] ARRAY_KEYS = {
            "results", "items", "courses", "users", "students", "games", "transactions",
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
                    callback.onSuccess(parse(data));
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

    private List<FeatureItem> parse(Object data) {
        List<FeatureItem> result = new ArrayList<>();
        if (data instanceof JSONArray) {
            appendArray(result, (JSONArray) data);
            return result;
        }
        JSONObject object = data instanceof JSONObject ? (JSONObject) data : new JSONObject();
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
