package com.example.smartkid.ui.student;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.R;
import com.example.smartkid.core.AppConstants;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.core.SafeJson;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.ui.common.BaseActivity;
import com.example.smartkid.ui.common.FeatureItemAdapter;
import com.example.smartkid.ui.courses.LessonPlayerActivity;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Phân tích tiến độ, gợi ý bài tiếp theo, đánh giá đầu vào và streak bằng API. */
public class LearningAnalysisActivity extends BaseActivity {
    private StudentFeatureRepository repository;
    private FeatureItemAdapter adapter;
    private ProgressBar progressBar;
    private TextView summaryText;
    private TextView statusText;
    private View assessmentButton;
    private View restoreButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_learning_analysis);
            repository = new StudentFeatureRepository(this);
            bindViews();
            MaterialToolbar toolbar = findViewById(R.id.toolbarLearningAnalysis);
            ListView list = findViewById(R.id.listLearningSuggestions);
            TextView empty = findViewById(R.id.textLearningSuggestionsEmpty);
            if (toolbar == null || list == null || empty == null) {
                throw new IllegalStateException("Giao diện phân tích học tập chưa đầy đủ");
            }
            toolbar.setNavigationOnClickListener(view -> finish());
            adapter = new FeatureItemAdapter(this);
            list.setAdapter(adapter);
            list.setEmptyView(empty);
            list.setOnItemClickListener((parent, row, position, id) ->
                    openSuggestion(adapter.getItem(position)));
            findViewById(R.id.buttonLearningAnalysisRefresh).setOnClickListener(view -> loadSafely());
            assessmentButton.setOnClickListener(view -> chooseAssessmentCourse());
            restoreButton.setOnClickListener(view -> confirmRestore());
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "LearningAnalysisActivity", "Không thể tạo phân tích", exception);
            showErrorDialog("Không thể mở phân tích học tập");
        }
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressLearningAnalysis);
        summaryText = findViewById(R.id.textLearningAnalysisSummary);
        statusText = findViewById(R.id.textLearningAnalysisStatus);
        assessmentButton = findViewById(R.id.buttonStartAssessment);
        restoreButton = findViewById(R.id.buttonRestoreStreak);
        if (progressBar == null || summaryText == null || statusText == null
                || assessmentButton == null || restoreButton == null) {
            throw new IllegalStateException("Giao diện phân tích thiếu thành phần bắt buộc");
        }
    }

    private void loadSafely() {
        setLoading(true);
        repository.loadLearningAnalysis(new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                try {
                    setLoading(false);
                    bindAnalysis(data == null ? new JSONObject() : data);
                } catch (Exception exception) {
                    AppLogger.error(LearningAnalysisActivity.this,
                            "LearningAnalysisActivity", "Không thể đọc phân tích", exception);
                    showStatus("Dữ liệu phân tích từ server không hợp lệ");
                }
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void bindAnalysis(JSONObject data) {
        JSONObject analysis = data.optJSONObject("analysis");
        JSONObject daily = data.optJSONObject("daily_goal");
        boolean hasCourses = SafeJson.bool(data, false, "has_courses");
        String summary = "Tiến độ tổng thể: " + SafeJson.integer(analysis, 0, "overall_progress") + "%"
                + "\nBài học: " + SafeJson.integer(analysis, 0, "completed_lessons")
                + "/" + SafeJson.integer(analysis, 0, "total_lessons")
                + "\nBài tập: " + SafeJson.integer(analysis, 0, "completed_exercises")
                + "/" + SafeJson.integer(analysis, 0, "total_exercises")
                + "\nĐiểm trung bình: " + SafeJson.decimal(analysis, 0, "avg_score")
                + "\nMục tiêu hôm nay: " + SafeJson.integer(daily, 0, "completed")
                + "/" + SafeJson.integer(daily, 0, "target")
                + " • Chuỗi học: " + SafeJson.integer(daily, 0, "streak") + " ngày";
        summaryText.setText(summary);
        assessmentButton.setEnabled(hasCourses);

        List<FeatureItem> suggestions = new ArrayList<>();
        JSONArray source = SafeJson.array(data, "suggestions");
        for (int index = 0; index < source.length(); index++) {
            JSONObject item = source.optJSONObject(index);
            if (item == null) continue;
            suggestions.add(new FeatureItem(
                    SafeJson.string(item, "", "lesson_id", "exercise_id"),
                    SafeJson.string(item, "Gợi ý học tập", "title"),
                    SafeJson.string(item, "", "subtitle"),
                    SafeJson.string(item, "", "reason"),
                    SafeJson.integer(item, 0, "estimated_time") + " phút", item));
        }
        adapter.setItems(suggestions);
        showStatus(SafeJson.string(data,
                hasCourses ? "Phân tích được tính từ tiến độ thật trên server"
                        : "Bạn chưa đăng ký khóa học", "message", "ai_message"));
    }

    private void openSuggestion(FeatureItem item) {
        if (item == null) return;
        try {
            JSONObject source = item.getSource();
            String courseId = SafeJson.string(source, "", "course_id");
            String lessonId = SafeJson.string(source, "", "lesson_id");
            if (courseId.isEmpty() || lessonId.isEmpty()) {
                showShortMessage("Gợi ý này chưa liên kết với một bài học");
                return;
            }
            Intent intent = new Intent(this, LessonPlayerActivity.class);
            intent.putExtra(AppConstants.EXTRA_COURSE_ID, courseId);
            intent.putExtra(AppConstants.EXTRA_LESSON_ID, lessonId);
            intent.putExtra(AppConstants.EXTRA_LESSON_TITLE, item.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "LearningAnalysisActivity", "Không thể mở gợi ý", exception);
            showErrorDialog("Không thể mở bài học được gợi ý");
        }
    }

    private void chooseAssessmentCourse() {
        setLoading(true);
        repository.loadLearningPath(new ApiCallback<List<FeatureItem>>() {
            @Override
            public void onSuccess(List<FeatureItem> courses) {
                if (!isUsable()) return;
                setLoading(false);
                if (courses == null || courses.isEmpty()) {
                    showStatus("Bạn cần đăng ký khóa học trước khi đánh giá đầu vào");
                    return;
                }
                String[] labels = new String[courses.size()];
                for (int index = 0; index < courses.size(); index++) {
                    labels[index] = courses.get(index).getTitle();
                }
                new AlertDialog.Builder(LearningAnalysisActivity.this)
                        .setTitle("Chọn khóa học đánh giá")
                        .setItems(labels, (dialog, which) -> openAssessment(courses.get(which)))
                        .setNegativeButton(R.string.cancel, null).show();
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void openAssessment(FeatureItem course) {
        try {
            Intent intent = new Intent(this, AssessmentActivity.class);
            intent.putExtra(AppConstants.EXTRA_COURSE_ID, course.getId());
            intent.putExtra(AppConstants.EXTRA_COURSE_TITLE, course.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "LearningAnalysisActivity", "Không thể mở đánh giá", exception);
            showErrorDialog("Không thể mở đánh giá đầu vào");
        }
    }

    private void confirmRestore() {
        new AlertDialog.Builder(this).setTitle("Khôi phục chuỗi học")
                .setMessage("Server chỉ cho phép khôi phục khi chuỗi học đã mất và còn lượt trong tháng.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Kiểm tra và khôi phục", (dialog, which) -> restoreSafely())
                .show();
    }

    private void restoreSafely() {
        setLoading(true);
        repository.restoreLearningStreak(new ApiCallback<String>() {
            @Override
            public void onSuccess(String message) {
                if (!isUsable()) return;
                showShortMessage(message);
                loadSafely();
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        assessmentButton.setEnabled(!loading);
        restoreButton.setEnabled(!loading);
    }

    private void showStatus(String message) {
        statusText.setText(message == null ? getString(R.string.unknown_error) : message);
        statusText.setVisibility(View.VISIBLE);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
