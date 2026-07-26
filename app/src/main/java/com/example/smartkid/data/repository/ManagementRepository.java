package com.example.smartkid.data.repository;

import android.content.Context;

import com.android.volley.Request;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.remote.MultipartFilePart;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Đọc và thao tác các API quản lý vốn có nhiều kiểu response khác nhau. */
public class ManagementRepository {
    static final int MAX_PAGINATION_PAGES = 100;
    private static final String[] ITEM_ID_KEYS = {
            "id", "uuid", "user_id", "course_id", "jti"
    };
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
        PaginationState state = new PaginationState();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            deliverPaginationError(state, callback, "Đường dẫn API không hợp lệ");
            return;
        }
        loadPage(endpoint, endpoint, state, callback);
    }

    private void loadPage(String rootEndpoint, String endpoint, PaginationState state,
                          ApiCallback<List<FeatureItem>> callback) {
        if (!state.visit(endpoint)) {
            deliverPaginationError(state, callback,
                    "Máy chủ trả về vòng lặp khi phân trang dữ liệu");
            return;
        }
        apiClient.getValue(endpoint, true, new ApiCallback<Object>() {
            @Override
            public void onSuccess(Object data) {
                List<FeatureItem> pageItems;
                String rawNext = "";
                boolean paginated = false;
                try {
                    if (data instanceof JSONObject
                            && ((JSONObject) data).has("results")) {
                        JSONObject page = (JSONObject) data;
                        JSONArray results = page.optJSONArray("results");
                        if (results == null) {
                            throw new IllegalStateException(
                                    "Trường results của trang không phải mảng");
                        }
                        pageItems = new ArrayList<>();
                        appendArray(pageItems, results);
                        Object nextValue = page.opt("next");
                        if (nextValue != null && nextValue != JSONObject.NULL) {
                            rawNext = String.valueOf(nextValue).trim();
                        }
                        paginated = true;
                    } else {
                        if (state.getPageCount() > 1) {
                            throw new IllegalStateException(
                                    "Trang tiếp theo không có cấu trúc phân trang DRF");
                        }
                        pageItems = parse(rootEndpoint, data);
                    }
                } catch (Exception exception) {
                    AppLogger.error(appContext, "ManagementRepository", "Không thể đọc API", exception);
                    deliverPaginationError(state, callback,
                            "Dữ liệu quản lý không hợp lệ");
                    return;
                }

                appendUnique(state, pageItems);
                if (!paginated || rawNext.isEmpty()) {
                    deliverPaginationSuccess(state, callback);
                    return;
                }
                if (state.getPageCount() >= MAX_PAGINATION_PAGES) {
                    deliverPaginationError(state, callback,
                            "Danh sách vượt quá giới hạn " + MAX_PAGINATION_PAGES + " trang");
                    return;
                }

                final String nextEndpoint;
                try {
                    nextEndpoint = resolveNextEndpoint(
                            endpoint, rawNext, AppConstants.getApiBaseUrl());
                } catch (Exception exception) {
                    AppLogger.error(appContext, "ManagementRepository",
                            "Đường dẫn trang tiếp theo không hợp lệ", exception);
                    deliverPaginationError(state, callback,
                            "Đường dẫn phân trang của máy chủ không hợp lệ");
                    return;
                }
                loadPage(rootEndpoint, nextEndpoint, state, callback);
            }

            @Override
            public void onError(ApiError error) {
                if (state.finish()) callback.onError(error);
            }
        });
    }

    private void appendUnique(PaginationState state, List<FeatureItem> items) {
        if (items == null) return;
        for (FeatureItem item : items) {
            if (item == null) continue;
            state.add(item, stableItemKey(item));
        }
    }

    private String stableItemKey(FeatureItem item) {
        JSONObject source = item.getSource();
        for (String key : ITEM_ID_KEYS) {
            if (!source.has(key) || source.isNull(key)) continue;
            String value = String.valueOf(source.opt(key)).trim();
            if (!value.isEmpty()) return key + ':' + value;
        }
        return null;
    }

    private void deliverPaginationSuccess(PaginationState state,
                                          ApiCallback<List<FeatureItem>> callback) {
        if (state.finish()) callback.onSuccess(state.snapshot());
    }

    private void deliverPaginationError(PaginationState state,
                                        ApiCallback<List<FeatureItem>> callback,
                                        String message) {
        if (state.finish()) callback.onError(new ApiError(0, message, false));
    }

    static String resolveNextEndpoint(String currentEndpoint, String rawNext, String baseUrl)
            throws Exception {
        String nextValue = rawNext == null ? "" : rawNext.trim();
        if (nextValue.isEmpty()) throw new IllegalArgumentException("Thiếu URL trang tiếp theo");

        URI base = new URI(ensureTrailingSlash(baseUrl));
        URI supplied = new URI(nextValue);
        URI resolved;
        if (supplied.isAbsolute()) {
            resolved = supplied;
        } else if (nextValue.startsWith("?")) {
            String current = currentEndpoint == null ? "" : currentEndpoint.trim();
            while (current.startsWith("/")) current = current.substring(1);
            resolved = base.resolve(current).resolve(supplied);
        } else if (nextValue.startsWith("/")) {
            resolved = base.resolve(supplied);
        } else {
            resolved = base.resolve(supplied);
        }

        if (!sameOrigin(base, resolved)) {
            throw new IllegalArgumentException("URL phân trang nằm ngoài API đã cấu hình");
        }
        if (resolved.getRawFragment() != null) {
            throw new IllegalArgumentException("URL phân trang không được chứa fragment");
        }

        String basePath = ensureTrailingSlash(base.getRawPath());
        String nextPath = resolved.getRawPath() == null ? "" : resolved.getRawPath();
        if (!nextPath.startsWith(basePath)) {
            throw new IllegalArgumentException("URL phân trang nằm ngoài đường dẫn API");
        }
        String relativePath = nextPath.substring(basePath.length());
        if (relativePath.isEmpty()) {
            throw new IllegalArgumentException("URL phân trang không có endpoint");
        }
        String query = resolved.getRawQuery();
        return query == null || query.isEmpty()
                ? relativePath : relativePath + '?' + query;
    }

    private static boolean sameOrigin(URI first, URI second) {
        String firstScheme = first.getScheme() == null ? "" : first.getScheme();
        String secondScheme = second.getScheme() == null ? "" : second.getScheme();
        String firstHost = first.getHost() == null ? "" : first.getHost();
        String secondHost = second.getHost() == null ? "" : second.getHost();
        return firstScheme.equalsIgnoreCase(secondScheme)
                && firstHost.equalsIgnoreCase(secondHost)
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String ensureTrailingSlash(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.endsWith("/") ? safe : safe + '/';
    }

    static final class PaginationState {
        private final List<FeatureItem> items = new ArrayList<>();
        private final Set<String> itemKeys = new HashSet<>();
        private final Set<String> visitedEndpoints = new LinkedHashSet<>();
        private boolean finished;

        synchronized boolean visit(String endpoint) {
            if (finished) return false;
            String key = endpoint == null ? "" : endpoint.trim();
            return !key.isEmpty() && visitedEndpoints.add(key);
        }

        synchronized int getPageCount() {
            return visitedEndpoints.size();
        }

        synchronized void add(FeatureItem item, String stableKey) {
            if (finished) return;
            if (stableKey == null || itemKeys.add(stableKey)) items.add(item);
        }

        synchronized boolean finish() {
            if (finished) return false;
            finished = true;
            return true;
        }

        synchronized List<FeatureItem> snapshot() {
            return new ArrayList<>(items);
        }
    }

    public void action(int method, String endpoint, JSONObject body,
                       ApiCallback<JSONObject> callback) {
        apiClient.request(method, endpoint, body, true, callback);
    }

    public void multipartAction(int method, String endpoint, JSONObject fields,
                                List<MultipartFilePart> files,
                                ApiCallback<JSONObject> callback) {
        apiClient.multipart(method, endpoint, fields, files, true, callback);
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
        // Hỏi đáp bài học: ghép "Khóa học • Bài học" để giáo viên biết câu hỏi thuộc video nào
        String lessonTitle = SafeJson.string(item, "", "lesson_title");
        if (!lessonTitle.isEmpty()) {
            subtitle = subtitle.isEmpty() ? lessonTitle : subtitle + " • " + lessonTitle;
        }
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
