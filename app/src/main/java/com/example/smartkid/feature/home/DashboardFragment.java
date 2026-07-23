package com.example.smartkid.feature.home;

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

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.Course;
import com.example.smartkid.data.model.DashboardSummary;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.DashboardRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.feature.course.CatalogActivity;
import com.example.smartkid.feature.course.CourseDetailActivity;
import com.example.smartkid.feature.ai.AITutorActivity;
import com.example.smartkid.feature.notification.FeatureListActivity;
import com.example.smartkid.feature.ai.LearningAnalysisActivity;

public class DashboardFragment extends Fragment {
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
    private DashboardRepository repository;
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
            bindViews(view);
            SessionManager sessionManager = new SessionManager(requireContext());
            String displayName = sessionManager.getUser().getFullName();
            welcomeText.setText(getString(R.string.welcome_student,
                    displayName.isEmpty() ? "bạn" : displayName));
            retryButton.setOnClickListener(clicked -> safeLoadDashboard());
            resumeButton.setOnClickListener(clicked -> openResumeCourse());
            bindQuickActions(view);
            safeLoadDashboard();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "DashboardFragment", "Không thể tạo dashboard", exception);
            showInlineError("Không thể khởi tạo trang chủ");
        }
    }

    private void bindViews(View view) {
        loadingView = view.findViewById(R.id.progressDashboard);
        contentView = view.findViewById(R.id.dashboardContent);
        errorView = view.findViewById(R.id.dashboardError);
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
        View notificationButton = view.findViewById(R.id.buttonDashboardNotifications);
        View aiButton = view.findViewById(R.id.buttonQuickAi);
        View analysisButton = view.findViewById(R.id.buttonQuickAnalysis);
        View catalogButton = view.findViewById(R.id.buttonQuickCatalog);
        if (notificationButton == null || aiButton == null || analysisButton == null
                || catalogButton == null) {
            throw new IllegalStateException("Giao diện thao tác nhanh chưa đầy đủ");
        }
        notificationButton.setOnClickListener(clicked -> openNotifications());
        aiButton.setOnClickListener(clicked -> openActivity(AITutorActivity.class));
        analysisButton.setOnClickListener(clicked -> openActivity(LearningAnalysisActivity.class));
        catalogButton.setOnClickListener(clicked -> openActivity(CatalogActivity.class));
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
            AppLogger.error(getContext(), "DashboardFragment", "Không thể mở thông báo", exception);
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
            AppLogger.error(getContext(), "DashboardFragment", "Không thể mở chức năng nhanh", exception);
            if (getActivity() instanceof BaseActivity) {
                ((BaseActivity) getActivity()).showShortMessage("Không thể mở chức năng đã chọn");
            }
        }
    }

    private void safeLoadDashboard() {
        try {
            loadDashboard();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "DashboardFragment", "Không thể tải dashboard", exception);
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
            AppLogger.error(getContext(), "DashboardFragment", "Không thể mở khóa học", exception);
        }
    }

    private void setLoading(boolean loading) {
        if (loadingView != null) {
            loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (loading && contentView != null) {
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
