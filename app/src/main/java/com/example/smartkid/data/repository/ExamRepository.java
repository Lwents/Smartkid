package com.example.smartkid.data.repository;

import android.content.Context;

import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Nghiệp vụ bài kiểm tra học viên dựa trên /api/student/exams/. */
public class ExamRepository {
    private final Context appContext;
    private final ApiClient apiClient;

    public ExamRepository(Context context) {
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
    }

    /** Danh sách đề học sinh được phép làm (theo khóa đã ghi danh và khối lớp). */
    public void loadExams(ApiCallback<List<FeatureItem>> callback) {
        apiClient.getArray("student/exams/", true, listCallback(callback, false));
    }

    /** Danh sách chứng chỉ đã đạt. */
    public void loadCertificates(ApiCallback<List<FeatureItem>> callback) {
        apiClient.getArray("student/exams/certificates/", true, listCallback(callback, true));
    }

    /** Chi tiết đề: câu hỏi, thời gian làm bài, điểm đạt (không kèm đáp án đúng). */
    public void loadDetail(String examId, ApiCallback<JSONObject> callback) {
        if (!validId(examId, callback)) return;
        apiClient.get("student/exams/" + examId.trim() + "/", true, callback);
    }

    /** Mở một lượt làm bài, trả về attemptId. */
    public void start(String examId, ApiCallback<JSONObject> callback) {
        if (!validId(examId, callback)) return;
        apiClient.post("student/exams/" + examId.trim() + "/start/",
                new JSONObject(), true, callback);
    }

    /** Nộp toàn bộ đáp án của lượt làm bài và nhận kết quả chấm. */
    public void submit(String examId, String attemptId, JSONObject answers,
                       ApiCallback<JSONObject> callback) {
        if (!validId(examId, callback) || attemptId == null || attemptId.trim().isEmpty()) {
            if (attemptId == null || attemptId.trim().isEmpty()) {
                callback.onError(new ApiError(0, "Lượt làm bài không hợp lệ", false));
            }
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("answers", answers == null ? new JSONObject() : answers);
            apiClient.post("student/exams/" + examId.trim() + "/submit/"
                    + attemptId.trim() + "/", body, true, callback);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ExamRepository", "Không thể tạo bài nộp", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị bài nộp", false));
        }
    }

    /** Xem lại kết quả của một lượt đã nộp. */
    public void loadResult(String examId, String attemptId, ApiCallback<JSONObject> callback) {
        if (!validId(examId, callback) || attemptId == null || attemptId.trim().isEmpty()) {
            if (attemptId == null || attemptId.trim().isEmpty()) {
                callback.onError(new ApiError(0, "Lượt làm bài không hợp lệ", false));
            }
            return;
        }
        apiClient.get("student/exams/" + examId.trim() + "/result/"
                + attemptId.trim() + "/", true, callback);
    }

    /** Bảng xếp hạng điểm của đề. */
    public void loadRanking(String examId, ApiCallback<JSONObject> callback) {
        if (!validId(examId, callback)) return;
        apiClient.get("student/exams/" + examId.trim() + "/ranking/", true, callback);
    }

    /** Đổi mảng JSON thành danh sách hiển thị dùng chung cho đề và chứng chỉ. */
    private ApiCallback<JSONArray> listCallback(ApiCallback<List<FeatureItem>> callback,
                                                 boolean certificate) {
        return new ApiCallback<JSONArray>() {
            @Override
            public void onSuccess(JSONArray data) {
                try {
                    List<FeatureItem> result = new ArrayList<>();
                    for (int index = 0; index < data.length(); index++) {
                        JSONObject item = data.optJSONObject(index);
                        if (item == null) continue;
                        if (certificate) {
                            result.add(new FeatureItem(
                                    SafeJson.string(item, "", "id"),
                                    SafeJson.string(item, "Chứng chỉ", "title"),
                                    "Điểm: " + SafeJson.decimal(item, 0, "score")
                                            + "/" + SafeJson.decimal(item, 100, "total"),
                                    SafeJson.string(item, "", "issuedAt", "issued_at"),
                                    "Đã cấp", item));
                        } else {
                            int duration = SafeJson.integer(item, 0, "durationSec", "duration_seconds");
                            int questions = SafeJson.integer(item, 0, "questionsCount", "questions_count");
                            result.add(new FeatureItem(
                                    SafeJson.string(item, "", "id"),
                                    SafeJson.string(item, "Bài kiểm tra", "title"),
                                    SafeJson.string(item, "", "level", "grade"),
                                    questions + " câu • " + Math.max(1, duration / 60) + " phút",
                                    "Điểm đạt: " + SafeJson.decimal(item, 0, "passScore", "pass_score"),
                                    item));
                        }
                    }
                    callback.onSuccess(result);
                } catch (Exception exception) {
                    AppLogger.error(appContext, "ExamRepository", "Không thể đọc danh sách", exception);
                    callback.onError(new ApiError(0, "Dữ liệu bài kiểm tra không hợp lệ", false));
                }
            }

            @Override public void onError(ApiError error) { callback.onError(error); }
        };
    }

    /** Chặn sớm khi thiếu mã đề/lượt làm bài để không gọi API sai đường dẫn. */
    private boolean validId(String id, ApiCallback<?> callback) {
        if (id == null || id.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã bài kiểm tra không hợp lệ", false));
            return false;
        }
        return true;
    }
}
