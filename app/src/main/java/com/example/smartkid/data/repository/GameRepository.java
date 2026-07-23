package com.example.smartkid.data.repository;

import android.content.Context;

import com.example.smartkid.core.AppLogger;
import com.example.smartkid.core.SafeJson;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Nghiệp vụ trò chơi học viên, mọi điểm số đều gửi về server. */
public class GameRepository {
    private final Context appContext;
    private final ApiClient apiClient;

    public GameRepository(Context context) {
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
    }

    public void loadGames(ApiCallback<List<FeatureItem>> callback) {
        apiClient.get("student/games/", true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    JSONArray games = SafeJson.array(data, "games");
                    List<FeatureItem> result = new ArrayList<>();
                    for (int index = 0; index < games.length(); index++) {
                        JSONObject game = games.optJSONObject(index);
                        if (game == null) continue;
                        int count = SafeJson.integer(game, 0, "question_count");
                        int plays = SafeJson.integer(game, 0, "user_play_count");
                        String best = game.isNull("user_best_score") ? ""
                                : " • Tốt nhất: " + SafeJson.integer(game, 0, "user_best_score");
                        result.add(new FeatureItem(
                                SafeJson.string(game, "", "id"),
                                SafeJson.string(game, "Trò chơi", "title"),
                                SafeJson.string(game, "", "game_type_display", "game_type"),
                                SafeJson.string(game, "", "description"),
                                count + " câu • Đã chơi " + plays + " lần" + best,
                                game));
                    }
                    callback.onSuccess(result);
                } catch (Exception exception) {
                    AppLogger.error(appContext, "GameRepository", "Không thể đọc game", exception);
                    callback.onError(new ApiError(0, "Dữ liệu trò chơi không hợp lệ", false));
                }
            }

            @Override public void onError(ApiError error) { callback.onError(error); }
        });
    }

    public void loadDetail(String gameId, ApiCallback<JSONObject> callback) {
        if (!valid(gameId, callback)) return;
        apiClient.get("student/games/" + gameId.trim() + "/", true, callback);
    }

    public void start(String gameId, ApiCallback<JSONObject> callback) {
        if (!valid(gameId, callback)) return;
        apiClient.post("student/games/" + gameId.trim() + "/start/",
                new JSONObject(), true, callback);
    }

    public void submit(String gameId, String sessionId, int score, int seconds,
                       JSONArray answers, ApiCallback<JSONObject> callback) {
        if (!valid(gameId, callback)) return;
        try {
            JSONObject body = new JSONObject();
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                body.put("session_id", sessionId.trim());
            }
            body.put("score", Math.max(0, score));
            body.put("time_spent", Math.max(0, seconds));
            body.put("answers", answers == null ? new JSONArray() : answers);
            apiClient.post("student/games/" + gameId.trim() + "/submit/",
                    body, true, callback);
        } catch (Exception exception) {
            AppLogger.error(appContext, "GameRepository", "Không thể tạo kết quả game", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị kết quả trò chơi", false));
        }
    }

    public void leaderboard(String gameId, ApiCallback<JSONObject> callback) {
        if (!valid(gameId, callback)) return;
        apiClient.get("student/games/" + gameId.trim() + "/leaderboard/", true, callback);
    }

    private boolean valid(String id, ApiCallback<?> callback) {
        if (id == null || id.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã trò chơi không hợp lệ", false));
            return false;
        }
        return true;
    }
}
