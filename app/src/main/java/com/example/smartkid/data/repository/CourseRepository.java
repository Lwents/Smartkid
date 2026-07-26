package com.example.smartkid.data.repository;

import android.content.Context;

import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.MediaUrl;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.model.Course;
import com.example.smartkid.data.model.CourseDetail;
import com.example.smartkid.data.model.CourseSection;
import com.example.smartkid.data.model.CourseListResult;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.model.Lesson;
import com.example.smartkid.data.model.LessonContent;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Nhóm API khóa học và bài học, cho cả học sinh và phần xem trước của giáo viên.
 * 
 * Có thêm bộ đệm trong bộ nhớ (memoryCache): khi mất mạng vẫn hiển thị được danh sách
 * khóa học đã tải lần trước thay vì bỏ trống màn hình.
 */
public class CourseRepository {
    private static final Object CACHE_LOCK = new Object();
    private static final List<Course> memoryCache = new ArrayList<>();

    private final Context appContext;
    private final ApiClient apiClient;

    public CourseRepository(Context context) {
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
    }

    /** Danh sách khóa học đang học; lỗi mạng thì lấy bộ đệm. */
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
                            new ApiError(0, "Không đọc được dữ liệu khóa học, vui lòng thử lại", false));
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

    /** Danh mục khóa học đã xuất bản, có tìm kiếm theo từ khóa. */
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

    /** Ghi danh vào khóa học. */
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

    /** Rời khỏi khóa học. */
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

    /** Chi tiết khóa học kèm danh sách chương và bài học. */
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
                            // Giữ nguyên cấu trúc chương mà giáo viên đã tạo: trước đây
                            // chỗ này gộp hết bài của mọi chương vào một danh sách nên
                            // học sinh chỉ thấy bài học, không thấy chương nào cả.
                            List<CourseSection> sectionList = new ArrayList<>();
                            JSONArray sections = SafeJson.array(response, "sections");
                            for (int sectionIndex = 0; sectionIndex < sections.length(); sectionIndex++) {
                                JSONObject section = sections.optJSONObject(sectionIndex);
                                if (section == null) {
                                    continue;
                                }
                                List<Lesson> lessons = new ArrayList<>();
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
                                sectionList.add(new CourseSection(
                                        SafeJson.string(section, "", "id"),
                                        SafeJson.string(section, "", "title"),
                                        lessons));
                            }
                            callback.onSuccess(new CourseDetail(course, sectionList));
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

    /** Xem trước bài học cho giáo viên: đọc thẳng content API, không cần enrollment. */
    public void loadLessonPreview(String lessonId, ApiCallback<LessonContent> callback) {
        if (lessonId == null || lessonId.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã bài học không hợp lệ", false));
            return;
        }
        loadLessonFrom("content/lessons/" + lessonId.trim() + "/", callback);
    }

    /** Nội dung bài học cho học sinh (qua player API, có kiểm tra ghi danh). */
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
        loadLessonFrom(endpoint, callback);
    }

    /** Phần đọc JSON dùng chung cho cả hai đường trên; đổi link media về dạng tuyệt đối. */
    private void loadLessonFrom(String endpoint, ApiCallback<LessonContent> callback) {
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
                            MediaUrl.absolute(
                                    SafeJson.string(response, "", "video_file", "video_url")),
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

    /** Ghi nhận đã học xong bài (cập nhật tiến độ). */
    public void markLessonCompleted(String lessonId, ApiCallback<Boolean> callback) {
        if (lessonId == null || lessonId.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã bài học không hợp lệ", false));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("video_watched", true);
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

    /** Danh sách bài luyện tập gắn trong bài học. */
    public void loadLessonExercises(String lessonId, ApiCallback<List<FeatureItem>> callback) {
        if (lessonId == null || lessonId.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã bài học không hợp lệ", false));
            return;
        }
        apiClient.getValue("activities/exercises/?lesson_id=" + lessonId.trim()
                        + "&status=published", true, new ApiCallback<Object>() {
                    @Override
                    public void onSuccess(Object data) {
                        try {
                            JSONArray items = data instanceof JSONArray ? (JSONArray) data
                                    : SafeJson.array(data instanceof JSONObject
                                            ? (JSONObject) data : new JSONObject(),
                                    "results", "items");
                            List<FeatureItem> result = new ArrayList<>();
                            String expectedLessonId = lessonId.trim();
                            for (int index = 0; index < items.length(); index++) {
                                JSONObject item = items.optJSONObject(index);
                                if (item == null) continue;
                                Object lessonValue = item.opt("lesson");
                                String linkedLessonId = "";
                                if (lessonValue instanceof JSONObject) {
                                    linkedLessonId = SafeJson.string(
                                            (JSONObject) lessonValue, "", "id", "uuid");
                                } else if (lessonValue != null
                                        && lessonValue != JSONObject.NULL) {
                                    linkedLessonId = String.valueOf(lessonValue).trim();
                                }
                                if (!expectedLessonId.equals(linkedLessonId)
                                        || !SafeJson.bool(item, false, "published")) {
                                    continue;
                                }
                                String id = SafeJson.string(item, "", "id");
                                if (id.isEmpty()) continue;
                                JSONObject settings = item.optJSONObject("settings");
                                int questions = SafeJson.array(item, "questions").length();
                                int seconds = SafeJson.integer(settings, 0,
                                        "duration_seconds", "time_limit_seconds");
                                result.add(new FeatureItem(id,
                                        SafeJson.string(item, "Bài luyện tập", "title"),
                                        questions + " câu hỏi",
                                        seconds > 0 ? Math.max(1, seconds / 60) + " phút" : "",
                                        "Sẵn sàng", item));
                            }
                            callback.onSuccess(result);
                        } catch (Exception exception) {
                            AppLogger.error(appContext, "CourseRepository",
                                    "Không thể đọc bài luyện tập", exception);
                            callback.onError(new ApiError(0,
                                    "Dữ liệu bài luyện tập không hợp lệ", false));
                        }
                    }

                    @Override
                    public void onError(ApiError error) {
                        callback.onError(error);
                    }
                });
    }

    /** Kiểm tra bài học đã được mở chưa (phải học xong bài trước). */
    public void checkLessonUnlock(String lessonId, ApiCallback<JSONObject> callback) {
        if (lessonId == null || lessonId.trim().isEmpty()) {
            callback.onSuccess(new JSONObject());
            return;
        }
        apiClient.get("content/lessons/" + lessonId.trim() + "/unlock-check/",
                true, callback);
    }

    /** Gửi điểm bài luyện tập và cập nhật tiến độ bài học. */
    public void markExerciseCompleted(String lessonId, double score,
                                      ApiCallback<Boolean> callback) {
        if (lessonId == null || lessonId.trim().isEmpty()) {
            callback.onError(new ApiError(0, "Mã bài học không hợp lệ", false));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("exercise_completed", true);
            body.put("exercise_score", score);
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
            AppLogger.error(appContext, "CourseRepository",
                    "Không thể cập nhật tiến độ bài tập", exception);
            callback.onError(new ApiError(0, "Không thể lưu tiến độ bài tập", false));
        }
    }

    /** Hủy các request đang chờ khi màn hình đóng, tránh rò bộ nhớ. */
    public void close() {
        // Không giữ tài nguyên cần đóng. Hàm được giữ để lifecycle gọi thống nhất.
    }

    /** Mất mạng: trả bộ đệm nếu có, không có thì báo lỗi gốc. */
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

    /** Lưu danh sách vừa tải vào bộ đệm. */
    private void saveMemoryCache(List<Course> courses) {
        synchronized (CACHE_LOCK) {
            memoryCache.clear();
            if (courses != null) {
                memoryCache.addAll(courses);
            }
        }
    }

    /** Đọc bản sao bộ đệm (trả bản copy để nơi khác không sửa được dữ liệu gốc). */
    private List<Course> readMemoryCache() {
        synchronized (CACHE_LOCK) {
            return new ArrayList<>(memoryCache);
        }
    }

    /** Đổi mảng JSON thành danh sách Course. */
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

    /** Đổi một JSON thành Course, chấp nhận nhiều cách đặt tên trường của server. */
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
                SafeJson.string(object, "", "thumbnail", "thumbnail_url"),
                description,
                SafeJson.bool(object, true, "isEnrolled", "is_enrolled")
        );
    }

    /** Ghép hai mảng khi server chia khóa học thành nhóm base và supp. */
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
