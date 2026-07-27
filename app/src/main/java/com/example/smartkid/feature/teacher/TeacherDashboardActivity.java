package com.example.smartkid.feature.teacher;

import android.animation.TimeInterpolator;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.smartkid.R;
import com.example.smartkid.common.ui.LiquidGlassUi;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.model.User;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.feature.shared.profile.ChangePasswordActivity;
import com.example.smartkid.feature.shared.profile.ProfileEditActivity;
import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.ui.FeatureSpec;
import com.example.smartkid.common.ui.RoleDashboardActivity;
import com.example.smartkid.common.ui.chart.ActivityChartView;
import com.example.smartkid.common.ui.form.ExerciseScope;
import com.example.smartkid.feature.teacher.course.TeacherCourseCreateActivity;
import com.example.smartkid.feature.teacher.exercise.TeacherExerciseEditorActivity;
import com.example.smartkid.feature.teacher.data.TeacherDashboardRepository;
import com.example.smartkid.feature.teacher.model.TeacherDashboardData;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native teacher home based on SmartKid's quick actions and course overview. */
public final class TeacherDashboardActivity extends RoleDashboardActivity {
    private static final int PAGE_OVERVIEW = 0;
    private static final int PAGE_COURSES = 1;
    private static final int PAGE_EXAMS = 2;
    private static final int PAGE_STUDENTS = 3;
    private static final String STATE_SELECTED_PAGE = "teacher_selected_page";
    private static final TimeInterpolator NAVIGATION_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0f, 1f);

    private TeacherDashboardRepository repository;
    private ProgressBar progressBar;
    private TextView statusText;
    private LinearLayout coursesContainer;
    private NestedScrollView dashboardScroll;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout[] refreshLayouts;
    private ActivityChartView activityChart;
    private TextView chartEmpty;
    private TextView[] chartTabs;
    private ViewFlipper pageFlipper;
    private FrameLayout bottomNavigation;
    private View navigationIndicator;
    private TextView[] navItems;
    private int selectedPage;
    private float swipeStartX;
    private float swipeStartY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireRole(UserRole.TEACHER)) return;
        try {
            setContentView(R.layout.teacher_activity_dashboard);
            LiquidGlassUi.useStatusBarBackdrop(this, R.id.teacherDashboardRoot,
                    R.drawable.admin_bg_screen, true);
            findViewById(R.id.teacherDashboardRoot).setBackgroundResource(
                    R.drawable.admin_bg_screen);
            repository = new TeacherDashboardRepository(this);
            bindViews();
            bindHeader();
            bindActions();
            bindNavigation();
            int restoredPage = savedInstanceState == null ? PAGE_OVERVIEW
                    : savedInstanceState.getInt(STATE_SELECTED_PAGE, PAGE_OVERVIEW);
            selectPage(Math.max(PAGE_OVERVIEW, Math.min(PAGE_STUDENTS, restoredPage)), false);
            loadDashboard();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherDashboardActivity", "Không thể tạo dashboard", exception);
            showErrorDialog("Không thể mở bảng điều khiển giáo viên");
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (repository != null) loadDashboard();
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressTeacherDashboard);
        statusText = findViewById(R.id.textTeacherDashboardStatus);
        coursesContainer = findViewById(R.id.containerTeacherCourses);
        dashboardScroll = findViewById(R.id.teacherDashboardScroll);
        activityChart = findViewById(R.id.teacherActivityChart);
        chartEmpty = findViewById(R.id.textTeacherChartEmpty);
        pageFlipper = findViewById(R.id.teacherPageFlipper);
        bottomNavigation = findViewById(R.id.teacherBottomNavigation);
        navigationIndicator = findViewById(R.id.teacherNavSelectionIndicator);
        navItems = new TextView[]{findViewById(R.id.buttonTeacherNavOverview),
                findViewById(R.id.buttonTeacherNavCourses),
                findViewById(R.id.buttonTeacherNavExams),
                findViewById(R.id.buttonTeacherNavStudents)};
        chartTabs = new TextView[]{findViewById(R.id.buttonTeacherChart7),
                findViewById(R.id.buttonTeacherChart30),
                findViewById(R.id.buttonTeacherChart90),
                findViewById(R.id.buttonTeacherChartCustom)};
        if (progressBar == null || statusText == null || coursesContainer == null
                || dashboardScroll == null || activityChart == null || chartEmpty == null
                || pageFlipper == null || bottomNavigation == null
                || navigationIndicator == null) {
            throw new IllegalStateException("Dashboard giáo viên thiếu thành phần bắt buộc");
        }
    }

    private void bindHeader() {
        User user = currentUser();
        String name = user.getFullName().isEmpty() ? user.getUsername() : user.getFullName();
        ((TextView) findViewById(R.id.textTeacherWelcome)).setText(
                getString(R.string.teacher_welcome_format));
        ((TextView) findViewById(R.id.textTeacherAvatar)).setText(initials(name));
        // Avatar mở menu tài khoản (hồ sơ / đổi mật khẩu / đăng xuất) thay vì
        // đăng xuất ngay — bấm nhầm avatar không còn làm mất phiên đăng nhập.
        findViewById(R.id.buttonTeacherLogout).setOnClickListener(view -> showAccountMenu(name));
    }

    private void showAccountMenu(String displayName) {
        String[] labels = {
                getString(R.string.account_menu_profile),
                getString(R.string.account_menu_change_password),
                getString(R.string.logout),
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle(displayName.isEmpty() ? getString(R.string.account_menu_title) : displayName)
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) openAccountScreen(ProfileEditActivity.class);
                    else if (which == 1) openAccountScreen(ChangePasswordActivity.class);
                    else confirmLogout();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openAccountScreen(Class<?> target) {
        try {
            startActivity(new Intent(this, target));
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherDashboardActivity", "Không thể mở trang tài khoản", exception);
            showErrorDialog("Không thể mở trang tài khoản");
        }
    }

    private void bindActions() {
        findViewById(R.id.buttonTeacherRefresh).setOnClickListener(view -> loadDashboard());
        refreshLayouts = new androidx.swiperefreshlayout.widget.SwipeRefreshLayout[]{
                findViewById(R.id.refreshTeacherOverview),
                findViewById(R.id.refreshTeacherCourses),
                findViewById(R.id.refreshTeacherExams),
                findViewById(R.id.refreshTeacherStudents)};
        for (androidx.swiperefreshlayout.widget.SwipeRefreshLayout layout : refreshLayouts) {
            if (layout != null) layout.setOnRefreshListener(this::loadDashboard);
        }
        findViewById(R.id.buttonTeacherNotifications).setOnClickListener(view ->
                openManagementFeature("teacher_notifications"));
        findViewById(R.id.buttonTeacherCreateCourse).setOnClickListener(view ->
                openCreate("teacher_courses"));
        findViewById(R.id.buttonTeacherCreateExam).setOnClickListener(view ->
                openCreate("teacher_exams"));
        findViewById(R.id.buttonTeacherReports).setOnClickListener(view ->
                openManagementFeature("teacher_exam_reports"));
        findViewById(R.id.buttonTeacherStudents).setOnClickListener(view ->
                openManagementFeature("teacher_students"));
        findViewById(R.id.buttonTeacherViewAllCourses).setOnClickListener(view ->
                openManagementFeature("teacher_courses"));
        findViewById(R.id.buttonTeacherPageManageCourses).setOnClickListener(view ->
                openManagementFeature("teacher_courses"));
        findViewById(R.id.buttonTeacherPageCreateCourse).setOnClickListener(view ->
                openCreate("teacher_courses"));
        findViewById(R.id.buttonTeacherPageManageExams).setOnClickListener(view ->
                openManagementFeature("teacher_exams"));
        findViewById(R.id.buttonTeacherPageCreateExam).setOnClickListener(view ->
                openCreate("teacher_exams"));
        findViewById(R.id.buttonTeacherPageExamReports).setOnClickListener(view ->
                openManagementFeature("teacher_exam_reports"));
        findViewById(R.id.buttonTeacherPageStudents).setOnClickListener(view ->
                openManagementFeature("teacher_students"));
        findViewById(R.id.buttonTeacherPageProgress).setOnClickListener(view ->
                openManagementFeature("teacher_progress"));
        findViewById(R.id.buttonTeacherPageFeedback).setOnClickListener(view ->
                openManagementFeature("teacher_feedback"));
        findViewById(R.id.buttonTeacherPageNotifications).setOnClickListener(view ->
                openManagementFeature("teacher_notifications"));
        for (int index = 0; index < chartTabs.length; index++) {
            final int selected = index;
            chartTabs[index].setOnClickListener(view -> selectChartTab(selected));
        }
    }

    private void bindNavigation() {
        navItems[0].setOnClickListener(view -> {
            if (selectedPage == PAGE_OVERVIEW) dashboardScroll.smoothScrollTo(0, 0);
            else selectPage(PAGE_OVERVIEW, true);
        });
        navItems[1].setOnClickListener(view -> selectPage(PAGE_COURSES, true));
        navItems[2].setOnClickListener(view -> selectPage(PAGE_EXAMS, true));
        navItems[3].setOnClickListener(view -> selectPage(PAGE_STUDENTS, true));
        bottomNavigation.post(() -> updateNavigationIndicator(selectedPage, false));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            swipeStartX = event.getX();
            swipeStartY = event.getY();
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP && bottomNavigation != null
                && event.getY() < bottomNavigation.getTop()) {
            float distanceX = event.getX() - swipeStartX;
            float distanceY = event.getY() - swipeStartY;
            if (Math.abs(distanceX) > dp(56)
                    && Math.abs(distanceX) > Math.abs(distanceY) * 1.2f) {
                int target = selectedPage + (distanceX < 0 ? 1 : -1);
                selectPage(Math.max(PAGE_OVERVIEW, Math.min(PAGE_STUDENTS, target)), true);
            }
        }
        return handled;
    }

    private void selectPage(int page, boolean animate) {
        if (page < PAGE_OVERVIEW || page > PAGE_STUDENTS) return;
        boolean changed = page != selectedPage || pageFlipper.getDisplayedChild() != page;
        int previousPage = selectedPage;
        pageFlipper.setInAnimation(null);
        pageFlipper.setOutAnimation(null);
        resetPageTransforms();
        selectedPage = page;
        pageFlipper.setDisplayedChild(page);
        if (animate && changed) animatePageEntry(page, page > previousPage);
        updateNavigationIndicator(page, animate && changed);
        for (int index = 0; index < navItems.length; index++) {
            navItems[index].setSelected(index == page);
        }
    }

    private void resetPageTransforms() {
        for (int index = 0; index < pageFlipper.getChildCount(); index++) {
            View child = pageFlipper.getChildAt(index);
            child.animate().cancel();
            child.setAlpha(1f);
            child.setScaleX(1f);
            child.setScaleY(1f);
            child.setTranslationX(0f);
        }
    }

    private void animatePageEntry(int page, boolean forward) {
        View child = pageFlipper.getChildAt(page);
        if (child == null) return;
        child.setAlpha(0.94f);
        child.setScaleX(0.995f);
        child.setScaleY(0.995f);
        child.setTranslationX(dp(forward ? 12 : -12));
        child.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .setDuration(210L)
                .setInterpolator(NAVIGATION_INTERPOLATOR)
                .withLayer()
                .start();
    }

    private void updateNavigationIndicator(int page, boolean animate) {
        int availableWidth = bottomNavigation.getWidth()
                - bottomNavigation.getPaddingLeft() - bottomNavigation.getPaddingRight();
        if (availableWidth <= 0) return;
        int itemWidth = Math.round(availableWidth / (float) navItems.length);
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) navigationIndicator.getLayoutParams();
        if (params.width != itemWidth) {
            params.width = itemWidth;
            navigationIndicator.setLayoutParams(params);
        }
        float target = itemWidth * page;
        navigationIndicator.animate().cancel();
        if (animate) {
            navigationIndicator.animate().translationX(target).setDuration(260L)
                    .setInterpolator(NAVIGATION_INTERPOLATOR).withLayer().start();
        } else {
            navigationIndicator.setTranslationX(target);
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_PAGE, selectedPage);
        super.onSaveInstanceState(outState);
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
        setText(R.id.textTeacherExamCount, number(data.getExamCount()));
        setText(R.id.textTeacherPendingCount, number(data.getAttemptCount()));
        setRate(R.id.textTeacherCourseRate, data.getCoursePublishedRate(),
                R.string.teacher_rate_published_format);
        setRate(R.id.textTeacherStudentRate, data.getStudentActiveRate(),
                R.string.teacher_rate_active_format);
        setRate(R.id.textTeacherLessonRate, data.getLessonPublishedRate(),
                R.string.teacher_rate_published_format);
        setRate(R.id.textTeacherExamRate, data.getExamPublishedRate(),
                R.string.teacher_rate_published_format);
        setRate(R.id.textTeacherSubmissionRate, data.getAttemptSubmittedRate(),
                R.string.teacher_rate_submitted_format);
        setRate(R.id.textTeacherCompletionRate, data.getCompletionRate(),
                R.string.teacher_rate_completed_format);
        setText(R.id.textTeacherCompletion, getString(
                R.string.teacher_percentage_format, data.getCompletionRate()));
        setText(R.id.textTeacherChartTotal, getString(R.string.teacher_chart_total_format,
                number(data.getStudentCount())));
        renderActivityChart(data.getCourses());
        renderCourses(data.getCourses());
    }

    private void renderActivityChart(List<TeacherDashboardData.CourseItem> courses) {
        List<String> labels = new ArrayList<>();
        List<Float> values = new ArrayList<>();
        int count = Math.min(courses.size(), 7);
        for (int index = 0; index < count; index++) {
            TeacherDashboardData.CourseItem item = courses.get(index);
            labels.add(getString(R.string.teacher_chart_course_label, index + 1));
            values.add((float) item.getEnrolled());
        }
        activityChart.setData(labels, values);
        chartEmpty.setVisibility(values.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void renderCourses(List<TeacherDashboardData.CourseItem> courses) {
        coursesContainer.removeAllViews();
        findViewById(R.id.cardTeacherCoursesEmpty).setVisibility(
                courses.isEmpty() ? View.VISIBLE : View.GONE);
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

    private void selectChartTab(int selectedIndex) {
        for (int index = 0; index < chartTabs.length; index++) {
            boolean selected = index == selectedIndex;
            chartTabs[index].setBackgroundResource(selected
                    ? R.drawable.admin_bg_chart_tab_selected : android.R.color.transparent);
            chartTabs[index].setTextColor(ContextCompat.getColor(this, selected
                    ? R.color.admin_primary : R.color.admin_text_secondary));
            chartTabs[index].setTypeface(null, selected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private String statusLabel(String status) {
        if ("published".equalsIgnoreCase(status)) return getString(R.string.status_published);
        if ("archived".equalsIgnoreCase(status)) return getString(R.string.status_archived);
        return getString(R.string.status_draft);
    }

    private void setLoading(boolean loading, String message) {
        boolean swiping = false;
        if (refreshLayouts != null) {
            for (androidx.swiperefreshlayout.widget.SwipeRefreshLayout layout : refreshLayouts) {
                if (layout == null) continue;
                if (!loading) layout.setRefreshing(false);
                else if (layout.isRefreshing()) swiping = true;
            }
        }
        progressBar.setVisibility(loading && !swiping ? View.VISIBLE : View.GONE);
        statusText.setText(message);
        statusText.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setText(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }

    private void setRate(int id, int value, int formatRes) {
        TextView view = findViewById(id);
        view.setText(getString(formatRes, value));
        int color = value >= 80 ? R.color.admin_green
                : value >= 40 ? R.color.admin_orange
                : value > 0 ? R.color.admin_red : R.color.admin_text_muted;
        view.setTextColor(ContextCompat.getColor(this, color));
    }

    private String number(int value) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(value);
    }

    private String initials(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return "GV";
        String[] parts = normalized.split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(new Locale("vi", "VN"));
    }

    private void openManagementFeature(String key) {
        FeatureSpec spec = TeacherManagementSpec.get(key);
        if (spec == null || !spec.isAllowedForRole(currentRole())) {
            showErrorDialog("Tài khoản không có quyền mở chức năng này");
            return;
        }
        if (!spec.isAvailable()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle(spec.getTitle())
                    .setMessage(spec.getUnavailableReason()
                            + "\n\nỨng dụng không tạo dữ liệu giả khi backend chưa sẵn sàng.")
                    .setPositiveButton("Đã hiểu", null)
                    .show();
            return;
        }
        try {
            Intent intent = new Intent(this, TeacherManagementActivity.class);
            intent.putExtra(TeacherManagementActivity.EXTRA_SPEC_KEY, key);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherDashboardActivity", "Không thể mở chức năng", exception);
            showErrorDialog("Không thể mở chức năng quản lý");
        }
    }

    private void openCreate(String key) {
        if (!currentRole().isTeacher()) {
            showErrorDialog("Tài khoản không có quyền tạo dữ liệu này");
            return;
        }
        try {
            Intent intent;
            if ("teacher_exams".equals(key)) {
                intent = new Intent(this, TeacherExerciseEditorActivity.class);
                intent.putExtra(TeacherExerciseEditorActivity.EXTRA_SCOPE,
                        ExerciseScope.STANDALONE_EXAM.name());
            } else {
                intent = new Intent(this, TeacherCourseCreateActivity.class);
            }
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherDashboardActivity", "Không thể mở biểu mẫu", exception);
            showErrorDialog("Không thể mở biểu mẫu tạo mới");
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }
}
