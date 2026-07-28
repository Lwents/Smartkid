package com.example.smartkid.feature.student.exam;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Làm bài kiểm tra trắc nghiệm/text, đếm giờ và nộp kết quả về Django. */
public class ExamActivity extends BaseActivity {
    public static final String EXTRA_EXAM_ID = "exam_id";
    public static final String EXTRA_EXAM_TITLE = "exam_title";

    private final Map<String, View> answerViews = new LinkedHashMap<>();
    private JSONArray attemptQuestions = new JSONArray();
    private JSONObject savedAnswers = new JSONObject();
    private int currentQuestionIndex = -1;
    private String examId;
    private String attemptId;
    private int durationSeconds = 1800;
    private MaterialToolbar toolbar;
    private ProgressBar progressBar;
    private TextView infoText;
    private TextView timerText;
    private TextView statusText;
    private LinearLayout questionsContainer;
    private ScrollView examScroll;
    private View questionNavigation;
    private TextView questionPosition;
    private Button previousQuestionButton;
    private Button nextQuestionButton;
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
        examScroll = findViewById(R.id.scrollExamContent);
        questionNavigation = findViewById(R.id.layoutExamQuestionNavigation);
        questionPosition = findViewById(R.id.textExamQuestionPosition);
        previousQuestionButton = findViewById(R.id.buttonExamPreviousQuestion);
        nextQuestionButton = findViewById(R.id.buttonExamNextQuestion);
        startButton = findViewById(R.id.buttonStartExam);
        submitButton = findViewById(R.id.buttonSubmitExam);
        rankingButton = findViewById(R.id.buttonExamRanking);
        if (toolbar == null || progressBar == null || infoText == null || timerText == null
                || statusText == null || questionsContainer == null || examScroll == null
                || questionNavigation == null || questionPosition == null
                || previousQuestionButton == null || nextQuestionButton == null
                || startButton == null || submitButton == null || rankingButton == null) {
            throw new IllegalStateException("Giao diện bài kiểm tra thiếu thành phần bắt buộc");
        }
        previousQuestionButton.setOnClickListener(view -> showQuestion(currentQuestionIndex - 1));
        nextQuestionButton.setOnClickListener(view -> showQuestion(currentQuestionIndex + 1));
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
                String deadline = ExamTiming.formatLocalDateTime(
                        SafeJson.string(data, "", "endAt", "end_at"),
                        TimeZone.getDefault());
                if (!deadline.isEmpty()) {
                    examInfo += "\n" + getString(R.string.exam_deadline_format, deadline);
                }
                infoText.setText(description.isEmpty() ? examInfo
                        : getString(R.string.exam_info_with_description, examInfo, description));
                boolean activeAttempt = SafeJson.bool(data, false,
                        "hasActiveAttempt", "has_active_attempt");
                int attemptsUsed = SafeJson.integer(data, 0,
                        "attemptsUsed", "attempts_used");
                int maxAttempts = SafeJson.integer(data, -1,
                        "maxAttempts", "max_attempts");
                int attemptsRemaining = SafeJson.integer(data, -1,
                        "attemptsRemaining", "attempts_remaining");
                String attemptsSummary = maxAttempts > 0
                        ? getString(R.string.exam_attempts_summary,
                                attemptsUsed, maxAttempts, Math.max(0, attemptsRemaining))
                        : getString(R.string.exam_attempts_unlimited, attemptsUsed);
                startButton.setText(activeAttempt ? "Tiếp tục làm bài" : "Bắt đầu làm bài");
                startButton.setEnabled(count > 0 && (activeAttempt || attemptsRemaining != 0));
                rankingButton.setVisibility(attemptsUsed > 0 ? View.VISIBLE : View.GONE);
                if (!activeAttempt && attemptsRemaining == 0) {
                    startButton.setVisibility(View.GONE);
                    showStatus(getString(R.string.exam_attempt_limit_with_count, attemptsSummary));
                } else {
                    String detailStatus = activeAttempt
                            ? getString(R.string.exam_active_attempt_status, attemptsSummary)
                            : attemptsSummary;
                    if (data.has("lastScore") && !data.isNull("lastScore")) {
                        double lastScore = SafeJson.decimal(data, 0,
                                "lastScore", "last_score");
                        double bestScore = SafeJson.decimal(data, lastScore,
                                "bestScore", "best_score");
                        detailStatus += getString(R.string.exam_previous_scores,
                                formatScore(lastScore), formatScore(bestScore));
                    }
                    showStatus(detailStatus);
                }
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleExamError(error, false);
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
                    startTimer(ExamTiming.remainingSeconds(
                            SafeJson.string(data, "", "deadlineAt", "deadline_at"),
                            System.currentTimeMillis(),
                            durationSeconds));
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleExamError(error, true);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "ExamActivity", "Không thể bắt đầu", exception);
            setLoading(false);
            showErrorDialog("Không thể bắt đầu làm bài");
        }
    }

    private void renderQuestions(JSONArray questions) {
        attemptQuestions = questions == null ? new JSONArray() : questions;
        savedAnswers = new JSONObject();
        currentQuestionIndex = -1;
        questionNavigation.setVisibility(attemptQuestions.length() > 1 ? View.VISIBLE : View.GONE);
        showQuestion(0);
    }

    private void showQuestion(int requestedIndex) {
        if (attemptQuestions.length() == 0) return;
        int index = Math.max(0, Math.min(requestedIndex, attemptQuestions.length() - 1));
        if (currentQuestionIndex >= 0) saveVisibleAnswer();
        questionsContainer.removeAllViews();
        answerViews.clear();
        currentQuestionIndex = index;
        JSONObject question = attemptQuestions.optJSONObject(index);
        if (question == null) return;
        String questionId = SafeJson.string(question, "", "id");
        if (questionId.isEmpty()) return;

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
        String questionType = normalizeQuestionType(
                SafeJson.string(question, "", "type", "question_type"),
                choices.length());
        TextView instruction = new TextView(this);
        instruction.setText(questionInstruction(questionType));
        instruction.setTextColor(getColor(R.color.smartkid_primary));
        instruction.setTextSize(12);
        instruction.setTypeface(instruction.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        instructionParams.setMargins(0, dp(6), 0, 0);
        instruction.setLayoutParams(instructionParams);
        card.addView(instruction);

        View answerView;
        if ("matching".equals(questionType)) {
            answerView = createMatchingAnswer(question);
        } else if ("mcq".equals(questionType) && choices.length() > 0) {
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
            answerView = group;
        } else {
            EditText answer = new EditText(this);
            answer.setHint("Viết câu trả lời của em");
            answer.setSingleLine(false);
            answer.setMinLines(2);
            answer.setTextColor(getColor(R.color.smartkid_text));
            answer.setHintTextColor(getColor(R.color.smartkid_text_tertiary));
            answerView = answer;
        }
        card.addView(answerView);
        answerViews.put(questionId, answerView);
        restoreAnswer(questionId, answerView);
        questionsContainer.addView(card);
        questionPosition.setText(getString(R.string.exam_question_position,
                index + 1, attemptQuestions.length()));
        previousQuestionButton.setEnabled(index > 0);
        nextQuestionButton.setEnabled(index < attemptQuestions.length() - 1);
        examScroll.post(() -> examScroll.smoothScrollTo(0, questionsContainer.getTop()));
    }

    private View createMatchingAnswer(JSONObject question) {
        JSONArray leftItems = SafeJson.array(question, "leftItems", "left_items");
        JSONArray rightItems = SafeJson.array(question, "rightItems", "right_items");
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(8), 0, 0);

        List<MatchingOption> options = new ArrayList<>();
        for (int index = 0; index < rightItems.length(); index++) {
            JSONObject item = rightItems.optJSONObject(index);
            if (item == null) continue;
            options.add(new MatchingOption(
                    SafeJson.string(item, "R" + (index + 1), "id"),
                    SafeJson.string(item, "Đáp án " + (index + 1), "text", "label")));
        }
        Collections.shuffle(options);
        MatchingAnswerState state = new MatchingAnswerState();
        for (int index = 0; index < leftItems.length(); index++) {
            JSONObject item = leftItems.optJSONObject(index);
            if (item == null) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, index == 0 ? 0 : dp(8), 0, 0);
            row.setLayoutParams(rowParams);
            row.setBackground(roundedBackground(
                    getColor(R.color.smartkid_avatar), dp(14),
                    getColor(R.color.smartkid_outline_visible)));

            TextView left = new TextView(this);
            left.setText((index + 1) + ". "
                    + SafeJson.string(item, "Vế " + (index + 1), "text", "label"));
            left.setTextColor(getColor(R.color.smartkid_text));
            left.setTextSize(15);
            left.setTypeface(left.getTypeface(), Typeface.BOLD);
            row.addView(left);

            Spinner selector = new Spinner(this);
            List<String> labels = new ArrayList<>();
            labels.add("Chọn đáp án phù hợp");
            for (MatchingOption option : options) labels.add(option.label);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            selector.setAdapter(adapter);
            selector.setPadding(0, dp(4), 0, 0);
            row.addView(selector, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
            container.addView(row);

            state.leftIds.add(SafeJson.string(item, "L" + (index + 1), "id"));
            state.selectors.add(selector);
        }
        for (MatchingOption option : options) state.rightIds.add(option.id);
        container.setTag(state);
        return container;
    }

    private void saveVisibleAnswer() {
        if (answerViews.isEmpty()) return;
        Map.Entry<String, View> entry = answerViews.entrySet().iterator().next();
        try {
            savedAnswers.put(entry.getKey(), answerFromView(entry.getValue()));
        } catch (Exception exception) {
            AppLogger.error(this, "ExamActivity", "Không thể giữ câu trả lời", exception);
        }
    }

    private JSONObject answerFromView(View view) throws Exception {
        JSONObject answer = new JSONObject();
        if (view instanceof RadioGroup) {
            int checked = ((RadioGroup) view).getCheckedRadioButtonId();
            RadioButton button = checked == -1 ? null : view.findViewById(checked);
            if (button != null && button.getTag() != null) {
                answer.put("selected_choice_id", String.valueOf(button.getTag()));
            }
        } else if (view instanceof EditText) {
            answer.put("text", ((EditText) view).getText().toString().trim());
        } else if (view != null && view.getTag() instanceof MatchingAnswerState) {
            MatchingAnswerState state = (MatchingAnswerState) view.getTag();
            JSONArray pairs = new JSONArray();
            for (int index = 0; index < state.selectors.size(); index++) {
                int selected = state.selectors.get(index).getSelectedItemPosition();
                if (selected <= 0 || selected > state.rightIds.size()) continue;
                pairs.put(new JSONObject()
                        .put("left_id", state.leftIds.get(index))
                        .put("right_id", state.rightIds.get(selected - 1)));
            }
            answer.put("pairs", pairs);
        }
        return answer;
    }

    private void restoreAnswer(String questionId, View view) {
        JSONObject answer = savedAnswers.optJSONObject(questionId);
        if (answer == null || view == null) return;
        if (view instanceof RadioGroup) {
            String selectedId = SafeJson.string(answer, "", "selected_choice_id");
            RadioGroup group = (RadioGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View child = group.getChildAt(index);
                if (child instanceof RadioButton && child.getTag() != null
                        && selectedId.equals(String.valueOf(child.getTag()))) {
                    ((RadioButton) child).setChecked(true);
                    break;
                }
            }
        } else if (view instanceof EditText) {
            ((EditText) view).setText(SafeJson.string(answer, "", "text"));
        } else if (view.getTag() instanceof MatchingAnswerState) {
            MatchingAnswerState state = (MatchingAnswerState) view.getTag();
            JSONArray pairs = SafeJson.array(answer, "pairs");
            Map<String, String> selectedByLeft = new LinkedHashMap<>();
            for (int index = 0; index < pairs.length(); index++) {
                JSONObject pair = pairs.optJSONObject(index);
                if (pair != null) selectedByLeft.put(
                        SafeJson.string(pair, "", "left_id"),
                        SafeJson.string(pair, "", "right_id"));
            }
            for (int index = 0; index < state.leftIds.size(); index++) {
                String rightId = selectedByLeft.get(state.leftIds.get(index));
                int rightIndex = rightId == null ? -1 : state.rightIds.indexOf(rightId);
                if (rightIndex >= 0) state.selectors.get(index).setSelection(rightIndex + 1);
            }
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
                    handleExamError(error, false);
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
        saveVisibleAnswer();
        return new JSONObject(savedAnswers.toString());
    }

    private int countUnanswered() {
        saveVisibleAnswer();
        int count = 0;
        for (int index = 0; index < attemptQuestions.length(); index++) {
            JSONObject question = attemptQuestions.optJSONObject(index);
            String questionId = SafeJson.string(question, "", "id");
            JSONObject answer = savedAnswers.optJSONObject(questionId);
            JSONArray choices = SafeJson.array(question, "choices", "options");
            String type = normalizeQuestionType(
                    SafeJson.string(question, "", "type", "question_type"),
                    choices.length());
            boolean answered;
            if ("matching".equals(type)) {
                int expected = SafeJson.array(question, "leftItems", "left_items").length();
                answered = answer != null && SafeJson.array(answer, "pairs").length() == expected
                        && expected > 0;
            } else if ("mcq".equals(type)) {
                answered = answer != null
                        && !SafeJson.string(answer, "", "selected_choice_id").isEmpty();
            } else {
                answered = answer != null && !SafeJson.string(answer, "", "text").isEmpty();
            }
            if (!answered) count++;
        }
        return count;
    }

    private void showResult(JSONObject result) {
        double score = SafeJson.decimal(result, 0, "totalScore", "total_score");
        double max = SafeJson.decimal(result, 0, "maxScore", "max_score");
        int correct = SafeJson.integer(result, 0, "correctCount", "correct_count");
        int total = SafeJson.integer(result, attemptQuestions.length(),
                "totalCount", "total_count");
        boolean passed = SafeJson.bool(result, false, "passed");
        questionsContainer.setVisibility(View.GONE);
        questionNavigation.setVisibility(View.GONE);
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
                showRankingSheet(data);
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleExamError(error, false);
            }
        });
    }

    private void handleExamError(ApiError error, boolean startingExam) {
        if (startingExam && ExamErrorMessages.isAttemptLimit(error)) {
            showAttemptLimitDialog();
            return;
        }
        if (error == null) {
            handleApiError(null);
            return;
        }
        handleApiError(new ApiError(
                error.getStatusCode(),
                ExamErrorMessages.studentFriendlyMessage(error),
                error.isSessionExpired()));
    }

    private void showAttemptLimitDialog() {
        startButton.setVisibility(View.GONE);
        rankingButton.setVisibility(View.VISIBLE);
        showStatus(getString(R.string.exam_attempt_limit_status));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exam_attempt_limit_title)
                .setMessage(R.string.exam_attempt_limit_message)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.exam_view_ranking,
                        (dialog, which) -> loadRanking())
                .show();
    }

    private void showRankingSheet(JSONObject data) {
        JSONArray top = SafeJson.array(data, "top");
        JSONObject me = data.optJSONObject("me");
        int participants = SafeJson.integer(data, top.length(), "participants");
        BottomSheetDialog sheet = new BottomSheetDialog(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(20));
        content.setBackground(roundedBackground(Color.WHITE, dp(28), Color.TRANSPARENT));

        TextView title = new TextView(this);
        title.setText("Bảng xếp hạng của lớp");
        title.setTextColor(getColor(R.color.smartkid_text));
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(participants + " học sinh đã hoàn thành bài kiểm tra");
        subtitle.setTextColor(getColor(R.color.smartkid_text_secondary));
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(3), 0, dp(14));
        subtitle.setLayoutParams(subtitleParams);
        content.addView(subtitle);

        if (me != null) content.addView(createMyRankingCard(me));

        TextView section = new TextView(this);
        section.setText("Thành tích nổi bật");
        section.setTextColor(getColor(R.color.smartkid_text));
        section.setTextSize(15);
        section.setTypeface(section.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(0, dp(16), 0, dp(6));
        section.setLayoutParams(sectionParams);
        content.addView(section);

        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        int visibleRows = Math.min(top.length(), 20);
        for (int index = 0; index < visibleRows; index++) {
            JSONObject row = top.optJSONObject(index);
            if (row != null) rows.addView(createRankingRow(row, index));
        }
        if (visibleRows == 0) {
            TextView empty = new TextView(this);
            empty.setText("Chưa có bạn nào hoàn thành. Em hãy là người đầu tiên nhé!");
            empty.setTextColor(getColor(R.color.smartkid_text_secondary));
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            rows.addView(empty);
        }
        scroll.addView(rows);
        int rankingHeight = visibleRows == 0
                ? dp(120) : dp(Math.min(360, visibleRows * 64 + 8));
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, rankingHeight));

        MaterialButton close = new MaterialButton(this);
        close.setText("Đóng");
        close.setTextColor(Color.WHITE);
        close.setTextSize(15);
        close.setTypeface(close.getTypeface(), Typeface.BOLD);
        close.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getColor(R.color.smartkid_primary)));
        close.setCornerRadius(dp(22));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        closeParams.setMargins(0, dp(14), 0, 0);
        close.setLayoutParams(closeParams);
        close.setOnClickListener(view -> sheet.dismiss());
        content.addView(close);

        sheet.setContentView(content);
        sheet.show();
    }

    private View createMyRankingCard(JSONObject me) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundedBackground(
                getColor(R.color.smartkid_avatar), dp(18),
                getColor(R.color.smartkid_primary_end)));

        TextView label = new TextView(this);
        int rank = SafeJson.integer(me, 0, "rank", "id");
        label.setText("Hạng của em\n#" + rank + " • Đúng "
                + SafeJson.integer(me, 0, "correct") + "/"
                + SafeJson.integer(me, 0, "total") + " câu");
        label.setTextColor(getColor(R.color.smartkid_text));
        label.setTextSize(14);
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        card.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView score = new TextView(this);
        score.setText(formatScore(SafeJson.decimal(me, 0, "score")) + "%");
        score.setTextColor(getColor(R.color.smartkid_primary));
        score.setTextSize(22);
        score.setTypeface(score.getTypeface(), Typeface.BOLD);
        card.addView(score);
        return card;
    }

    private View createRankingRow(JSONObject row, int index) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        itemParams.setMargins(0, index == 0 ? 0 : dp(5), 0, 0);
        item.setLayoutParams(itemParams);
        boolean isMe = SafeJson.bool(row, false, "isMe", "is_me");
        item.setBackground(roundedBackground(
                isMe ? getColor(R.color.smartkid_avatar) : getColor(R.color.glass_surface_soft),
                dp(16), getColor(R.color.smartkid_outline_visible)));

        int rank = SafeJson.integer(row, index + 1, "rank", "id");
        TextView badge = new TextView(this);
        badge.setText(String.valueOf(rank));
        badge.setGravity(android.view.Gravity.CENTER);
        badge.setTextColor(rank <= 3 ? Color.WHITE : getColor(R.color.smartkid_primary));
        badge.setTextSize(14);
        badge.setTypeface(badge.getTypeface(), Typeface.BOLD);
        badge.setBackground(roundedBackground(rankColor(rank), dp(20), Color.TRANSPARENT));
        item.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(SafeJson.string(row, "Học viên", "name") + (isMe ? " • Em" : ""));
        name.setTextColor(getColor(R.color.smartkid_text));
        name.setTextSize(14);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        details.addView(name);
        TextView meta = new TextView(this);
        meta.setText("Đúng " + SafeJson.integer(row, 0, "correct") + "/"
                + SafeJson.integer(row, 0, "total") + " • "
                + SafeJson.string(row, "00:00", "time"));
        meta.setTextColor(getColor(R.color.smartkid_text_secondary));
        meta.setTextSize(12);
        details.addView(meta);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        detailsParams.setMargins(dp(10), 0, dp(8), 0);
        item.addView(details, detailsParams);

        TextView score = new TextView(this);
        score.setText(formatScore(SafeJson.decimal(row, 0, "score")) + "%");
        score.setTextColor(getColor(R.color.smartkid_primary));
        score.setTextSize(16);
        score.setTypeface(score.getTypeface(), Typeface.BOLD);
        item.addView(score);
        return item;
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

    private static String normalizeQuestionType(String raw, int choiceCount) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("matching".equals(value) || "match".equals(value)) return "matching";
        if ("short_answer".equals(value) || "short".equals(value)
                || "text".equals(value)) return "short_answer";
        if ("mcq".equals(value) || "single".equals(value)
                || "multiple_choice".equals(value)) return "mcq";
        return choiceCount > 0 ? "mcq" : "short_answer";
    }

    private String questionInstruction(String type) {
        if ("matching".equals(type)) return "Nối từng ý với đáp án đúng";
        if ("short_answer".equals(type)) return "Viết câu trả lời ngắn";
        return "Chọn một đáp án đúng";
    }

    private GradientDrawable roundedBackground(int color, int radius, int strokeColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) background.setStroke(dp(1), strokeColor);
        return background;
    }

    private int rankColor(int rank) {
        if (rank == 1) return Color.parseColor("#F6A609");
        if (rank == 2) return Color.parseColor("#8A94A6");
        if (rank == 3) return Color.parseColor("#C97842");
        return getColor(R.color.smartkid_avatar);
    }

    private String formatScore(double score) {
        return new DecimalFormat("0.##").format(score);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }

    private static final class MatchingOption {
        private final String id;
        private final String label;

        private MatchingOption(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static final class MatchingAnswerState {
        private final List<String> leftIds = new ArrayList<>();
        private final List<String> rightIds = new ArrayList<>();
        private final List<Spinner> selectors = new ArrayList<>();
    }
}
