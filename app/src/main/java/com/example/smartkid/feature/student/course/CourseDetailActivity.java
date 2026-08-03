package com.example.smartkid.feature.student.course;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.model.Course;
import com.example.smartkid.data.model.CourseDetail;
import com.example.smartkid.data.model.Lesson;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.CourseRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.squareup.picasso.Picasso;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.smartkid.common.util.SwipeRefreshFix;

public class CourseDetailActivity extends BaseActivity {
    private MaterialToolbar toolbar;
    private ImageView thumbnail;
    private TextView titleText;
    private TextView metaText;
    private TextView teacherText;
    private TextView descriptionText;
    private TextView emptyText;
    private ProgressBar progressBar;
    private ProgressBar learningProgress;
    private TextView learningProgressText;
    private Button startButton;
    private Button retryButton;
    private ListView lessonList;
    private SwipeRefreshLayout refreshLayout;
    private LessonAdapter lessonAdapter;
    private CourseRepository repository;
    private CourseDetail courseDetail;
    private String courseId;

    /** Khởi tạo màn chi tiết khóa học và đọc courseId từ Intent. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.course_activity_detail);
            courseId = getIntent().getStringExtra(AppConstants.EXTRA_COURSE_ID);
            if (courseId == null || courseId.trim().isEmpty()) {
                showErrorDialog("Không tìm thấy mã khóa học");
                return;
            }
            repository = new CourseRepository(this);
            bindViews();
            lessonAdapter = new LessonAdapter(this);
            lessonList.setAdapter(lessonAdapter);
            lessonList.setEmptyView(emptyText);
            lessonList.setOnItemClickListener((parent, view, position, id) ->
                    openLesson(lessonAdapter.getItem(position)));
            toolbar.setNavigationOnClickListener(view -> finish());
            startButton.setOnClickListener(view -> handlePrimaryAction());
            retryButton.setOnClickListener(view -> safeLoadDetail());

            String intentTitle = getIntent().getStringExtra(AppConstants.EXTRA_COURSE_TITLE);
            toolbar.setTitle(intentTitle == null ? getString(R.string.course_detail) : intentTitle);
            safeLoadDetail();
        } catch (Exception exception) {
            AppLogger.error(this, "CourseDetailActivity", "Không thể tạo chi tiết khóa học", exception);
            showErrorDialog("Không thể mở chi tiết khóa học: " + exception.getMessage());
        }
    }

    /** Tải lại chi tiết sau khi quay về từ bài học hoặc thao tác ghi danh. */
    @Override
    protected void onRestart() {
        super.onRestart();
        safeLoadDetail();
    }

    /** Kết thúc tài nguyên Repository theo lifecycle của Activity. */
    @Override
    protected void onDestroy() {
        if (repository != null) {
            repository.close();
        }
        super.onDestroy();
    }

    /** Ánh xạ phần thông tin khóa học, danh sách bài học và nút hành động chính. */
    private void bindViews() {
        toolbar = findViewById(R.id.toolbarCourseDetail);
        thumbnail = findViewById(R.id.imageCourseDetail);
        titleText = findViewById(R.id.textCourseDetailTitle);
        metaText = findViewById(R.id.textCourseDetailMeta);
        teacherText = findViewById(R.id.textCourseDetailTeacher);
        descriptionText = findViewById(R.id.textCourseDescription);
        emptyText = findViewById(R.id.textLessonsEmpty);
        progressBar = findViewById(R.id.progressCourseDetailLoading);
        learningProgress = findViewById(R.id.progressCourseDetail);
        learningProgressText = findViewById(R.id.textCourseDetailProgress);
        startButton = findViewById(R.id.buttonStartLearning);
        retryButton = findViewById(R.id.buttonRetryCourseDetail);
        lessonList = findViewById(R.id.listLessons);
        refreshLayout = findViewById(R.id.refreshCourseDetail);
        SwipeRefreshFix.attach(refreshLayout);
        if (refreshLayout != null) {
            refreshLayout.setOnRefreshListener(this::safeLoadDetail);
        }
    }

    /** Bao bước tải chi tiết để lỗi ngoài dự kiến không làm màn hình dừng. */
    private void safeLoadDetail() {
        try {
            loadDetail();
        } catch (Exception exception) {
            AppLogger.error(this, "CourseDetailActivity", "Không thể tải chi tiết", exception);
            setLoading(false);
            showErrorDialog("Không thể tải chi tiết khóa học");
        }
    }

