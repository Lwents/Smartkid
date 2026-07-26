package com.example.smartkid.feature.student.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartkid.R;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.ui.RingProgressView;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.common.util.SwipeRefreshFix;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.feature.student.course.LessonPlayerActivity;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Màn Phân tích học tập. Chỉ gồm 4 phần: khối chỉ số (vòng tiến độ + 5 ô số liệu),
 * thẻ động viên, danh sách bài học nên học tiếp, và nút khôi phục chuỗi học.
 */
public class LearningAnalysisActivity extends BaseActivity {

    private StudentFeatureRepository repository;
    private ProgressBar progressBar;
    private SwipeRefreshLayout refreshLayout;
    private RingProgressView ring;
    private View statLessons;
    private View statExercises;
    private View statAverage;
    private View statGoal;
    private View statStreak;
    private View encourageCard;
    private TextView encourageTitle;
    private TextView encourageMessage;
    private LinearLayout suggestionsContainer;
    private TextView suggestionsEmpty;
    private View restoreButton;

    // ===== KHỞI TẠO MÀN HÌNH =====

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.ai_activity_learning_analysis);
            repository = new StudentFeatureRepository(this);
            bindViews();
            MaterialToolbar toolbar = findViewById(R.id.toolbarLearningAnalysis);
            toolbar.setNavigationOnClickListener(view -> finish());
            refreshLayout.setOnRefreshListener(this::loadSafely);
            restoreButton.setOnClickListener(view -> confirmRestore());
            prepareStatTiles();
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "LearningAnalysisActivity", "Không thể tạo phân tích", exception);
            showErrorDialog("Không thể mở phân tích học tập");
        }
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressLearningAnalysis);
        refreshLayout = findViewById(R.id.refreshLearningAnalysis);
        ring = findViewById(R.id.ringOverallProgress);
        statLessons = findViewById(R.id.statLessons);
        statExercises = findViewById(R.id.statExercises);
        statAverage = findViewById(R.id.statAverage);
        statGoal = findViewById(R.id.statGoal);
        statStreak = findViewById(R.id.statStreak);
        encourageCard = findViewById(R.id.cardEncourage);
        encourageTitle = findViewById(R.id.textEncourageTitle);
        encourageMessage = findViewById(R.id.textEncourageMessage);
        suggestionsContainer = findViewById(R.id.containerSuggestions);
        suggestionsEmpty = findViewById(R.id.textLearningSuggestionsEmpty);
        restoreButton = findViewById(R.id.buttonRestoreStreak);
        SwipeRefreshFix.attach(refreshLayout);
        if (ring == null || statLessons == null || suggestionsContainer == null
                || restoreButton == null || refreshLayout == null) {
            throw new IllegalStateException("Giao diện phân tích thiếu thành phần bắt buộc");
        }
    }

    // ===== 5 Ô CHỈ SỐ =====

    /** Gắn sẵn biểu tượng, màu và nhãn cho từng ô; số liệu điền sau khi có dữ liệu. */
    private void prepareStatTiles() {
        setupStat(statLessons, R.drawable.ai_ic_lessons, R.drawable.ai_bg_circle_purple,
                R.string.analysis_stat_lessons);
        setupStat(statExercises, R.drawable.ai_ic_exercises, R.drawable.ai_bg_circle_teal,
                R.string.analysis_stat_exercises);
        setupStat(statAverage, R.drawable.ai_ic_star, R.drawable.ai_bg_circle_amber,
                R.string.analysis_stat_average);
        setupStat(statGoal, R.drawable.ai_ic_target, R.drawable.ai_bg_circle_rose,
                R.string.analysis_stat_goal);
        setupStat(statStreak, R.drawable.ai_ic_streak, R.drawable.ai_bg_circle_blue,
                R.string.analysis_stat_streak);
    }

    private void setupStat(View tile, int iconRes, int backgroundRes, int labelRes) {
        if (tile == null) return;
        ((ImageView) tile.findViewById(R.id.iconStat)).setImageResource(iconRes);
        tile.findViewById(R.id.frameStatIcon).setBackgroundResource(backgroundRes);
        ((TextView) tile.findViewById(R.id.textStatLabel)).setText(labelRes);
    }

    /** Điền số liệu: phần đạt được in đậm, phần "/ tổng" nhạt hơn (để trống nếu không có tổng). */
    private void fillStat(View tile, String value, String total) {
        if (tile == null) return;
        ((TextView) tile.findViewById(R.id.textStatValue)).setText(value);
        TextView totalText = tile.findViewById(R.id.textStatTotal);
        totalText.setText(total);
        totalText.setVisibility(total == null || total.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ===== TẢI VÀ ĐỔ DỮ LIỆU =====

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
                    showEmpty("Không đọc được dữ liệu phân tích, vui lòng thử lại");
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

        ring.setPercent(SafeJson.integer(analysis, 0, "overall_progress"));

        fillStat(statLessons,
                String.valueOf(SafeJson.integer(analysis, 0, "completed_lessons")),
                "/ " + SafeJson.integer(analysis, 0, "total_lessons"));
        fillStat(statExercises,
                String.valueOf(SafeJson.integer(analysis, 0, "completed_exercises")),
                "/ " + SafeJson.integer(analysis, 0, "total_exercises"));
        fillStat(statAverage,
                String.format(java.util.Locale.US, "%.1f",
                        SafeJson.decimal(analysis, 0, "avg_score")), "");

        int done = SafeJson.integer(daily, 0, "completed");
        int target = SafeJson.integer(daily, 0, "target");
        fillStat(statGoal, String.valueOf(done), "/ " + target);

        int streak = SafeJson.integer(daily, 0, "streak");
        fillStat(statStreak, getString(R.string.analysis_streak_days, streak), "");

        bindEncourage(done, target);
        bindSuggestions(SafeJson.array(data, "suggestions"));
    }

    /** Thẻ động viên đổi lời theo việc học sinh còn thiếu mấy bài của mục tiêu hôm nay. */
    private void bindEncourage(int done, int target) {
        int remaining = target - done;
        if (target <= 0) {
            encourageTitle.setText(R.string.analysis_encourage_title);
            encourageMessage.setText(R.string.analysis_encourage_start);
        } else if (remaining <= 0) {
            encourageTitle.setText(R.string.analysis_encourage_done_title);
            encourageMessage.setText(R.string.analysis_encourage_done);
        } else {
            encourageTitle.setText(R.string.analysis_encourage_title);
            encourageMessage.setText(getString(R.string.analysis_encourage_remaining, remaining));
        }
        encourageCard.setVisibility(View.VISIBLE);
    }

    // ===== DANH SÁCH BÀI HỌC NÊN HỌC TIẾP =====

    private void bindSuggestions(JSONArray source) {
        suggestionsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int shown = 0;
        for (int index = 0; index < source.length(); index++) {
            JSONObject item = source.optJSONObject(index);
            if (item == null) continue;
            View card = inflater.inflate(
                    R.layout.ai_item_learning_suggestion, suggestionsContainer, false);

            ((TextView) card.findViewById(R.id.textSuggestionTitle))
                    .setText(SafeJson.string(item, "Gợi ý học tập", "title"));
            ((TextView) card.findViewById(R.id.textSuggestionSubtitle))
                    .setText(shortSubtitle(SafeJson.string(item, "", "subtitle")));
            ((TextView) card.findViewById(R.id.textSuggestionReason))
                    .setText(SafeJson.string(item, "", "reason"));
            ((TextView) card.findViewById(R.id.textSuggestionTime)).setText(
                    getString(R.string.analysis_minutes,
                            SafeJson.integer(item, 0, "estimated_time")));

            String courseId = SafeJson.string(item, "", "course_id");
            String lessonId = SafeJson.string(item, "", "lesson_id");
            String title = SafeJson.string(item, "", "title");
            card.setOnClickListener(view -> openSuggestion(courseId, lessonId, title));

            suggestionsContainer.addView(card);
            shown++;
        }
        if (shown == 0) showEmpty(getString(R.string.analysis_no_suggestion));
        else suggestionsEmpty.setVisibility(View.GONE);
    }

    /**
     * Server trả về dạng "Toán lớp 4 - Giải toán có lời văn - Chương 1: Kiến thức nền".
     * Chuỗi này dài quá một dòng nên bị cắt cụt; rút lại thành "Toán lớp 4 • Chương 1"
     * bằng cách bỏ tên phụ của khóa và bỏ phần mô tả sau dấu hai chấm của chương.
     */
    private String shortSubtitle(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String[] parts = raw.split(" - ");
        String course = parts[0].trim();
        String chapter = "";
        for (String part : parts) {
            String value = part.trim();
            if (value.startsWith("Chương")) {
                int colon = value.indexOf(':');
                chapter = colon > 0 ? value.substring(0, colon).trim() : value;
                break;
            }
        }
        return chapter.isEmpty() ? course : course + " • " + chapter;
    }

    private void showEmpty(String message) {
        suggestionsContainer.removeAllViews();
        suggestionsEmpty.setText(message);
        suggestionsEmpty.setVisibility(View.VISIBLE);
    }

    private void openSuggestion(String courseId, String lessonId, String title) {
        if (courseId.isEmpty() || lessonId.isEmpty()) {
            showShortMessage("Gợi ý này chưa liên kết với một bài học");
            return;
        }
        try {
            Intent intent = new Intent(this, LessonPlayerActivity.class);
            intent.putExtra(AppConstants.EXTRA_COURSE_ID, courseId);
            intent.putExtra(AppConstants.EXTRA_LESSON_ID, lessonId);
            intent.putExtra(AppConstants.EXTRA_LESSON_TITLE, title);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "LearningAnalysisActivity", "Không thể mở gợi ý", exception);
            showErrorDialog("Không thể mở bài học được gợi ý");
        }
    }

    // ===== KHÔI PHỤC CHUỖI HỌC =====

    private void confirmRestore() {
        new AlertDialog.Builder(this).setTitle(R.string.analysis_restore_streak)
                .setMessage("Chỉ khôi phục được khi chuỗi học đã mất và bạn còn lượt trong tháng này.")
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

    // ===== TIỆN ÍCH =====

    private void setLoading(boolean loading) {
        if (!loading && refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
        boolean swiping = loading && refreshLayout != null && refreshLayout.isRefreshing();
        progressBar.setVisibility(loading && !swiping ? View.VISIBLE : View.GONE);
        restoreButton.setEnabled(!loading);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
