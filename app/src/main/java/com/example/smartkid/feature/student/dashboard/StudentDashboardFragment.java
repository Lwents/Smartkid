package com.example.smartkid.feature.student.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.Course;
import com.example.smartkid.data.model.DashboardSummary;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.DashboardRepository;
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.feature.student.course.CatalogActivity;
import com.example.smartkid.feature.student.course.CourseDetailActivity;
import com.example.smartkid.feature.shared.notification.FeatureListActivity;
import com.example.smartkid.feature.student.ai.LearningAnalysisActivity;

public class StudentDashboardFragment extends Fragment {
    private ProgressBar loadingView;
    private View contentView;
    private View errorView;
    private TextView welcomeText;
    private TextView courseCountText;
    private TextView examCountText;
    private TextView resumeTitleText;
    private TextView resumeProgressText;
    private Button resumeButton;
    private Button retryButton;
    private SwipeRefreshLayout refreshLayout;
    private DashboardRepository repository;
    private StudentFeatureRepository featureRepository;
    private TextView streakText;
    private TextView notificationBadge;
    private View notificationButton;
    private Course resumeCourse;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home_fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            repository = new DashboardRepository(requireContext());
            featureRepository = new StudentFeatureRepository(requireContext());
            bindViews(view);
            SessionManager sessionManager = new SessionManager(requireContext());
            String displayName = sessionManager.getUser().getFullName();
            welcomeText.setText(getString(R.string.welcome_student,
                    displayName.isEmpty() ? "bạn" : displayName));
            retryButton.setOnClickListener(clicked -> safeLoadDashboard());
            resumeButton.setOnClickListener(clicked -> openResumeCourse());
            refreshLayout.setOnRefreshListener(this::safeLoadDashboard);
            bindQuickActions(view);
            safeLoadDashboard();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentDashboardFragment", "Không thể tạo dashboard", exception);
            showInlineError("Không thể khởi tạo trang chủ");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null && featureRepository != null) loadNotificationBadge();
    }

    private void bindViews(View view) {
        loadingView = view.findViewById(R.id.progressDashboard);
        contentView = view.findViewById(R.id.dashboardContent);
        errorView = view.findViewById(R.id.dashboardError);
        refreshLayout = view.findViewById(R.id.dashboardRefresh);
        welcomeText = view.findViewById(R.id.textWelcome);
        courseCountText = view.findViewById(R.id.textCourseCount);
        examCountText = view.findViewById(R.id.textExamCount);
        resumeTitleText = view.findViewById(R.id.textResumeTitle);
        resumeProgressText = view.findViewById(R.id.textResumeProgress);
        resumeButton = view.findViewById(R.id.buttonResumeCourse);
        retryButton = view.findViewById(R.id.buttonRetryDashboard);
        if (loadingView == null || contentView == null || errorView == null
                || welcomeText == null || courseCountText == null || examCountText == null
                || resumeTitleText == null || resumeProgressText == null
                || resumeButton == null || retryButton == null) {
            throw new IllegalStateException("Giao diện trang chủ thiếu thành phần bắt buộc");
        }
    }

    private void bindQuickActions(View view) {
        notificationButton = view.findViewById(R.id.buttonDashboardNotifications);
        notificationBadge = view.findViewById(R.id.textDashboardNotificationBadge);
        View aiButton = view.findViewById(R.id.buttonQuickAi);
        View analysisButton = view.findViewById(R.id.buttonQuickAnalysis);
        View catalogButton = view.findViewById(R.id.buttonQuickCatalog);
        if (notificationButton == null || notificationBadge == null
                || aiButton == null || analysisButton == null
                || catalogButton == null) {
            throw new IllegalStateException("Giao diện thao tác nhanh chưa đầy đủ");
        }
        streakText = view.findViewById(R.id.textHomeStreakDays);
        notificationButton.setOnClickListener(clicked -> openNotifications());
        // Ô lửa nay hiển thị chuỗi ngày học -> mở màn phân tích học tập (nơi có
        // chi tiết chuỗi học và khôi phục chuỗi), không mở AI Tutor nữa.
        aiButton.setOnClickListener(clicked -> openActivity(LearningAnalysisActivity.class));
        analysisButton.setOnClickListener(clicked -> openActivity(LearningAnalysisActivity.class));
        catalogButton.setOnClickListener(clicked -> openActivity(CatalogActivity.class));
    }

    private void loadNotificationBadge() {
        featureRepository.loadNotifications(new ApiCallback<java.util.List<com.example.smartkid.data.model.FeatureItem>>() {
            @Override
            public void onSuccess(java.util.List<com.example.smartkid.data.model.FeatureItem> items) {
                if (!isAdded() || notificationBadge == null || notificationButton == null) return;
                int unread = 0;
                if (items != null) {
                    for (com.example.smartkid.data.model.FeatureItem item : items) {
                        if (item != null && !com.example.smartkid.common.util.SafeJson.bool(
                                item.getSource(), false, "is_read", "isRead")) {
                            unread++;
                        }
                    }
                }
                showNotificationBadge(unread);
            }

            @Override
            public void onError(ApiError error) {
                if (!isAdded() || notificationBadge == null) return;
                notificationBadge.setVisibility(View.GONE);
            }
        });
    }

    private void showNotificationBadge(int unread) {
        if (notificationBadge == null || notificationButton == null) return;
        if (unread <= 0) {
            notificationBadge.setVisibility(View.GONE);
            notificationButton.setContentDescription(getString(R.string.open_notifications));
            return;
        }
        notificationBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
        notificationBadge.setVisibility(View.VISIBLE);
        notificationBadge.setScaleX(0.7f);
        notificationBadge.setScaleY(0.7f);
        notificationBadge.setAlpha(0f);
        notificationBadge.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();
        notificationButton.setContentDescription(
                getString(R.string.notification_badge_description, unread));
    }

    private void openNotifications() {
        if (!isAdded()) {
            return;
        }
        try {
            Intent intent = new Intent(requireContext(), FeatureListActivity.class);
            intent.putExtra(FeatureListActivity.EXTRA_MODE, FeatureListActivity.MODE_NOTIFICATIONS);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentDashboardFragment", "Không thể mở thông báo", exception);
            showInlineError("Không thể mở thông báo");
        }
    }

    private void openActivity(Class<?> destination) {
        if (!isAdded()) {
            return;
        }
        try {
            startActivity(new Intent(requireContext(), destination));
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentDashboardFragment", "Không thể mở chức năng nhanh", exception);
            if (getActivity() instanceof BaseActivity) {
                ((BaseActivity) getActivity()).showShortMessage("Không thể mở chức năng đã chọn");
            }
        }
    }

    /** Ô lửa hiển thị số ngày học liên tiếp thay cho nhãn "AI Tutor". */
    private void loadStreak() {
        if (streakText == null) return;
        new com.example.smartkid.data.repository.StudentFeatureRepository(requireContext())
                .loadLearningAnalysis(new ApiCallback<org.json.JSONObject>() {
                    @Override
                    public void onSuccess(org.json.JSONObject data) {
                        if (!isAdded() || streakText == null) return;
                        org.json.JSONObject daily = data.optJSONObject("daily_goal");
                        if (daily == null) daily = data.optJSONObject("daily");
                        int streak = com.example.smartkid.common.util.SafeJson
                                .integer(daily, 0, "streak");
                        streakText.setText(getResources().getQuantityString(
                                R.plurals.home_streak_days, streak, streak));
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isAdded() || streakText == null) return;
                        streakText.setText(getResources().getQuantityString(
                                R.plurals.home_streak_days, 0, 0));
                    }
                });
    }

    private void safeLoadDashboard() {
        try {
            loadDashboard();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentDashboardFragment", "Không thể tải dashboard", exception);
            showInlineError("Không thể tải trang chủ");
        }
    }

    private void loadDashboard() {
        setLoading(true);
        repository.loadDashboard(new ApiCallback<DashboardSummary>() {
            @Override
            public void onSuccess(DashboardSummary data) {
                if (!isAdded() || getView() == null) {
                    return;
                }
                setLoading(false);
                contentView.setVisibility(View.VISIBLE);
                errorView.setVisibility(View.GONE);
                resumeCourse = data.getResumeCourse();
                courseCountText.setText(String.valueOf(data.getFeaturedCourseCount()));
                examCountText.setText(String.valueOf(data.getPreviewExamCount()));
                if (resumeCourse == null) {
                    resumeTitleText.setText(R.string.no_learning_course);
                    resumeProgressText.setText(R.string.choose_course_hint);
                    resumeButton.setEnabled(false);
                } else {
                    resumeTitleText.setText(resumeCourse.getTitle());
                    resumeProgressText.setText(getString(R.string.progress_percent,
                            resumeCourse.getProgress()));
                    resumeButton.setEnabled(true);
                }
                loadStreak();
            }

            @Override
            public void onError(ApiError error) {
                if (!isAdded() || getView() == null) {
                    return;
                }
                setLoading(false);
                if (error.isSessionExpired() && getActivity() instanceof BaseActivity) {
                    ((BaseActivity) getActivity()).handleApiError(error);
                } else {
                    showInlineError(error.getMessage());
                }
            }
        });
    }

    private void openResumeCourse() {
        if (resumeCourse == null || resumeCourse.getId().isEmpty()) {
            return;
        }
        try {
            Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
            intent.putExtra(AppConstants.EXTRA_COURSE_ID, resumeCourse.getId());
            intent.putExtra(AppConstants.EXTRA_COURSE_TITLE, resumeCourse.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentDashboardFragment", "Không thể mở khóa học", exception);
        }
    }

    private void setLoading(boolean loading) {
        boolean swipeRefreshing = refreshLayout != null && refreshLayout.isRefreshing();
        if (!loading && refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
        if (loadingView != null) {
            loadingView.setVisibility(loading && !swipeRefreshing ? View.VISIBLE : View.GONE);
        }
        if (loading && !swipeRefreshing && contentView != null) {
            contentView.setVisibility(View.GONE);
        }
        if (loading && errorView != null) {
            errorView.setVisibility(View.GONE);
        }
    }

    private void showInlineError(String message) {
        if (getView() == null) {
            return;
        }
        setLoading(false);
        contentView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        TextView errorText = errorView.findViewById(R.id.textDashboardError);
        if (errorText != null) {
            errorText.setText(message);
        }
    }
}
