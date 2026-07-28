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
            "results", "items", "courses", "users", "students",
            "logs", "notifications", "backups", "sessions", "data", "questions", "feedback",
            "recent"
    };

    /** Nhãn tiếng Việt cho các key kỹ thuật trong báo cáo/cấu hình admin. */
    private static final String[][] KEY_LABELS = {
            // Báo cáo người dùng
            {"dau", "Hoạt động trong ngày (DAU)"},
            {"mau", "Hoạt động trong tháng (MAU)"},
            {"newUsers", "Người dùng mới"},
            {"activeUsers", "Người dùng đang hoạt động"},
            // Báo cáo học tập
            {"avgCompletion", "Tỷ lệ hoàn thành trung bình (%)"},
            {"avgScore", "Điểm trung bình"},
            {"avgTimeSpentMin", "Thời gian học trung bình (phút)"},
            // Báo cáo nội dung
            {"totalPublished", "Bài học đã xuất bản"},
            {"totalEnrollments", "Tổng lượt ghi danh"},
            {"avgRating", "Điểm đánh giá trung bình"},
            // Cấu hình / bảo mật
            {"brand", "Thương hiệu"},
            {"domainEmail", "Tên miền và Email"},
            {"integrations", "Tích hợp"},
            {"twoFA", "Xác thực 2 lớp"},
            {"rateLimit", "Giới hạn đăng nhập sai"},
            {"lockout", "Khóa tài khoản tạm thời"},
            {"rbacNote", "Ghi chú phân quyền"},
            {"cpu", "CPU"},
            {"ram", "RAM"},
            {"disk", "Ổ đĩa"},
            {"backup", "Sao lưu"},
            // Loại thông báo -> nhãn tiếng Việt
            {"lesson_question", "Hỏi đáp bài học"},
            {"lesson_question_reply", "Trả lời hỏi đáp"},
            {"course", "Khóa học"},
            {"exam", "Bài kiểm tra"},
            {"system", "Hệ thống"},
            {"admin_broadcast", "Thông báo từ nhà trường"},
            {"info", "Thông tin"},
            {"authSession", "Đăng nhập và phiên"},
            {"logging", "Ghi log"},
            {"maintenance", "Bảo trì hệ thống"},
            {"version", "Phiên bản"},
            {"updatedAt", "Cập nhật lúc"},
            {"updatedBy", "Cập nhật bởi"},
            // Key con hay xuất hiện trong phần cấu hình / bảo mật
            {"enforceAdmin", "Bắt buộc với quản trị"},
            {"enforceTeacher", "Bắt buộc với giáo viên"},
            {"loginFailures", "Số lần sai tối đa"},
            {"windowMin", "Trong vòng (phút)"},
            {"attempts", "Số lần thử"},
            {"lockMinutes", "Khóa (phút)"},
            {"banStrikes", "Số lần bị khóa để chặn"},
            {"domain", "Tên miền"},
            {"forceHttps", "Bắt buộc HTTPS"},
            {"hsts", "HSTS"},
            {"idleTimeoutMin", "Tự khóa khi không hoạt động (phút)"},
            {"maxSessionHours", "Thời lượng phiên tối đa (giờ)"},
            {"rememberMeDays", "Ghi nhớ đăng nhập (ngày)"},
            {"ssoGoogleEnabled", "Đăng nhập Google"},
            {"singleDeviceOnly", "Chỉ cho phép một thiết bị"},
            {"minLength", "Độ dài mật khẩu tối thiểu"},
            {"requireNumbers", "Bắt buộc có số"},
            {"requireSymbols", "Bắt buộc có ký tự đặc biệt"},
            {"schedule", "Lịch chạy"},
            {"retentionDays", "Thời gian lưu (ngày)"},
            {"rpoMinutes", "Mất dữ liệu tối đa (phút)"},
            {"rtoMinutes", "Thời gian khôi phục (phút)"},
            {"encrypted", "Mã hóa"},
            {"enabled", "Đang bật"},
            {"level", "Mức ghi log"},
            {"traceIdEnabled", "Theo dõi mã yêu cầu"},
            {"siteName", "Tên hệ thống"},
            {"language", "Ngôn ngữ"},
            {"timezone", "Múi giờ"},
            {"currency", "Tiền tệ"},
            {"logoUrl", "Logo"},
            {"lastBackup", "Sao lưu gần nhất"},
            {"lastRun", "Chạy gần nhất"},
            {"status", "Trạng thái"},
            {"current", "Hiện tại"},
            {"p95", "P95"},
    };

    /** Thứ tự hiển thị ưu tiên cho các key báo cáo (key không có trong danh sách xếp sau). */
    private static final String[] KEY_ORDER = {
            "activeUsers", "newUsers", "dau", "mau",
            "totalPublished", "totalEnrollments", "avgRating",
            "avgCompletion", "avgScore", "avgTimeSpentMin",
    };

    private final Context appContext;
    private final ApiClient apiClient;

    public ManagementRepository(Context context) {
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
    }

    /** Tải danh sách từ một endpoint bất kỳ, tự xử lý phân trang. */
    public void load(String endpoint, ApiCallback<List<FeatureItem>> callback) {
        PaginationState state = new PaginationState();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            deliverPaginationError(state, callback, "Chức năng này đang lỗi, vui lòng thử lại sau");
            return;
        }
        loadPage(endpoint, endpoint, state, callback);
    }

    /** Tải một trang; nếu còn trang sau thì gọi tiếp cho tới hết. */
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

    /** Thêm mục mới, bỏ mục trùng giữa các trang. */
    private void appendUnique(PaginationState state, List<FeatureItem> items) {
        if (items == null) return;
        for (FeatureItem item : items) {
            if (item == null) continue;
            state.add(item, stableItemKey(item));
        }
    }

    /** Khóa nhận dạng một mục để phát hiện trùng lặp. */
    private String stableItemKey(FeatureItem item) {
        JSONObject source = item.getSource();
        for (String key : ITEM_ID_KEYS) {
            if (!source.has(key) || source.isNull(key)) continue;
            String value = String.valueOf(source.opt(key)).trim();
            if (!value.isEmpty()) return key + ':' + value;
        }
        return null;
    }

    /** Trả kết quả cuối cùng về màn hình sau khi đã gom hết trang. */
    private void deliverPaginationSuccess(PaginationState state,
                                          ApiCallback<List<FeatureItem>> callback) {
        if (state.finish()) callback.onSuccess(state.snapshot());
    }

    /** Trả lỗi và bảo đảm chỉ gọi callback đúng một lần. */
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

    /** Chỉ đi theo link trang sau nếu cùng server, tránh bị dẫn sang địa chỉ lạ. */
    private static boolean sameOrigin(URI first, URI second) {
        String firstScheme = first.getScheme() == null ? "" : first.getScheme();
        String secondScheme = second.getScheme() == null ? "" : second.getScheme();
        String firstHost = first.getHost() == null ? "" : first.getHost();
        String secondHost = second.getHost() == null ? "" : second.getHost();
        return firstScheme.equalsIgnoreCase(secondScheme)
                && firstHost.equalsIgnoreCase(secondHost)
                && effectivePort(first) == effectivePort(second);
    }

    /** Suy ra cổng mặc định (80/443) khi URL không ghi rõ. */
    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** Bảo đảm đường dẫn kết thúc bằng dấu / như Django yêu cầu. */
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

    /** Thực hiện một thao tác quản trị (xuất bản, khóa tài khoản, xóa...). */
    public void action(int method, String endpoint, JSONObject body,
                       ApiCallback<JSONObject> callback) {
        apiClient.request(method, endpoint, body, true, callback);
    }

    /** Thao tác quản trị có kèm file tải lên. */
    public void multipartAction(int method, String endpoint, JSONObject fields,
                                List<MultipartFilePart> files,
                                ApiCallback<JSONObject> callback) {
        apiClient.multipart(method, endpoint, fields, files, true, callback);
    }

    /** Đọc một object JSON đơn lẻ (chi tiết bài học, thống kê...). */
    public void loadObject(String endpoint, ApiCallback<JSONObject> callback) {
        apiClient.get(endpoint, true, callback);
    }

    /** Điểm phân luồng: nhận diện dạng JSON rồi chọn cách đổi sang danh sách. */
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
        for (String key : orderedKeys(object)) {
            Object value = object.opt(key);
            if (value instanceof JSONObject) {
                JSONObject child = (JSONObject) value;
                result.add(new FeatureItem(key, readable(key), summarizeObject(child), "", "", child));
            } else if (value instanceof JSONArray) {
                appendArray(result, (JSONArray) value);
            } else {
                result.add(new FeatureItem(key, readable(key), display(value), "", "", object));
            }
        }
        return result;
    }

    /** Key đã biết xếp trước theo KEY_ORDER, key lạ giữ nguyên thứ tự server trả về. */
    private List<String> orderedKeys(JSONObject object) {
        List<String> original = new ArrayList<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) original.add(iterator.next());
        List<String> ordered = new ArrayList<>();
        for (String known : KEY_ORDER) {
            if (original.contains(known)) ordered.add(known);
        }
        for (String key : original) {
            if (!ordered.contains(key)) ordered.add(key);
        }
        return ordered;
    }

    /** Tóm tắt object con thành 1 dòng dễ đọc (vd cấu hình: siteName, ngôn ngữ...). */
    private String summarizeObject(JSONObject child) {
        StringBuilder summary = new StringBuilder();
        Iterator<String> keys = child.keys();
        while (keys.hasNext() && summary.length() < 90) {
            String key = keys.next();
            Object value = child.opt(key);
            if (value instanceof JSONObject || value instanceof JSONArray) continue;
            if (value == null || value == JSONObject.NULL) continue;
            String text = display(value).trim();
            if (text.isEmpty()) continue;
            if (summary.length() > 0) summary.append(" • ");
            summary.append(readable(key)).append(": ").append(text);
        }
        return summary.length() == 0 ? "Nhấn để xem chi tiết" : summary.toString();
    }

    /** Nhận biết API sức khỏe hệ thống để hiển thị riêng. */
    private boolean isSystemHealthEndpoint(String endpoint) {
        return endpoint != null && endpoint.startsWith("admin/system/health/");
    }

    /** Đổi số liệu CPU/RAM/ổ đĩa/sao lưu thành các thẻ hiển thị. */
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

    /** Thêm một chỉ số tài nguyên kèm trạng thái tốt/cảnh báo. */
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

    /** Thêm thẻ tình trạng sao lưu gần nhất. */
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

    /** Ngưỡng đánh giá CPU/RAM: bình thường, cảnh báo hay quá tải. */
    private String resourceStatus(double value) {
        if (value >= 85) return "Mức sử dụng cao";
        if (value >= 70) return "Cần theo dõi";
        return "Ổn định";
    }

    /** Ngưỡng đánh giá dung lượng ổ đĩa. */
    private String diskStatus(double value) {
        if (value >= 90) return "Gần hết dung lượng";
        if (value >= 75) return "Sắp đầy";
        return "Còn đủ dung lượng";
    }

    private String percent(double value) {
        return String.format(Locale.getDefault(), "%.1f%%", value);
    }

    private String feedbackRating(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value)
                : String.format(Locale.getDefault(), "%.1f", value);
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
        // Thông báo Admin: ẩn mã category kỹ thuật và làm rõ trạng thái đọc.
        if (item.has("is_read") && item.has("category") && item.has("message")) {
            boolean isRead = SafeJson.bool(item, false, "is_read", "isRead");
            return new FeatureItem(id,
                    SafeJson.string(item, "Thông báo", "title"),
                    notificationCategoryLabel(SafeJson.string(item, "system", "category")),
                    SafeJson.string(item, "", "message"),
                    isRead ? "Đã đọc" : "Chưa đọc", item);
        }
        // Nhật ký hoạt động: hành động + người thực hiện + thời gian
        if (item.has("action") && item.has("userEmail")) {
            return new FeatureItem(id,
                    actionLabel(SafeJson.string(item, "", "action")),
                    SafeJson.string(item, "", "userEmail"),
                    shortTime(SafeJson.string(item, "", "timestamp")),
                    statusLabel(SafeJson.string(item, "", "status")), item);
        }
        // Bài kiểm tra: "5 câu • Trắc nghiệm" thay vì giá trị kỹ thuật "mcq"
        String exerciseType = SafeJson.string(item, "", "type");
        if (item.has("published") && isExerciseType(exerciseType)) {
            JSONArray questions = item.optJSONArray("questions");
            int questionCount = questions == null ? 0 : questions.length();
            return new FeatureItem(id,
                    SafeJson.string(item, "Bài kiểm tra", "title"),
                    questionCount + " câu • " + exerciseTypeSummary(questions, exerciseType),
                    SafeJson.string(item, "", "description"),
                    SafeJson.bool(item, false, "published") ? "Đã xuất bản" : "Bản nháp",
                    item);
        }
        // Khóa học: ưu tiên tên môn do server trả về, không hiển thị UUID môn học.
        if (item.has("lessonsCount") && item.has("grade") && item.has("published")) {
            String subject = SafeJson.string(item, "", "subject_title", "subjectTitle");
            String grade = SafeJson.string(item, "", "grade");
            int lessons = SafeJson.integer(item, 0, "lessonsCount", "lessons_count");
            String subtitle = (subject.isEmpty() ? "Khóa học" : subject)
                    + (grade.isEmpty() ? "" : " • Lớp " + grade)
                    + " • " + lessons + " bài học";
            return new FeatureItem(id,
                    SafeJson.string(item, "Khóa học", "title"),
                    subtitle,
                    SafeJson.string(item, "", "description", "introduction"),
                    SafeJson.bool(item, false, "published") ? "Đã xuất bản" : "Bản nháp",
                    item);
        }
        // Học viên của giáo viên: tóm tắt tiến độ học thay vì chỉ email + lần hoạt động
        JSONArray courses = item.optJSONArray("courses");
        if (courses != null && item.has("lastActive")) {
            int courseCount = courses.length();
            int completed = 0;
            int totalLessons = 0;
            int progressSum = 0;
            int finishedCourses = 0;
            for (int index = 0; index < courseCount; index++) {
                JSONObject course = courses.optJSONObject(index);
                if (course == null) continue;
                completed += SafeJson.integer(course, 0, "completedLessons");
                totalLessons += SafeJson.integer(course, 0, "totalLessons");
                int progress = SafeJson.integer(course, 0, "progress");
                progressSum += progress;
                if (progress >= 100) finishedCourses++;
            }
            int average = courseCount == 0 ? 0 : progressSum / courseCount;
            String subtitle = courseCount + " khóa học • " + completed + "/" + totalLessons
                    + " bài học đã xong";
            String detail = "Tiến độ trung bình: " + average + "%"
                    + (finishedCourses > 0 ? " • Hoàn thành " + finishedCourses + " khóa" : "");
            double score = SafeJson.decimal(item, -1, "avgScore");
            if (score >= 0) detail += " • Điểm TB: " + score;
            return new FeatureItem(id, SafeJson.string(item, "Học viên", "name", "username"),
                    subtitle, detail,
                    "Hoạt động: " + SafeJson.string(item, "", "lastActive"), item);
        }
        // Lịch sử phản hồi giáo viên đã gửi: hiện đúng học sinh, khóa học và điểm.
        if (item.has("studentName") && item.has("rating") && item.has("message")) {
            String courseTitle = SafeJson.string(item, "", "courseTitle", "course_title");
            double rating = SafeJson.decimal(item, 0, "rating");
            String status = "Đánh giá " + feedbackRating(rating) + "/10";
            return new FeatureItem(id,
                    SafeJson.string(item, "Học viên", "studentName"),
                    courseTitle.isEmpty() ? "Phản hồi chung" : courseTitle,
                    SafeJson.string(item, "", "message"), status, item);
        }
        // Người dùng đang hoạt động: tên + vai trò/email + lần hoạt động gần nhất
        if (item.has("lastActive") && item.has("name")) {
            String roleLabel = SafeJson.string(item, "", "roleLabel", "role");
            String email = SafeJson.string(item, "", "email");
            return new FeatureItem(id,
                    SafeJson.string(item, "Người dùng", "name"),
                    roleLabel + (email.isEmpty() || roleLabel.isEmpty() ? email : " • " + email),
                    "Hoạt động: " + shortTime(SafeJson.string(item, "", "lastActive")),
                    "", item);
        }
        // Phiên đăng nhập: thiết bị + IP/vị trí + hoạt động gần nhất
        if (item.has("jti") && item.has("device")) {
            String email = SafeJson.string(item, "", "userEmail");
            String ip = SafeJson.string(item, "", "ip");
            return new FeatureItem(id,
                    SafeJson.string(item, "Thiết bị", "device"),
                    email,
                    (ip.isEmpty() ? "" : "IP: " + ip + " • ")
                            + "Hoạt động: " + shortTime(SafeJson.string(item, "", "lastActiveAt")),
                    "", item);
        }
        if (item.has("fileName") && item.has("sizeBytes")) {
            double sizeMb = SafeJson.decimal(item, 0, "sizeMB");
            return new FeatureItem(id,
                    SafeJson.string(item, "Bản sao lưu", "title"),
                    String.format(Locale.getDefault(), "%.2f MB", sizeMb),
                    SafeJson.string(item, "", "notes"),
                    statusLabel(SafeJson.string(item, "", "status")), item);
        }
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
        // API khóa học của admin trả status dạng chữ ("published"/"draft") -> dịch sang tiếng Việt
        status = statusLabel(status);
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
        if (value instanceof Boolean) return (Boolean) value ? "Bật" : "Tắt";
        String raw = String.valueOf(value);
        return raw.length() > 400 ? raw.substring(0, 400) + "…" : raw;
    }

    /** Dịch trạng thái sang tiếng Việt: published -> Đã xuất bản... */
    private String statusLabel(String status) {
        switch (status) {
            case "published": return "Đã xuất bản";
            case "draft": return "Bản nháp";
            case "archived": return "Đã lưu trữ";
            case "in_progress": return "Đang học";
            case "completed": return "Hoàn thành";
            case "success": return "Thành công";
            case "failed": return "Thất bại";
            default: return status;
        }
    }

    private String notificationCategoryLabel(String category) {
        switch (category) {
            case "admin_broadcast": return "Thông báo từ nhà trường";
            case "system_health": return "Sức khỏe hệ thống";
            case "learning_report": return "Báo cáo học tập";
            case "security": return "Bảo mật";
            case "course": return "Khóa học";
            case "exam": return "Bài kiểm tra";
            case "system": return "Thông báo hệ thống";
            default: return "Thông báo";
        }
    }

    /** Nhận biết JSON đang là bài kiểm tra dựa vào trường type. */
    private boolean isExerciseType(String type) {
        return "mcq".equals(type) || "short_answer".equals(type) || "matching".equals(type);
    }

    /** Dịch dạng câu hỏi sang tiếng Việt: mcq -> Trắc nghiệm... */
    private String exerciseTypeLabel(String type) {
        switch (type) {
            case "mcq": return "Trắc nghiệm";
            case "short_answer": return "Trả lời ngắn";
            case "matching": return "Nối cặp";
            default: return type;
        }
    }

    private String exerciseTypeSummary(JSONArray questions, String fallbackType) {
        Set<String> types = new LinkedHashSet<>();
        if (questions != null) {
            for (int index = 0; index < questions.length(); index++) {
                JSONObject question = questions.optJSONObject(index);
                JSONObject meta = question == null ? null : question.optJSONObject("meta");
                types.add(exerciseTypeLabel(SafeJson.string(meta, fallbackType, "type")));
            }
        }
        types.remove("");
        if (types.size() > 1) return types.size() + " dạng câu hỏi";
        return types.isEmpty() ? exerciseTypeLabel(fallbackType) : types.iterator().next();
    }

    /** Dịch key kỹ thuật của API sang nhãn tiếng Việt để hiển thị. */
    private String readable(String key) {
        if (key == null) return "Thông tin";
        for (String[] pair : KEY_LABELS) {
            if (pair[0].equals(key)) return pair[1];
        }
        return key.replace('_', ' ').trim();
    }

    /** "user.login" -> "Đăng nhập", các action lạ giữ nguyên. */
    private String actionLabel(String action) {
        switch (action) {
            case "user.login": return "Đăng nhập";
            case "user.signup": return "Đăng ký tài khoản";
            case "user.logout": return "Đăng xuất";
            case "user.login_failed": return "Đăng nhập thất bại";
            case "user.create": return "Tạo tài khoản";
            case "user.update": return "Cập nhật tài khoản";
            case "user.delete": return "Xóa tài khoản";
            case "system.config.update": return "Cập nhật cấu hình hệ thống";
            case "system.backup.create": return "Tạo bản sao lưu";
            case "security.policy.update": return "Cập nhật chính sách bảo mật";
            case "security.session.revoke": return "Thu hồi phiên đăng nhập";
            default: return action.isEmpty() ? "Hoạt động" : action;
        }
    }

    /** "2026-07-26T10:42:59...+00:00" (UTC) -> "26/07/2026 17:42" theo giờ máy. */
    private String shortTime(String isoValue) {
        if (isoValue == null || isoValue.trim().isEmpty()) return "";
        String raw = isoValue.trim();
        try {
            java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date parsed = parser.parse(raw.substring(0, Math.min(19, raw.length())));
            java.text.SimpleDateFormat printer = new java.text.SimpleDateFormat(
                    "dd/MM/yyyy HH:mm", Locale.US);
            printer.setTimeZone(java.util.TimeZone.getDefault());
            return parsed == null ? raw : printer.format(parsed);
        } catch (Exception ignored) {
            return raw.replace('T', ' ');
        }
    }
}
