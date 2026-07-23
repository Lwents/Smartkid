package com.example.smartkid.data.repository;

import android.content.Context;

import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.model.Course;
import com.example.smartkid.data.model.DashboardSummary;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;

import org.json.JSONArray;
import org.json.JSONObject;

public class DashboardRepository {
    private final Context appContext;
    private final ApiClient apiClient;

    public DashboardRepository(Context context) {
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
    }

    public void loadDashboard(ApiCallback<DashboardSummary> callback) {
        apiClient.get(AppConstants.DASHBOARD_ENDPOINT, true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject resumeObject = response.optJSONObject("resumeCourse");
                    Course resumeCourse = resumeObject == null ? null : parseCourse(resumeObject);
                    JSONArray featured = SafeJson.array(response, "featured");
                    JSONArray exams = SafeJson.array(response, "previewExams");
                    callback.onSuccess(new DashboardSummary(
                            resumeCourse, featured.length(), exams.length()));
                } catch (Exception exception) {
                    AppLogger.error(appContext, "DashboardRepository",
                            "Không thể đọc dashboard", exception);
                    callback.onError(new ApiError(0, "Dữ liệu trang chủ không hợp lệ", false));
                }
            }

            @Override
            public void onError(ApiError error) {
                callback.onError(error);
            }
        });
    }

    private Course parseCourse(JSONObject object) {
        return new Course(
                SafeJson.string(object, "", "id"),
                SafeJson.string(object, "Khóa học", "title"),
                SafeJson.string(object, "", "grade", "gradeLabel"),
                SafeJson.string(object, "", "subject", "subjectSlug"),
                SafeJson.string(object, "", "teacherName", "teacher_name"),
                SafeJson.integer(object, 0, "lessonsCount", "lessons_count"),
                SafeJson.integer(object, 0, "progress"),
                SafeJson.decimal(object, 0, "price"),
                SafeJson.string(object, "", "thumbnail", "thumbnail_url"),
                SafeJson.string(object, "", "description", "introduction"),
                true
        );
    }
}
