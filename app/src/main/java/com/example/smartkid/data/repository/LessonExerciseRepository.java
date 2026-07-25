package com.example.smartkid.data.repository;

import android.content.Context;

import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;

import org.json.JSONObject;

/** API làm bài luyện tập gắn bài học theo luồng activities của SunEdu. */
public final class LessonExerciseRepository {
    private final ApiClient apiClient;

    public LessonExerciseRepository(Context context) {
        apiClient = ApiClient.getInstance(context.getApplicationContext());
    }

    public void loadDetail(String exerciseId, ApiCallback<JSONObject> callback) {
        if (!valid(exerciseId, callback)) return;
        apiClient.get("activities/exercises/" + exerciseId.trim() + "/", true, callback);
    }

    public void start(String exerciseId, ApiCallback<JSONObject> callback) {
        if (!valid(exerciseId, callback)) return;
        apiClient.post("activities/exercises/" + exerciseId.trim() + "/start/",
                new JSONObject(), true, callback);
    }

    public void submitAnswer(String attemptId, String questionId, JSONObject answer,
                             ApiCallback<JSONObject> callback) {
        if (!valid(attemptId, callback) || questionId == null || questionId.trim().isEmpty()) {
            if (questionId == null || questionId.trim().isEmpty()) {
                callback.onError(new ApiError(0, "Mã câu hỏi không hợp lệ", false));
            }
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("question_id", questionId.trim());
            body.put("answer", answer == null ? new JSONObject() : answer);
            apiClient.post("activities/attempts/" + attemptId.trim() + "/answers/",
                    body, true, callback);
        } catch (Exception exception) {
            callback.onError(new ApiError(0, "Không thể chuẩn bị câu trả lời", false));
        }
    }

    public void finalizeAttempt(String attemptId, ApiCallback<JSONObject> callback) {
        if (!valid(attemptId, callback)) return;
        apiClient.post("activities/attempts/" + attemptId.trim() + "/finalize/",
                new JSONObject(), true, callback);
    }

    private boolean valid(String id, ApiCallback<?> callback) {
        if (id == null || id.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã bài luyện tập không hợp lệ", false));
            return false;
        }
        return true;
    }
}
