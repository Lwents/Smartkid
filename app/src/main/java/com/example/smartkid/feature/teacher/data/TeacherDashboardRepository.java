package com.example.smartkid.feature.teacher.data;

import android.content.Context;

import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.feature.teacher.model.TeacherDashboardData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Loads the teacher KPIs and course summaries exposed by SunEdu's dashboard API. */
public final class TeacherDashboardRepository {
    private final Context appContext;
    private final ApiClient apiClient;

    public TeacherDashboardRepository(Context context) {
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
    }

    public void load(ApiCallback<TeacherDashboardData> callback) {
        apiClient.get("teacher/dashboard/", true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    callback.onSuccess(parse(response));
                } catch (Exception exception) {
                    AppLogger.error(appContext, "TeacherDashboardRepository",
                            "Không thể đọc dashboard giáo viên", exception);
                    callback.onError(new ApiError(0,
                            "Dữ liệu dashboard giáo viên không hợp lệ", false));
                }
            }

            @Override
            public void onError(ApiError error) {
                callback.onError(error);
            }
        });
    }

    private TeacherDashboardData parse(JSONObject response) {
        JSONObject root = response == null ? new JSONObject() : response;
        JSONObject stats = root.optJSONObject("stats");
        if (stats == null) stats = new JSONObject();
        return new TeacherDashboardData(
                SafeJson.integer(stats, 0, "courses"),
                SafeJson.integer(stats, 0, "students"),
                SafeJson.integer(stats, 0, "assignments", "lessons"),
                parseCourses(root));
    }

    private List<TeacherDashboardData.CourseItem> parseCourses(JSONObject root) {
        List<TeacherDashboardData.CourseItem> result = new ArrayList<>();
        JSONArray values = SafeJson.array(root, "myCourses", "courses");
        for (int index = 0; index < values.length(); index++) {
            JSONObject item = values.optJSONObject(index);
            if (item == null) continue;
            result.add(new TeacherDashboardData.CourseItem(
                    SafeJson.string(item, "", "id"),
                    SafeJson.string(item, "Khóa học", "title"),
                    SafeJson.integer(item, 0, "enrolled", "enrollments"),
                    SafeJson.integer(item, 0, "lessons"),
                    SafeJson.string(item, "draft", "status")));
        }
        return result;
    }
}