    /** Gọi API chi tiết khóa học rồi dựng các section và lesson. */
    private void loadDetail() {
        setLoading(true);
        repository.loadCourseDetail(courseId, new ApiCallback<CourseDetail>() {
            @Override
            public void onSuccess(CourseDetail data) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                courseDetail = data;
                bindCourse(data.getCourse());
                lessonAdapter.setSections(data.getSections());
                startButton.setEnabled(!data.getCourse().isEnrolled()
                        || !data.getLessons().isEmpty());
                retryButton.setVisibility(View.GONE);
            }

            @Override
            public void onError(ApiError error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                retryButton.setVisibility(View.VISIBLE);
                handleApiError(error);
            }
        });
    }

    /** Gắn thông tin tổng quan và trạng thái ghi danh của khóa học lên UI. */
    private void bindCourse(Course course) {
        if (course == null) {
            throw new IllegalArgumentException("Khóa học rỗng");
        }
        toolbar.setTitle(course.getTitle());
        titleText.setText(course.getTitle());
        String meta = course.getSubject();
        if (!course.getGrade().isEmpty()) {
            meta += meta.isEmpty() ? course.getGrade() : " • " + course.getGrade();
        }
        metaText.setText(meta);
        teacherText.setText(course.getTeacherName().isEmpty()
                ? getString(R.string.teacher_updating)
                : getString(R.string.teacher_format, course.getTeacherName()));
        descriptionText.setText(course.getDescription().isEmpty()
                ? getString(R.string.no_course_description) : course.getDescription());
        learningProgress.setProgress(course.getProgress());
        learningProgressText.setText(getString(R.string.progress_percent, course.getProgress()));
        thumbnail.setImageResource(R.drawable.course_ic_placeholder);
        if (!course.getThumbnailUrl().isEmpty()) {
            Picasso.get().load(course.getThumbnailUrl())
                    .placeholder(R.drawable.course_ic_placeholder)
                    .error(R.drawable.course_ic_placeholder)
                    .fit().centerCrop().into(thumbnail);
        }
        if (course.isEnrolled()) {
            startButton.setText(R.string.start_learning);
        } else {
            startButton.setText(R.string.enroll_free);
        }
    }

    /** Ghi danh miễn phí hoặc mở bài học đầu tiên tùy trạng thái khóa học. */
    private void handlePrimaryAction() {
        if (courseDetail == null || courseDetail.getCourse() == null) {
            showShortMessage("Dữ liệu khóa học chưa sẵn sàng");
            return;
        }
        Course course = courseDetail.getCourse();
        if (course.isEnrolled()) {
            openFirstLesson();
        } else {
            enrollFreeCourse();
        }
    }

    /** Gọi API ghi danh miễn phí rồi tải lại dữ liệu khóa học. */
    private void enrollFreeCourse() {
        setLoading(true);
        repository.enroll(courseId, new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                if (isFinishing() || isDestroyed()) return;
                showShortMessage(getString(R.string.enrolled_success));
                safeLoadDetail();
            }

            @Override
            public void onError(ApiError error) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    /** Tìm bài học đầu tiên có thể học và mở LessonPlayerActivity. */
    private void openFirstLesson() {
        if (courseDetail == null || courseDetail.getLessons().isEmpty()) {
            showShortMessage("Khóa học chưa có bài học");
            return;
        }
        openLesson(courseDetail.getLessons().get(0));
    }

    /** Truyền thông tin course/lesson sang màn hình phát bài học. */
    private void openLesson(Lesson lesson) {
        if (courseDetail != null && !courseDetail.getCourse().isEnrolled()) {
            showShortMessage("Bạn cần đăng ký khóa học trước");
            return;
        }
        if (lesson == null || lesson.getId().isEmpty()) {
            showErrorDialog("Bài học không có mã hợp lệ");
            return;
        }
        if (courseDetail != null && !courseDetail.isLessonUnlocked(lesson.getId())) {
            Lesson blockingLesson = courseDetail.getBlockingLesson(lesson.getId());
            showShortMessage(blockingLesson == null
                    ? getString(R.string.lesson_locked)
                    : getString(R.string.complete_previous_lesson,
                            blockingLesson.getTitle()));
            return;
        }
        try {
            Intent intent = new Intent(this, LessonPlayerActivity.class);
            intent.putExtra(AppConstants.EXTRA_COURSE_ID, courseId);
            intent.putExtra(AppConstants.EXTRA_LESSON_ID, lesson.getId());
            intent.putExtra(AppConstants.EXTRA_LESSON_TITLE, lesson.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "CourseDetailActivity", "Không thể mở bài học", exception);
            showErrorDialog("Không thể mở bài học");
        }
    }

    /** Chuyển màn hình giữa trạng thái tải và nội dung khóa học. */
    private void setLoading(boolean loading) {
        if (!loading && refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
        boolean swiping = loading && refreshLayout != null && refreshLayout.isRefreshing();
        progressBar.setVisibility(loading && !swiping ? View.VISIBLE : View.GONE);
        startButton.setEnabled(!loading && courseDetail != null
                && (!courseDetail.getCourse().isEnrolled()
                || !courseDetail.getLessons().isEmpty()));
    }
}
