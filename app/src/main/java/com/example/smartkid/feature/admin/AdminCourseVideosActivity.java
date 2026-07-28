package com.example.smartkid.feature.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.android.volley.Request;
import com.example.smartkid.R;
import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.ManagementRepository;
import com.example.smartkid.feature.student.course.LessonPlayerActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Admin can inspect or remove a course, and view or remove individual videos. */
public final class AdminCourseVideosActivity extends BaseActivity {
    public static final String EXTRA_COURSE_ID = "admin_course_id";
    public static final String EXTRA_COURSE_TITLE = "admin_course_title";

    private final List<VideoEntry> videos = new ArrayList<>();
    private ManagementRepository repository;
    private String courseId;
    private ProgressBar progress;
    private View content;
    private TextView status;
    private TextView courseTitle;
    private TextView courseMeta;
    private TextView courseTeacher;
    private TextView courseStatus;
    private TextView empty;
    private LinearLayout videosContainer;
    private MaterialButton deleteCourseButton;
    private String currentCourseTitle = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.admin_activity_course_videos);
            if (!UserRole.fromString(new SessionManager(this).getUser().getRole()).isAdmin()) {
                showErrorDialog(getString(R.string.admin_course_video_admin_only));
                finish();
                return;
            }
            courseId = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(EXTRA_COURSE_ID));
            if (courseId.isEmpty()) {
                showErrorDialog(getString(R.string.admin_course_video_invalid_course));
                finish();
                return;
            }
            repository = new ManagementRepository(this);
            bindViews();
            loadCourse();
        } catch (Exception exception) {
            AppLogger.error(this, "AdminCourseVideosActivity",
                    "Không thể mở danh sách video khóa học", exception);
            showErrorDialog(getString(R.string.admin_course_video_open_error));
        }
    }

    private void bindViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarAdminCourseVideos);
        progress = findViewById(R.id.progressAdminCourseVideos);
        content = findViewById(R.id.contentAdminCourseVideos);
        status = findViewById(R.id.textAdminCourseVideoStatus);
        courseTitle = findViewById(R.id.textAdminCourseVideoTitle);
        courseMeta = findViewById(R.id.textAdminCourseVideoMeta);
        courseTeacher = findViewById(R.id.textAdminCourseVideoTeacher);
        courseStatus = findViewById(R.id.textAdminCourseStatus);
        empty = findViewById(R.id.textAdminCourseVideoEmpty);
        videosContainer = findViewById(R.id.containerAdminCourseVideos);
        deleteCourseButton = findViewById(R.id.buttonAdminDeleteCourse);
        if (toolbar == null || progress == null || content == null || status == null
                || courseTitle == null || courseMeta == null || courseTeacher == null
                || courseStatus == null || empty == null || videosContainer == null
                || deleteCourseButton == null) {
            throw new IllegalStateException("Giao diện video khóa học chưa đầy đủ");
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        deleteCourseButton.setOnClickListener(view -> confirmDeleteCourse());
        String title = getIntent().getStringExtra(EXTRA_COURSE_TITLE);
        toolbar.setTitle(safe(title).isEmpty()
                ? getString(R.string.admin_course_videos_title) : title);
    }

    private void loadCourse() {
        setLoading(true, getString(R.string.admin_course_videos_loading));
        repository.loadObject(AdminCourseVideoActions.courseDetailEndpoint(courseId),
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        bindCourse(data == null ? new JSONObject() : data);
                        setLoading(false, "");
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false, error == null
                                ? getString(R.string.admin_course_video_load_error)
                                : error.getMessage());
                        if (error != null && error.isSessionExpired()) handleApiError(error);
                    }
                });
    }

    private void bindCourse(JSONObject source) {
        currentCourseTitle = SafeJson.string(source,
                getString(R.string.admin_course_fallback), "title");
        courseTitle.setText(currentCourseTitle);
        int lessons = SafeJson.integer(source, 0, "lessonsCount");
        int enrollments = SafeJson.integer(source, 0, "enrollments");
        courseMeta.setText(getString(R.string.admin_course_video_meta, lessons, enrollments));
        String teacher = SafeJson.string(source, "", "teacherName");
        courseTeacher.setText(teacher.isEmpty() ? ""
                : getString(R.string.admin_course_video_teacher, teacher));
        courseTeacher.setVisibility(teacher.isEmpty() ? View.GONE : View.VISIBLE);
        bindCourseStatus(SafeJson.string(source, "draft", "status"));

        videos.clear();
        JSONArray sections = SafeJson.array(source, "sections");
        for (int sectionIndex = 0; sectionIndex < sections.length(); sectionIndex++) {
            JSONObject section = sections.optJSONObject(sectionIndex);
            if (section == null) continue;
            String sectionTitle = SafeJson.string(section,
                    getString(R.string.admin_course_section_fallback), "title");
            JSONArray lessonsArray = SafeJson.array(section, "lessons");
            for (int lessonIndex = 0; lessonIndex < lessonsArray.length(); lessonIndex++) {
                JSONObject lesson = lessonsArray.optJSONObject(lessonIndex);
                if (lesson == null) continue;
                boolean hasVideo = SafeJson.bool(lesson, false, "hasVideo");
                String lessonId = SafeJson.string(lesson, "", "id");
                if (!AdminCourseVideoActions.canManageVideo(hasVideo, lessonId)) continue;
                videos.add(new VideoEntry(
                        lessonId,
                        SafeJson.string(lesson,
                                getString(R.string.admin_course_video_fallback), "title"),
                        sectionTitle,
                        SafeJson.string(lesson, "", "videoSource"),
                        SafeJson.bool(lesson, false, "published")
                ));
            }
        }
        renderVideos();
    }

    private void bindCourseStatus(String rawStatus) {
        String normalized = safe(rawStatus).toLowerCase(java.util.Locale.ROOT);
        boolean published = "published".equals(normalized);
        boolean archived = "archived".equals(normalized);
        courseStatus.setText(getString(R.string.admin_course_status_value,
                getString(archived ? R.string.status_archived
                        : published ? R.string.status_published : R.string.status_draft)));
    }

    private void confirmDeleteCourse() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_course_delete_title)
                .setMessage(getString(R.string.admin_course_delete_message, currentCourseTitle))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.admin_course_delete_confirm,
                        (dialog, which) -> deleteCourse())
                .show();
    }

    private void deleteCourse() {
        setLoading(true, getString(R.string.admin_course_deleting));
        repository.action(Request.Method.DELETE,
                AdminCourseVideoActions.deleteCourseEndpoint(courseId),
                new JSONObject(), new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        showShortMessage(getString(R.string.admin_course_deleted));
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false, "");
                        if (error == null) {
                            showErrorDialog(getString(R.string.admin_course_delete_error));
                        } else {
                            handleApiError(error);
                        }
                    }
                });
    }

    private void renderVideos() {
        videosContainer.removeAllViews();
        empty.setVisibility(videos.isEmpty() ? View.VISIBLE : View.GONE);
        for (VideoEntry video : videos) {
            View row = LayoutInflater.from(this).inflate(
                    R.layout.admin_item_course_video, videosContainer, false);
            ((TextView) row.findViewById(R.id.textAdminCourseVideoItemTitle))
                    .setText(video.title);
            ((TextView) row.findViewById(R.id.textAdminCourseVideoItemMeta))
                    .setText(getString(R.string.admin_course_video_item_meta,
                            video.sectionTitle,
                            sourceLabel(video.source),
                            getString(video.published
                                    ? R.string.status_published : R.string.status_draft)));
            MaterialButton viewButton = row.findViewById(R.id.buttonAdminViewCourseVideo);
            MaterialButton deleteButton = row.findViewById(R.id.buttonAdminDeleteCourseVideo);
            viewButton.setOnClickListener(view -> openPreview(video));
            deleteButton.setOnClickListener(view -> confirmDelete(video));
            videosContainer.addView(row);
        }
    }

    private void openPreview(VideoEntry video) {
        try {
            Intent intent = new Intent(this, LessonPlayerActivity.class);
            intent.putExtra(AppConstants.EXTRA_LESSON_ID, video.lessonId);
            intent.putExtra(AppConstants.EXTRA_LESSON_TITLE, video.title);
            intent.putExtra(LessonPlayerActivity.EXTRA_PREVIEW_MODE, true);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "AdminCourseVideosActivity",
                    "Không thể xem video khóa học", exception);
            showErrorDialog(getString(R.string.admin_course_video_preview_error));
        }
    }

    private void confirmDelete(VideoEntry video) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_course_video_delete_title)
                .setMessage(getString(R.string.admin_course_video_delete_message, video.title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete,
                        (dialog, which) -> deleteVideo(video))
                .show();
    }

    private void deleteVideo(VideoEntry video) {
        setLoading(true, getString(R.string.admin_course_video_deleting));
        repository.action(Request.Method.DELETE,
                AdminCourseVideoActions.deleteVideoEndpoint(courseId, video.lessonId),
                new JSONObject(), new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        showShortMessage(getString(R.string.admin_course_video_deleted));
                        loadCourse();
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false, "");
                        if (error == null) {
                            showErrorDialog(getString(R.string.admin_course_video_delete_error));
                        } else {
                            handleApiError(error);
                        }
                    }
                });
    }

    private String sourceLabel(String source) {
        return "link".equalsIgnoreCase(source)
                ? getString(R.string.admin_course_video_source_link)
                : getString(R.string.admin_course_video_source_file);
    }

    private void setLoading(boolean loading, String message) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        content.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        status.setText(safe(message));
        status.setVisibility(safe(message).isEmpty() ? View.GONE : View.VISIBLE);
    }

    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class VideoEntry {
        final String lessonId;
        final String title;
        final String sectionTitle;
        final String source;
        final boolean published;

        VideoEntry(String lessonId, String title, String sectionTitle,
                   String source, boolean published) {
            this.lessonId = lessonId;
            this.title = title;
            this.sectionTitle = sectionTitle;
            this.source = source;
            this.published = published;
        }
    }
}
