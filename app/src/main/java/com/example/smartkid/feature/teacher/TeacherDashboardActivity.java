package com.example.smartkid.feature.teacher;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.example.smartkid.R;
import com.example.smartkid.common.ui.LiquidGlassUi;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.model.User;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.feature.management.RoleDashboardActivity;
import com.example.smartkid.feature.teacher.data.TeacherDashboardRepository;
import com.example.smartkid.feature.teacher.model.TeacherDashboardData;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Native teacher home based on SunEdu's quick actions and course overview. */
public final class TeacherDashboardActivity extends RoleDashboardActivity {
    private static final String[] MANAGEMENT_KEYS = {
            "teacher_qa", "teacher_courses", "teacher_content_library", "teacher_classes",
            "teacher_assignments", "teacher_live", "teacher_exams", "teacher_exam_reports",
            "teacher_students", "teacher_progress", "teacher_feedback",
            "teacher_notifications"
    };

    private TeacherDashboardRepository repository;
    private ProgressBar progressBar;
    private TextView statusText;
    private LinearLayout coursesContainer;
    private NestedScrollView dashboardScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireRole("teacher", "instructor")) return;
        try {
            setContentView(R.layout.teacher_activity_dashboard);
            LiquidGlassUi.useStatusBarBackdrop(this, R.id.teacherDashboardRoot,
                    R.drawable.teacher_bg_header, false);
            LiquidGlassUi.useDarkNavigationBar(this);
            repository = new TeacherDashboardRepository(this);
            bindViews();
            bindHeader();
            bindActions();
            populateManagementActions(findViewById(R.id.containerTeacherManagement), MANAGEMENT_KEYS);
            loadDashboard();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherDashboardActivity", "Không thể tạo dashboard", exception);
            showErrorDialog("Không thể mở bảng điều khiển giáo viên");
        }
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressTeacherDashboard);
        statusText = findViewById(R.id.textTeacherDashboardStatus);
        coursesContainer = findViewById(R.id.containerTeacherCourses);
        dashboardScroll = findViewById(R.id.teacherDashboardScroll);
        if (progressBar == null || statusText == null || coursesContainer == null
                || dashboardScroll == null) {
            throw new IllegalStateException("Dashboard giáo viên thiếu thành phần bắt buộc");
        }
    }

    private void bindHeader() {
        User user = currentUser();
        String name = user.getFullName().isEmpty() ? user.getUsername() : user.getFullName();
        ((TextView) findViewById(R.id.textTeacherWelcome)).setText(
                getString(R.string.teacher_welcome_format, name));
        bindLogoutAction(R.id.buttonTeacherLogout);
    }

    private void bindActions() {
        findViewById(R.id.buttonTeacherNavOverview).setSelected(true);
        findViewById(R.id.buttonTeacherRefresh).setOnClickListener(view -> loadDashboard());
        findViewById(R.id.buttonTeacherCreateCourse).setOnClickListener(view ->
                openCreate("teacher_courses"));
        findViewById(R.id.buttonTeacherCreateExam).setOnClickListener(view ->
                openCreate("teacher_exams"));
        findViewById(R.id.buttonTeacherReports).setOnClickListener(view ->
                openManagementFeature("teacher_exam_reports"));
        findViewById(R.id.buttonTeacherNavOverview).setOnClickListener(view ->
                dashboardScroll.smoothScrollTo(0, 0));
        findViewById(R.id.buttonTeacherNavCourses).setOnClickListener(view ->
                openManagementFeature("teacher_courses"));
        findViewById(R.id.buttonTeacherNavExams).setOnClickListener(view ->
                openManagementFeature("teacher_exams"));
        findViewById(R.id.buttonTeacherNavStudents).setOnClickListener(view ->
                openManagementFeature("teacher_students"));
    }

    private void loadDashboard() {
        setLoading(true, getString(R.string.loading_dashboard));
        repository.load(new ApiCallback<TeacherDashboardData>() {
            @Override
            public void onSuccess(TeacherDashboardData data) {
                if (!isUsable()) return;
                setLoading(false, "");
                render(data);
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false, error == null ? getString(R.string.dashboard_load_error)
                        : error.getMessage());
                if (error != null && error.isSessionExpired()) handleApiError(error);
            }
        });
    }

    private void render(TeacherDashboardData data) {
        setText(R.id.textTeacherCourseCount, number(data.getCourseCount()));
        setText(R.id.textTeacherStudentCount, number(data.getStudentCount()));
        setText(R.id.textTeacherLessonCount, number(data.getLessonCount()));
        renderCourses(data.getCourses());
    }

    private void renderCourses(List<TeacherDashboardData.CourseItem> courses) {
        coursesContainer.removeAllViews();
        TextView empty = findViewById(R.id.textTeacherCoursesEmpty);
        empty.setVisibility(courses.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (TeacherDashboardData.CourseItem item : courses) {
            View row = inflater.inflate(R.layout.teacher_item_course_summary, coursesContainer, false);
            ((TextView) row.findViewById(R.id.textTeacherCourseTitle)).setText(item.getTitle());
            ((TextView) row.findViewById(R.id.textTeacherCourseMeta)).setText(
                    getString(R.string.teacher_course_meta_format,
                            item.getEnrolled(), item.getLessons()));
            ((TextView) row.findViewById(R.id.textTeacherCourseStatus)).setText(
                    statusLabel(item.getStatus()));
            row.setOnClickListener(view -> openManagementFeature("teacher_courses"));
            coursesContainer.addView(row);
        }
    }

    private String statusLabel(String status) {
        if ("published".equalsIgnoreCase(status)) return getString(R.string.status_published);
        if ("archived".equalsIgnoreCase(status)) return getString(R.string.status_archived);
        return getString(R.string.status_draft);
    }

    private void setLoading(boolean loading, String message) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        statusText.setText(message);
        statusText.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setText(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }

    private String number(int value) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(value);
    }

    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }
}
