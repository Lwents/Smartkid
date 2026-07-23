package com.example.smartkid.data.repository;

import android.content.Context;

import com.example.smartkid.core.AppLogger;
import com.example.smartkid.core.SafeJson;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Các chức năng bổ trợ trong cổng học viên. */
public class StudentFeatureRepository {
    private final Context appContext;
    private final ApiClient apiClient;

    public StudentFeatureRepository(Context context) {
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
    }

    public void loadLearningPath(ApiCallback<List<FeatureItem>> callback) {
        apiClient.get("student/learning-path/", true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    List<FeatureItem> result = new ArrayList<>();
                    appendLearningCourses(result, SafeJson.array(data, "grade_1_2"), "Khối 1–2");
                    appendLearningCourses(result, SafeJson.array(data, "grade_3_5"), "Khối 3–5");
                    callback.onSuccess(result);
                } catch (Exception exception) {
                    parseFailure("lộ trình", exception, callback);
                }
            }

            @Override public void onError(ApiError error) { callback.onError(error); }
        });
    }

    public void generateLearningPath(String courseId, ApiCallback<String> callback) {
        if (courseId == null || courseId.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã khóa học không hợp lệ", false));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("course_id", courseId.trim());
            apiClient.post("student/learning-path/manage/", body, true,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            callback.onSuccess("Đã tạo lộ trình cho "
                                    + SafeJson.string(data, "khóa học", "course_title"));
                        }

                        @Override public void onError(ApiError error) { callback.onError(error); }
                    });
        } catch (Exception exception) {
            AppLogger.error(appContext, "StudentFeatureRepository", "Không thể tạo lộ trình", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị lộ trình", false));
        }
    }

    public void loadPayments(ApiCallback<List<FeatureItem>> callback) {
        apiClient.get("payments/history/", true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    JSONArray items = SafeJson.array(data, "results", "items");
                    List<FeatureItem> result = new ArrayList<>();
                    NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                    for (int index = 0; index < items.length(); index++) {
                        JSONObject item = items.optJSONObject(index);
                        if (item == null) continue;
                        JSONObject plan = item.optJSONObject("plan");
                        JSONObject metadata = item.optJSONObject("metadata");
                        String planName = SafeJson.string(item, "", "plan_name");
                        if (planName.isEmpty()) planName = SafeJson.string(plan, "Thanh toán tuỳ chỉnh", "name");
                        String gateway = SafeJson.string(item, "", "gateway");
                        if (gateway.isEmpty()) gateway = SafeJson.string(metadata, "MoMo", "gateway");
                        result.add(new FeatureItem(
                                SafeJson.string(item, "", "id"), planName,
                                currency.format(SafeJson.decimal(item, 0, "amount")),
                                SafeJson.string(item, "", "paid_at", "created_at"),
                                statusVietnamese(SafeJson.string(item, "", "status")), item));
                    }
                    callback.onSuccess(result);
                } catch (Exception exception) {
                    parseFailure("thanh toán", exception, callback);
                }
            }

            @Override public void onError(ApiError error) { callback.onError(error); }
        });
    }

    public void loadNotifications(ApiCallback<List<FeatureItem>> callback) {
        apiClient.get("student/notifications/?limit=100", true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    JSONArray items = SafeJson.array(data, "notifications");
                    List<FeatureItem> result = new ArrayList<>();
                    for (int index = 0; index < items.length(); index++) {
                        JSONObject item = items.optJSONObject(index);
                        if (item == null) continue;
                        boolean read = SafeJson.bool(item, false, "is_read");
                        result.add(new FeatureItem(
                                SafeJson.string(item, "", "id"),
                                SafeJson.string(item, "Thông báo", "title"),
                                SafeJson.string(item, "", "category", "type"),
                                SafeJson.string(item, "", "message"),
                                read ? "Đã đọc" : "Chưa đọc", item));
                    }
                    callback.onSuccess(result);
                } catch (Exception exception) {
                    parseFailure("thông báo", exception, callback);
                }
            }

            @Override public void onError(ApiError error) { callback.onError(error); }
        });
    }

    public void markNotificationRead(String id, ApiCallback<Boolean> callback) {
        if (id == null || id.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã thông báo không hợp lệ", false));
            return;
        }
        apiClient.patch("student/notifications/" + id.trim() + "/read/",
                new JSONObject(), true, booleanCallback(callback));
    }

    public void markAllNotificationsRead(ApiCallback<Boolean> callback) {
        apiClient.patch("student/notifications/read-all/", new JSONObject(), true,
                booleanCallback(callback));
    }

    public void loadParent(ApiCallback<JSONObject> callback) {
        apiClient.get("student/account/parent/", true, callback);
    }

    public void updateParent(String name, String email, String phone, String relationship,
                             ApiCallback<JSONObject> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("name", safe(name));
            body.put("email", safe(email));
            body.put("phone", safe(phone));
            body.put("relationship", safe(relationship));
            apiClient.put("student/account/parent/", body, true, callback);
        } catch (Exception exception) {
            AppLogger.error(appContext, "StudentFeatureRepository", "Không thể lưu phụ huynh", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị thông tin phụ huynh", false));
        }
    }

    public void changePassword(String oldPassword, String newPassword,
                               ApiCallback<String> callback) {
        try {
            JSONObject body = new JSONObject();
            String role = new SessionManager(appContext).getUser().getRole();
            boolean student = "student".equalsIgnoreCase(role);
            body.put(student ? "oldPassword" : "old_password",
                    oldPassword == null ? "" : oldPassword);
            body.put(student ? "newPassword" : "new_password",
                    newPassword == null ? "" : newPassword);
            apiClient.post(student ? "student/account/change-password/" : "account/password/change/",
                    body, true,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            callback.onSuccess(SafeJson.string(data,
                                    "Đổi mật khẩu thành công", "detail", "message"));
                        }

                        @Override public void onError(ApiError error) { callback.onError(error); }
                    });
        } catch (Exception exception) {
            AppLogger.error(appContext, "StudentFeatureRepository", "Không thể đổi mật khẩu", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị yêu cầu đổi mật khẩu", false));
        }
    }

    public void initiateMomo(double amount, String planId, String description,
                             String courseId, String courseTitle,
                             ApiCallback<JSONObject> callback) {
        JSONArray courseIds = new JSONArray();
        JSONArray courseTitles = new JSONArray();
        if (courseId != null && !courseId.trim().isEmpty()) {
            courseIds.put(courseId.trim());
            courseTitles.put(safe(courseTitle));
        }
        initiateMomo(amount, planId, description, courseIds, courseTitles, callback);
    }

    public void initiateMomo(double amount, String planId, String description,
                             JSONArray courseIds, JSONArray courseTitles,
                             ApiCallback<JSONObject> callback) {
        try {
            JSONObject body = new JSONObject();
            if (planId != null && !planId.trim().isEmpty()) body.put("plan_id", planId.trim());
            else body.put("amount", amount);
            body.put("description", safe(description).isEmpty() ? "Nạp tiền SmartKid" : description.trim());
            body.put("flow", "pay_with_method");
            if (courseIds != null && courseIds.length() > 0) {
                body.put("course_ids", courseIds);
                body.put("course_titles", courseTitles == null ? new JSONArray() : courseTitles);
            }
            apiClient.post("payments/momo/initiate/", body, true, callback);
        } catch (Exception exception) {
            AppLogger.error(appContext, "StudentFeatureRepository", "Không thể khởi tạo MoMo", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị giao dịch", false));
        }
    }

    public void chatWithTutor(String message, String lessonId, String lessonTitle,
                              ApiCallback<String> callback) {
        String normalized = safe(message);
        if (normalized.isEmpty()) {
            callback.onError(new ApiError(0, "Câu hỏi không được để trống", false));
            return;
        }
        if (normalized.length() > 1000) {
            callback.onError(new ApiError(0, "Câu hỏi tối đa 1000 ký tự", false));
            return;
        }
        try {
            JSONObject context = new JSONObject();
            if (!safe(lessonId).isEmpty()) context.put("lesson_id", safe(lessonId));
            if (!safe(lessonTitle).isEmpty()) context.put("lesson_title", safe(lessonTitle));
            JSONObject body = new JSONObject();
            body.put("message", normalized);
            body.put("context", context);
            body.put("conversation_id", safe(lessonId).isEmpty() ? "default" : safe(lessonId));
            apiClient.post("student/ai/tutor/chat/", body, true,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            if (!SafeJson.bool(data, false, "success")) {
                                callback.onError(new ApiError(503,
                                        SafeJson.string(data, "AI Tutor chưa sẵn sàng", "detail", "error"),
                                        false));
                                return;
                            }
                            String answer = SafeJson.string(data, "", "message");
                            if (answer.isEmpty()) {
                                callback.onError(new ApiError(0,
                                        "Server AI không trả về nội dung", false));
                            } else {
                                callback.onSuccess(answer);
                            }
                        }

                        @Override public void onError(ApiError error) { callback.onError(error); }
                    });
        } catch (Exception exception) {
            AppLogger.error(appContext, "StudentFeatureRepository", "Không thể tạo câu hỏi AI", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị câu hỏi AI", false));
        }
    }

    public void clearTutorHistory(String lessonId, ApiCallback<Boolean> callback) {
        String endpoint = "student/ai/tutor/history/";
        String normalized = safe(lessonId);
        if (!normalized.isEmpty()) {
            try {
                endpoint += "?conversation_id="
                        + java.net.URLEncoder.encode(normalized, "UTF-8");
            } catch (Exception exception) {
                AppLogger.error(appContext, "StudentFeatureRepository",
                        "Không thể mã hóa hội thoại AI", exception);
            }
        }
        apiClient.delete(endpoint, null, true, booleanCallback(callback));
    }

    public void loadLessonQuestions(String lessonId, ApiCallback<List<FeatureItem>> callback) {
        String normalized = safe(lessonId);
        if (normalized.isEmpty()) {
            callback.onError(new ApiError(0, "Mã bài học không hợp lệ", false));
            return;
        }
        try {
            String endpoint = "student/lesson-questions/?lesson_id="
                    + java.net.URLEncoder.encode(normalized, "UTF-8");
            apiClient.get(endpoint, true, new ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject data) {
                    try {
                        JSONArray items = SafeJson.array(data, "items");
                        List<FeatureItem> result = new ArrayList<>();
                        for (int index = 0; index < items.length(); index++) {
                            JSONObject item = items.optJSONObject(index);
                            if (item == null) continue;
                            JSONArray replies = SafeJson.array(item, "replies");
                            int reactions = SafeJson.integer(item, 0, "reactions_count");
                            result.add(new FeatureItem(
                                    SafeJson.string(item, "", "id"),
                                    SafeJson.string(item, "Học viên", "student"),
                                    SafeJson.string(item, "", "created_at")
                                            + " • " + replies.length() + " phản hồi",
                                    SafeJson.string(item, "", "content"),
                                    reactions + " lượt thích", item));
                        }
                        callback.onSuccess(result);
                    } catch (Exception exception) {
                        parseFailure("hỏi đáp bài học", exception, callback);
                    }
                }

                @Override public void onError(ApiError error) { callback.onError(error); }
            });
        } catch (Exception exception) {
            AppLogger.error(appContext, "StudentFeatureRepository",
                    "Không thể tải hỏi đáp bài học", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị yêu cầu hỏi đáp", false));
        }
    }

    public void createLessonQuestion(String lessonId, String content,
                                     ApiCallback<Boolean> callback) {
        String normalizedLesson = safe(lessonId);
        String normalizedContent = safe(content);
        if (normalizedLesson.isEmpty() || normalizedContent.isEmpty()) {
            callback.onError(new ApiError(0, "Bài học và nội dung câu hỏi là bắt buộc", false));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("lesson_id", normalizedLesson);
            body.put("content", normalizedContent);
            apiClient.post("student/lesson-questions/", body, true, booleanCallback(callback));
        } catch (Exception exception) {
            requestFailure("gửi câu hỏi", exception, callback);
        }
    }

    public void replyLessonQuestion(String questionId, String content,
                                    ApiCallback<Boolean> callback) {
        String id = safe(questionId);
        String normalizedContent = safe(content);
        if (id.isEmpty() || normalizedContent.isEmpty()) {
            callback.onError(new ApiError(0, "Nội dung phản hồi là bắt buộc", false));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("content", normalizedContent);
            apiClient.post("student/lesson-questions/" + id + "/reply/",
                    body, true, booleanCallback(callback));
        } catch (Exception exception) {
            requestFailure("gửi phản hồi", exception, callback);
        }
    }

    public void reactLessonQuestion(String questionId, ApiCallback<Boolean> callback) {
        String id = safe(questionId);
        if (id.isEmpty()) {
            callback.onError(new ApiError(0, "Mã câu hỏi không hợp lệ", false));
            return;
        }
        apiClient.post("student/lesson-questions/" + id + "/react/",
                new JSONObject(), true, booleanCallback(callback));
    }

    public void editLessonQuestion(String questionId, String content,
                                   ApiCallback<Boolean> callback) {
        String id = safe(questionId);
        String normalizedContent = safe(content);
        if (id.isEmpty() || normalizedContent.isEmpty()) {
            callback.onError(new ApiError(0, "Nội dung chỉnh sửa là bắt buộc", false));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("content", normalizedContent);
            apiClient.patch("student/lesson-questions/" + id + "/",
                    body, true, booleanCallback(callback));
        } catch (Exception exception) {
            requestFailure("sửa câu hỏi", exception, callback);
        }
    }

    public void deleteLessonQuestion(String questionId, ApiCallback<Boolean> callback) {
        String id = safe(questionId);
        if (id.isEmpty()) {
            callback.onError(new ApiError(0, "Mã câu hỏi không hợp lệ", false));
            return;
        }
        apiClient.delete("student/lesson-questions/" + id + "/",
                null, true, booleanCallback(callback));
    }

    public void reportLessonQuestion(String questionId, String detail,
                                     ApiCallback<Boolean> callback) {
        String id = safe(questionId);
        if (id.isEmpty()) {
            callback.onError(new ApiError(0, "Mã câu hỏi không hợp lệ", false));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("question_id", id);
            body.put("reason", "Vi phạm nội dung");
            body.put("detail", safe(detail));
            apiClient.post("student/lesson-question-report/", body,
                    true, booleanCallback(callback));
        } catch (Exception exception) {
            requestFailure("báo cáo nội dung", exception, callback);
        }
    }

    public void loadLearningAnalysis(ApiCallback<JSONObject> callback) {
        apiClient.get("student/ai/learning-analyzer/", true, callback);
    }

    public void startAssessment(String courseId, ApiCallback<JSONObject> callback) {
        String id = safe(courseId);
        if (id.isEmpty()) {
            callback.onError(new ApiError(0, "Mã khóa học không hợp lệ", false));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("course_id", id);
            body.put("use_ai", true);
            apiClient.post("student/ai/assessment/", body, true, callback);
        } catch (Exception exception) {
            AppLogger.error(appContext, "StudentFeatureRepository",
                    "Không thể tạo đánh giá đầu vào", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị đánh giá đầu vào", false));
        }
    }

    public void submitAssessment(String courseId, JSONArray answers,
                                 ApiCallback<JSONObject> callback) {
        String id = safe(courseId);
        if (id.isEmpty()) {
            callback.onError(new ApiError(0, "Mã khóa học không hợp lệ", false));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("course_id", id);
            body.put("answers", answers == null ? new JSONArray() : answers);
            body.put("use_ai", true);
            apiClient.post("student/ai/assessment/result/", body, true, callback);
        } catch (Exception exception) {
            AppLogger.error(appContext, "StudentFeatureRepository",
                    "Không thể nộp đánh giá đầu vào", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị kết quả đánh giá", false));
        }
    }

    public void restoreLearningStreak(ApiCallback<String> callback) {
        apiClient.post("student/ai/learning/restore-streak/", new JSONObject(), true,
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        callback.onSuccess(SafeJson.string(data,
                                "Đã khôi phục chuỗi học tập", "message", "detail"));
                    }

                    @Override public void onError(ApiError error) { callback.onError(error); }
                });
    }

    private void appendLearningCourses(List<FeatureItem> target, JSONArray array, String group) {
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) continue;
            int progress = SafeJson.integer(item, 0, "progress");
            target.add(new FeatureItem(
                    SafeJson.string(item, "", "id"),
                    SafeJson.string(item, "Khóa học", "title"),
                    group + " • " + SafeJson.string(item, "", "subject"),
                    "Tiến độ học tập", progress + "%", item));
        }
    }

    private ApiCallback<JSONObject> booleanCallback(ApiCallback<Boolean> callback) {
        return new ApiCallback<JSONObject>() {
            @Override public void onSuccess(JSONObject data) { callback.onSuccess(true); }
            @Override public void onError(ApiError error) { callback.onError(error); }
        };
    }

    private <T> void parseFailure(String type, Exception exception, ApiCallback<T> callback) {
        AppLogger.error(appContext, "StudentFeatureRepository",
                "Không thể đọc dữ liệu " + type, exception);
        callback.onError(new ApiError(0, "Dữ liệu " + type + " không hợp lệ", false));
    }

    private void requestFailure(String action, Exception exception,
                                ApiCallback<Boolean> callback) {
        AppLogger.error(appContext, "StudentFeatureRepository",
                "Không thể " + action, exception);
        callback.onError(new ApiError(0, "Không thể chuẩn bị dữ liệu để " + action, false));
    }

    private static String statusVietnamese(String value) {
        if ("paid".equalsIgnoreCase(value) || "success".equalsIgnoreCase(value)) return "Thành công";
        if ("pending".equalsIgnoreCase(value)) return "Đang xử lý";
        if ("failed".equalsIgnoreCase(value)) return "Thất bại";
        if ("refunded".equalsIgnoreCase(value)) return "Đã hoàn tiền";
        return value;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
