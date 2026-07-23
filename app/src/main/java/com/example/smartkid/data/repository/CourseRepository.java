package com.example.smartkid.data.repository;

import android.content.Context;

import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.model.Course;
import com.example.smartkid.data.model.CourseDetail;
import com.example.smartkid.data.model.CourseListResult;
import com.example.smartkid.data.model.Lesson;
import com.example.smartkid.data.model.LessonContent;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CourseRepository {
    private static final Object CACHE_LOCK = new Object();
    private static final List<Course> memoryCache = new ArrayList<>();

    private final Context appContext;
    private final ApiClient apiClient;

    public CourseRepository(Context context) {
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
    }

    public void loadMyCourses(ApiCallback<CourseListResult> callback) {
        apiClient.get(AppConstants.MY_COURSES_ENDPOINT, true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray items = SafeJson.array(response, "all");
                    if (items.length() == 0) {
                        items = mergeArrays(SafeJson.array(response, "base"),
                                SafeJson.array(response, "supp"));
                    }
                    List<Course> courses = parseCourses(items);
                    saveMemoryCache(courses);
                    callback.onSuccess(new CourseListResult(courses, false, ""));
                } catch (Exception exception) {
                    AppLogger.error(appContext, "CourseRepository",
                            "Không thể đọc danh sách khóa học", exception);
                    loadCacheOrError(callback,
                            new ApiError(0, "Dữ liệu khóa học từ server không hợp lệ", false));
                }
            }

            @Override
            public void onError(ApiError error) {
                if (error.isSessionExpired()) {
                    callback.onError(error);
                } else {
                    loadCacheOrError(callback, error);
                }
            }
        });
    }

    public void loadCatalog(String keyword, ApiCallback<List<Course>> callback) {
        String endpoint = "student/catalog/?page=1&pageSize=100";
        if (keyword != null && !keyword.trim().isEmpty()) {
            try {
                endpoint += "&q=" + java.net.URLEncoder.encode(keyword.trim(), "UTF-8");
            } catch (Exception exception) {
                AppLogger.error(appContext, "CourseRepository", "Không thể mã hóa tìm kiếm", exception);
            }
        }
        apiClient.get(endpoint, true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    callback.onSuccess(parseCourses(SafeJson.array(response, "items", "results")));
                } catch (Exception exception) {
                    AppLogger.error(appContext, "CourseRepository", "Không thể đọc danh mục", exception);
                    callback.onError(new ApiError(0, "Dữ liệu danh mục không hợp lệ", false));
                }
            }

            @Override public void onError(ApiError error) { callback.onError(error); }
        });
    }

    public void enroll(String courseId, ApiCallback<Boolean> callback) {
        if (courseId == null || courseId.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã khóa học không hợp lệ", false));
            return;
        }
        apiClient.post("content/courses/" + courseId.trim() + "/enroll/",
                new JSONObject(), true, new ApiCallback<JSONObject>() {
                    @Override public void onSuccess(JSONObject data) { callback.onSuccess(true); }
                    @Override public void onError(ApiError error) { callback.onError(error); }
                });
    }

    public void unenroll(String courseId, ApiCallback<Boolean> callback) {
        if (courseId == null || courseId.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã khóa học không hợp lệ", false));
            return;
        }
        apiClient.delete("content/courses/" + courseId.trim() + "/enroll/",
                null, true, new ApiCallback<JSONObject>() {
                    @Override public void onSuccess(JSONObject data) { callback.onSuccess(true); }
                    @Override public void onError(ApiError error) { callback.onError(error); }
                });
    }

    public void loadCourseDetail(String courseId, ApiCallback<CourseDetail> callback) {
        if (courseId == null || courseId.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã khóa học không hợp lệ", false));
            return;
        }
        apiClient.get("student/courses/" + courseId.trim() + "/", true,
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            Course course = parseCourse(response);
                            List<Lesson> lessons = new ArrayList<>();
                            JSONArray sections = SafeJson.array(response, "sections");
                            for (int sectionIndex = 0; sectionIndex < sections.length(); sectionIndex++) {
                                JSONObject section = sections.optJSONObject(sectionIndex);
                                JSONArray lessonArray = SafeJson.array(section, "lessons");
                                for (int lessonIndex = 0; lessonIndex < lessonArray.length(); lessonIndex++) {
                                    JSONObject lesson = lessonArray.optJSONObject(lessonIndex);
                                    if (lesson == null) {
                                        continue;
                                    }
                                    lessons.add(new Lesson(
                                            SafeJson.string(lesson, "", "id"),
                                            SafeJson.string(lesson, "Bài học", "title"),
                                            SafeJson.string(lesson, "text", "type", "content_type"),
                                            SafeJson.bool(lesson, false, "completed")
                                    ));
                                }
                            }
                            callback.onSuccess(new CourseDetail(course, lessons));
                        } catch (Exception exception) {
                            AppLogger.error(appContext, "CourseRepository",
                                    "Không thể đọc chi tiết khóa học", exception);
                            callback.onError(new ApiError(0,
                                    "Dữ liệu chi tiết khóa học không hợp lệ", false));
                        }
                    }

                    @Override
                    public void onError(ApiError error) {
                        callback.onError(error);
                    }
                });
    }

    public void loadLesson(String courseId, String lessonId,
                           ApiCallback<LessonContent> callback) {
        if (courseId == null || courseId.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã khóa học không hợp lệ", false));
            return;
        }
        String endpoint = "student/courses/" + courseId.trim() + "/player/";
        if (lessonId != null && !lessonId.trim().isEmpty()) {
            endpoint += lessonId.trim() + "/";
        }
        apiClient.get(endpoint, true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject progress = response.optJSONObject("progress");
                    String text = SafeJson.string(response, "", "text_content", "introduction");
                    callback.onSuccess(new LessonContent(
                            SafeJson.string(response, "", "id"),
                            SafeJson.string(response, "Bài học", "title"),
                            SafeJson.string(response, "text", "content_type"),
                            SafeJson.string(response, "", "video_file", "video_url"),
                            SafeJson.string(response, "", "document_file"),
                            text,
                            SafeJson.bool(progress, false, "completed")
                    ));
                } catch (Exception exception) {
                    AppLogger.error(appContext, "CourseRepository",
                            "Không thể đọc nội dung bài học", exception);
                    callback.onError(new ApiError(0, "Nội dung bài học không hợp lệ", false));
                }
            }

            @Override
            public void onError(ApiError error) {
                callback.onError(error);
            }
        });
    }

    public void markLessonCompleted(String lessonId, ApiCallback<Boolean> callback) {
        if (lessonId == null || lessonId.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã bài học không hợp lệ", false));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("video_watched", true);
            body.put("completed", true);
            apiClient.post("content/lessons/" + lessonId.trim() + "/progress/",
                    body, true, new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            callback.onSuccess(SafeJson.bool(data, true, "completed"));
                        }

                        @Override
                        public void onError(ApiError error) {
                            callback.onError(error);
                        }
                    });
        } catch (Exception exception) {
            AppLogger.error(appContext, "CourseRepository", "Không thể cập nhật tiến độ", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị dữ liệu tiến độ", false));
        }
    }

    public void close() {
        // Không giữ tài nguyên cần đóng. Hàm được giữ để lifecycle gọi thống nhất.
    }

    private void loadCacheOrError(ApiCallback<CourseListResult> callback, ApiError originalError) {
        try {
            List<Course> cachedCourses = readMemoryCache();
            if (cachedCourses.isEmpty()) {
                callback.onError(originalError);
            } else {
                callback.onSuccess(new CourseListResult(cachedCourses, true,
                        originalError.getMessage()
                                + ". Đang hiển thị dữ liệu tạm của lần tải gần nhất."));
            }
        } catch (Exception exception) {
            AppLogger.error(appContext, "CourseRepository",
                    "Không thể trả dữ liệu tạm lên giao diện", exception);
            callback.onError(originalError);
        }
    }

    private void saveMemoryCache(List<Course> courses) {
        synchronized (CACHE_LOCK) {
            memoryCache.clear();
            if (courses != null) {
                memoryCache.addAll(courses);
            }
        }
    }

    private List<Course> readMemoryCache() {
        synchronized (CACHE_LOCK) {
            return new ArrayList<>(memoryCache);
        }
    }

    private List<Course> parseCourses(JSONArray array) {
        List<Course> courses = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object != null) {
                Course course = parseCourse(object);
                if (!course.getId().isEmpty()) {
                    courses.add(course);
                }
            }
        }
        return courses;
    }

    private Course parseCourse(JSONObject object) {
        String description = SafeJson.string(object, "", "description", "introduction");
        return new Course(
                SafeJson.string(object, "", "id"),
                SafeJson.string(object, "Khóa học", "title"),
                SafeJson.string(object, "", "gradeLabel", "grade", "gradeNumber"),
                SafeJson.string(object, "", "subject", "subjectSlug", "subject_name"),
                SafeJson.string(object, "", "teacherName", "teacher_name"),
                SafeJson.integer(object, 0, "lessonsCount", "lessons_count"),
                SafeJson.integer(object, 0, "progress"),
                SafeJson.decimal(object, 0, "price"),
                SafeJson.string(object, "", "thumbnail", "thumbnail_url"),
                description,
                SafeJson.bool(object, true, "isEnrolled", "is_enrolled")
        );
    }

    private JSONArray mergeArrays(JSONArray first, JSONArray second) {
        JSONArray result = new JSONArray();
        for (int index = 0; index < first.length(); index++) {
            result.put(first.opt(index));
        }
        for (int index = 0; index < second.length(); index++) {
            result.put(second.opt(index));
        }
        return result;
    }
}
