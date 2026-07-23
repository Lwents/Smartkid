package com.example.smartkid.feature.exam;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.ExamRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Làm bài kiểm tra trắc nghiệm/text, đếm giờ và nộp kết quả về Django. */
public class ExamActivity extends BaseActivity {
    public static final String EXTRA_EXAM_ID = "exam_id";
    public static final String EXTRA_EXAM_TITLE = "exam_title";

    private final Map<String, View> answerViews = new LinkedHashMap<>();
    private String examId;
    private String attemptId;
    private int durationSeconds = 1800;
    private MaterialToolbar toolbar;
    private ProgressBar progressBar;
    private TextView infoText;
    private TextView timerText;
    private TextView statusText;
    private LinearLayout questionsContainer;
    private Button startButton;
    private Button submitButton;
    private Button rankingButton;
    private ExamRepository repository;
    private CountDownTimer timer;
    private boolean submitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.exam_activity_exam);
            examId = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_EXAM_ID);
            if (examId == null || examId.trim().isEmpty()) {
                showErrorDialog("Không tìm thấy mã bài kiểm tra");
                finish();
                return;
            }
            repository = new ExamRepository(this);
            bindViews();
            String title = getIntent().getStringExtra(EXTRA_EXAM_TITLE);
            toolbar.setTitle(title == null ? getString(R.string.exam_detail) : title);
            toolbar.setNavigationOnClickListener(view -> finish());
            startButton.setOnClickListener(view -> startSafely());
            submitButton.setOnClickListener(view -> confirmSubmit());
            rankingButton.setOnClickListener(view -> loadRanking());
            loadDetailSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "ExamActivity", "Không thể tạo màn hình", exception);
            showErrorDialog("Không thể mở bài kiểm tra");
        }
    }

    @Override
    protected void onDestroy() {
        cancelTimer();
        super.onDestroy();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarExam);
        progressBar = findViewById(R.id.progressExam);
        infoText = findViewById(R.id.textExamInfo);
        timerText = findViewById(R.id.textExamTimer);
        statusText = findViewById(R.id.textExamStatus);
        questionsContainer = findViewById(R.id.containerExamQuestions);
        startButton = findViewById(R.id.buttonStartExam);
        submitButton = findViewById(R.id.buttonSubmitExam);
        rankingButton = findViewById(R.id.buttonExamRanking);
        if (toolbar == null || progressBar == null || infoText == null || timerText == null
                || statusText == null || questionsContainer == null || startButton == null
                || submitButton == null || rankingButton == null) {
            throw new IllegalStateException("Giao diện bài kiểm tra thiếu thành phần bắt buộc");
        }
    }

    private void loadDetailSafely() {
        setLoading(true);
        repository.loadDetail(examId, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                setLoading(false);
                toolbar.setTitle(SafeJson.string(data, "Bài kiểm tra", "title"));
                durationSeconds = Math.max(60,
                        SafeJson.integer(data, 1800, "durationSec", "duration_seconds"));
                int count = SafeJson.integer(data, 0, "questionsCount", "questions_count");
                double pass = SafeJson.decimal(data, 0, "passScore", "pass_score");
                String description = SafeJson.string(data, "", "description");
                int minutes = Math.max(1, durationSeconds / 60);
                String questionCount = getResources().getQuantityString(
                        R.plurals.question_count_short, count, count);
                String minuteCount = getResources().getQuantityString(
                        R.plurals.minute_count_short, minutes, minutes);
                String examInfo = getString(R.string.exam_info_format, questionCount,
                        minuteCount, String.valueOf(pass));
                infoText.setText(description.isEmpty() ? examInfo
                        : getString(R.string.exam_info_with_description, examInfo, description));
                startButton.setEnabled(count > 0);
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void startSafely() {
        try {
            setLoading(true);
            repository.start(examId, new ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject data) {
                    if (!isUsable()) return;
                    setLoading(false);
                    attemptId = SafeJson.string(data, "", "id", "attempt_id");
                    JSONArray questions = SafeJson.array(data, "questions");
                    if (attemptId.isEmpty() || questions.length() == 0) {
                        showStatus("Server không trả về lượt làm bài hoặc câu hỏi");
                        return;
                    }
                    renderQuestions(questions);
                    startButton.setVisibility(View.GONE);
                    submitButton.setVisibility(View.VISIBLE);
                    timerText.setVisibility(View.VISIBLE);
                    startTimer(durationSeconds);
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "ExamActivity", "Không thể bắt đầu", exception);
            setLoading(false);
            showErrorDialog("Không thể bắt đầu làm bài");
        }
    }

    private void renderQuestions(JSONArray questions) {
        questionsContainer.removeAllViews();
        answerViews.clear();
        for (int index = 0; index < questions.length(); index++) {
            JSONObject question = questions.optJSONObject(index);
            if (question == null) continue;
            String questionId = SafeJson.string(question, "", "id");
            if (questionId.isEmpty()) continue;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, dp(6), 0, dp(10));
            card.setLayoutParams(cardParams);
            card.setBackgroundResource(R.drawable.common_bg_feature_card);

            TextView prompt = new TextView(this);
            prompt.setText(getString(R.string.numbered_question, index + 1,
                    SafeJson.string(question, "Câu hỏi", "text", "prompt", "question")));
            prompt.setTextColor(getColor(R.color.smartkid_text));
            prompt.setTextSize(16);
            prompt.setTypeface(prompt.getTypeface(), android.graphics.Typeface.BOLD);
            card.addView(prompt);

            JSONArray choices = SafeJson.array(question, "choices", "options");
            if (choices.length() > 0) {
                RadioGroup group = new RadioGroup(this);
                group.setOrientation(RadioGroup.VERTICAL);
                group.setPadding(0, dp(8), 0, 0);
                for (int choiceIndex = 0; choiceIndex < choices.length(); choiceIndex++) {
                    Object rawChoice = choices.opt(choiceIndex);
                    JSONObject choice = rawChoice instanceof JSONObject ? (JSONObject) rawChoice : null;
                    String choiceId = choice == null ? String.valueOf(choiceIndex)
                            : SafeJson.string(choice, String.valueOf(choiceIndex), "id");
                    String choiceText = choice == null ? String.valueOf(rawChoice)
                            : SafeJson.string(choice, "Đáp án", "text", "label");
                    RadioButton button = new RadioButton(this);
                    button.setId(View.generateViewId());
                    button.setTag(choiceId);
                    button.setText(choiceText);
                    button.setTextColor(getColor(R.color.smartkid_text));
                    button.setPadding(0, dp(4), 0, dp(4));
                    group.addView(button);
                }
                card.addView(group);
                answerViews.put(questionId, group);
            } else {
                EditText answer = new EditText(this);
                answer.setHint("Nhập câu trả lời");
                answer.setSingleLine(false);
                answer.setMinLines(2);
                card.addView(answer);
                answerViews.put(questionId, answer);
            }
            questionsContainer.addView(card);
        }
    }

    private void confirmSubmit() {
        if (submitting) return;
        try {
            int unanswered = countUnanswered();
            String message = unanswered == 0 ? "Bạn chắc chắn muốn nộp bài?"
                    : "Còn " + unanswered + " câu chưa trả lời. Vẫn nộp bài?";
            new AlertDialog.Builder(this)
                    .setTitle(R.string.submit_exam)
                    .setMessage(message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.submit_exam, (dialog, which) -> submitSafely())
                    .show();
        } catch (Exception exception) {
            AppLogger.error(this, "ExamActivity", "Không thể xác nhận nộp", exception);
            showErrorDialog("Không thể chuẩn bị nộp bài");
        }
    }

    private void submitSafely() {
        if (attemptId == null || attemptId.isEmpty() || submitting) return;
        try {
            submitting = true;
            setLoading(true);
            JSONObject answers = collectAnswers();
            repository.submit(examId, attemptId, answers, new ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    if (!isUsable()) return;
                    submitting = false;
                    setLoading(false);
                    cancelTimer();
                    showResult(result);
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    submitting = false;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            submitting = false;
            setLoading(false);
            AppLogger.error(this, "ExamActivity", "Không thể nộp bài", exception);
            showErrorDialog("Không thể chuẩn bị dữ liệu nộp bài");
        }
    }

    private JSONObject collectAnswers() throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, View> entry : answerViews.entrySet()) {
            View view = entry.getValue();
            JSONObject answer = new JSONObject();
            if (view instanceof RadioGroup) {
                int checked = ((RadioGroup) view).getCheckedRadioButtonId();
                RadioButton button = checked == -1 ? null : view.findViewById(checked);
                if (button != null && button.getTag() != null) {
                    answer.put("selected_choice_id", String.valueOf(button.getTag()));
                }
            } else if (view instanceof EditText) {
                answer.put("text", ((EditText) view).getText().toString().trim());
            }
            result.put(entry.getKey(), answer);
        }
        return result;
    }

    private int countUnanswered() {
        int count = 0;
        for (View view : answerViews.values()) {
            if (view instanceof RadioGroup && ((RadioGroup) view).getCheckedRadioButtonId() == -1) count++;
            else if (view instanceof EditText
                    && ((EditText) view).getText().toString().trim().isEmpty()) count++;
        }
        return count;
    }

    private void showResult(JSONObject result) {
        double score = SafeJson.decimal(result, 0, "totalScore", "total_score");
        double max = SafeJson.decimal(result, 0, "maxScore", "max_score");
        int correct = SafeJson.integer(result, 0, "correctCount", "correct_count");
        int total = SafeJson.integer(result, answerViews.size(), "totalCount", "total_count");
        boolean passed = SafeJson.bool(result, false, "passed");
        questionsContainer.setVisibility(View.GONE);
        submitButton.setVisibility(View.GONE);
        timerText.setVisibility(View.GONE);
        showStatus((passed ? "Đạt" : "Chưa đạt") + " • Điểm " + score + "/" + max
                + " • Đúng " + correct + "/" + total);
        rankingButton.setVisibility(View.VISIBLE);
    }

    private void loadRanking() {
        setLoading(true);
        repository.loadRanking(examId, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                setLoading(false);
                JSONArray top = SafeJson.array(data, "top");
                StringBuilder text = new StringBuilder();
                for (int index = 0; index < top.length(); index++) {
                    JSONObject row = top.optJSONObject(index);
                    if (row == null) continue;
                    text.append(index + 1).append(". ")
                            .append(SafeJson.string(row, "Học viên", "name"))
                            .append(" — ").append(SafeJson.decimal(row, 0, "score")).append('\n');
                }
                if (text.length() == 0) text.append("Chưa có dữ liệu xếp hạng");
                new AlertDialog.Builder(ExamActivity.this)
                        .setTitle(R.string.ranking).setMessage(text.toString().trim())
                        .setPositiveButton("Đã hiểu", null).show();
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void startTimer(int seconds) {
        cancelTimer();
        timer = new CountDownTimer(Math.max(1, seconds) * 1000L, 1000L) {
            @Override public void onTick(long millisUntilFinished) {
                long total = millisUntilFinished / 1000L;
                timerText.setText(getString(R.string.exam_timer_format,
                        total / 60, total % 60));
            }

            @Override public void onFinish() {
                timerText.setText(R.string.time_up);
                if (!submitting && isUsable()) submitSafely();
            }
        }.start();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        startButton.setEnabled(!loading);
        submitButton.setEnabled(!loading);
        rankingButton.setEnabled(!loading);
    }

    private void showStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
