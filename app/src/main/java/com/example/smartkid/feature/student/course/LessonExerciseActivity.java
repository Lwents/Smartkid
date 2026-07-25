package com.example.smartkid.feature.student.course;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.R;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.CourseRepository;
import com.example.smartkid.data.repository.LessonExerciseRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Trình làm bài tập trong bài học, dùng trực tiếp activities attempt API. */
public final class LessonExerciseActivity extends BaseActivity {
    public static final String EXTRA_EXERCISE_ID = "lesson_exercise_id";
    public static final String EXTRA_EXERCISE_TITLE = "lesson_exercise_title";
    public static final String EXTRA_LESSON_ID = "lesson_exercise_lesson_id";

    private final Map<String, View> answerViews = new LinkedHashMap<>();
    private final Map<String, MatchingAnswer> matchingAnswers = new LinkedHashMap<>();
    private LessonExerciseRepository exerciseRepository;
    private CourseRepository courseRepository;
    private MaterialToolbar toolbar;
    private ProgressBar progress;
    private TextView info;
    private TextView timerText;
    private TextView status;
    private LinearLayout questionsContainer;
    private MaterialButton startButton;
    private MaterialButton submitButton;
    private String exerciseId;
    private String lessonId;
    private String attemptId;
    private int durationSeconds = 1800;
    private CountDownTimer timer;
    private boolean submitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.exam_activity_exam);
            exerciseId = value(EXTRA_EXERCISE_ID);
            lessonId = value(EXTRA_LESSON_ID);
            if (exerciseId.isEmpty() || lessonId.isEmpty()) {
                showErrorDialog(getString(R.string.lesson_exercise_invalid));
                finish();
                return;
            }
            exerciseRepository = new LessonExerciseRepository(this);
            courseRepository = new CourseRepository(this);
            bindViews();
            toolbar.setTitle(value(EXTRA_EXERCISE_TITLE));
            toolbar.setNavigationOnClickListener(view -> finish());
            startButton.setText(R.string.lesson_exercise_start);
            startButton.setOnClickListener(view -> startExercise());
            submitButton.setOnClickListener(view -> confirmSubmit());
            findViewById(R.id.buttonExamRanking).setVisibility(View.GONE);
            loadDetail();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonExerciseActivity",
                    "Không thể mở bài luyện tập", exception);
            showErrorDialog(getString(R.string.lesson_exercise_open_error));
        }
    }

    @Override
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        if (courseRepository != null) courseRepository.close();
        super.onDestroy();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarExam);
        progress = findViewById(R.id.progressExam);
        info = findViewById(R.id.textExamInfo);
        timerText = findViewById(R.id.textExamTimer);
        status = findViewById(R.id.textExamStatus);
        questionsContainer = findViewById(R.id.containerExamQuestions);
        startButton = findViewById(R.id.buttonStartExam);
        submitButton = findViewById(R.id.buttonSubmitExam);
    }

    private void loadDetail() {
        setLoading(true);
        exerciseRepository.loadDetail(exerciseId, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                setLoading(false);
                toolbar.setTitle(SafeJson.string(data, "Bài luyện tập", "title"));
                JSONObject settings = data.optJSONObject("settings");
                durationSeconds = Math.max(60, SafeJson.integer(settings, 1800,
                        "duration_seconds", "time_limit_seconds"));
                int count = SafeJson.array(data, "questions").length();
                info.setText(getString(R.string.lesson_exercise_info_format, count,
                        Math.max(1, durationSeconds / 60)));
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

    private void startExercise() {
        setLoading(true);
        exerciseRepository.start(exerciseId, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                setLoading(false);
                attemptId = SafeJson.string(data, "", "id", "attempt_id");
                JSONArray questions = SafeJson.array(data, "questions");
                if (attemptId.isEmpty() || questions.length() == 0) {
                    showStatus(getString(R.string.lesson_exercise_empty));
                    return;
                }
                renderQuestions(questions);
                startButton.setVisibility(View.GONE);
                submitButton.setVisibility(View.VISIBLE);
                timerText.setVisibility(View.VISIBLE);
                startTimer();
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void renderQuestions(JSONArray questions) {
        questionsContainer.removeAllViews();
        answerViews.clear();
        matchingAnswers.clear();
        for (int index = 0; index < questions.length(); index++) {
            JSONObject question = questions.optJSONObject(index);
            if (question == null) continue;
            String questionId = SafeJson.string(question, "", "id");
            if (questionId.isEmpty()) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, dp(6), 0, dp(10));
            card.setLayoutParams(params);
            card.setBackgroundResource(R.drawable.common_bg_feature_card);

            TextView prompt = new TextView(this);
            prompt.setText(getString(R.string.numbered_question, index + 1,
                    SafeJson.string(question, "Câu hỏi", "prompt", "text")));
            prompt.setTextColor(getColor(R.color.smartkid_text));
            prompt.setTextSize(16f);
            prompt.setTypeface(prompt.getTypeface(), android.graphics.Typeface.BOLD);
            card.addView(prompt);

            String questionType = questionType(question);
            JSONArray choices = SafeJson.array(question, "choices", "options");
            if ("matching".equals(questionType)) {
                LinearLayout matchingView = renderMatchingQuestion(question);
                card.addView(matchingView);
                answerViews.put(questionId, matchingView);
            } else if ("short_answer".equals(questionType)) {
                EditText answer = createShortAnswerInput();
                card.addView(answer);
                answerViews.put(questionId, answer);
            } else if (choices.length() > 0) {
                RadioGroup group = new RadioGroup(this);
                group.setOrientation(RadioGroup.VERTICAL);
                group.setPadding(0, dp(8), 0, 0);
                for (int choiceIndex = 0; choiceIndex < choices.length(); choiceIndex++) {
                    JSONObject choice = choices.optJSONObject(choiceIndex);
                    if (choice == null) continue;
                    RadioButton button = new RadioButton(this);
                    button.setId(View.generateViewId());
                    button.setTag(SafeJson.string(choice, String.valueOf(choiceIndex), "id"));
                    button.setText(SafeJson.string(choice, "Đáp án", "text", "label"));
                    button.setTextColor(getColor(R.color.smartkid_text));
                    button.setPadding(0, dp(4), 0, dp(4));
                    group.addView(button);
                }
                card.addView(group);
                answerViews.put(questionId, group);
            } else {
                EditText answer = createShortAnswerInput();
                card.addView(answer);
                answerViews.put(questionId, answer);
            }
            questionsContainer.addView(card);
        }
    }

    private EditText createShortAnswerInput() {
        EditText answer = new EditText(this);
        answer.setHint(R.string.lesson_exercise_answer_hint);
        answer.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        answer.setMinLines(2);
        return answer;
    }

    private LinearLayout renderMatchingQuestion(JSONObject question) {
        MatchingDefinition definition = matchingDefinition(question);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(8), 0, 0);

        TextView instruction = new TextView(this);
        instruction.setText("Ghép mỗi mục bên trái với đáp án phù hợp.");
        instruction.setTextColor(getColor(R.color.smartkid_text_secondary));
        instruction.setTextSize(14f);
        container.addView(instruction);

        List<MatchingRow> rows = new ArrayList<>();
        if (!definition.scorable || definition.leftItems.isEmpty()
                || definition.rightItems.isEmpty()) {
            TextView unavailable = new TextView(this);
            unavailable.setText("Câu nối cặp chưa có đủ dữ liệu để hiển thị.");
            unavailable.setTextColor(getColor(R.color.smartkid_error));
            unavailable.setPadding(0, dp(8), 0, 0);
            container.addView(unavailable);
        } else {
            List<String> rightLabels = new ArrayList<>();
            rightLabels.add("Chọn đáp án");
            for (MatchingItem item : definition.rightItems) {
                rightLabels.add(item.text);
            }

            for (MatchingItem leftItem : definition.leftItems) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, dp(8), 0, dp(4));

                TextView left = new TextView(this);
                left.setText(leftItem.text);
                left.setTextColor(getColor(R.color.smartkid_text));
                left.setTextSize(15f);
                left.setTypeface(left.getTypeface(), android.graphics.Typeface.BOLD);
                row.addView(left);

                Spinner rightSelector = new Spinner(this);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, rightLabels);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                rightSelector.setAdapter(adapter);
                rightSelector.setPrompt("Chọn đáp án phù hợp");
                LinearLayout.LayoutParams selectorParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                selectorParams.setMargins(0, dp(4), 0, 0);
                rightSelector.setLayoutParams(selectorParams);
                row.addView(rightSelector);

                rows.add(new MatchingRow(leftItem.id, rightSelector,
                        definition.rightItems));
                container.addView(row);
            }
        }

        matchingAnswers.put(SafeJson.string(question, "", "id"),
                new MatchingAnswer(rows));
        return container;
    }

    private static String questionType(JSONObject question) {
        JSONObject meta = question == null ? null : question.optJSONObject("meta");
        String raw = SafeJson.string(meta,
                SafeJson.string(question, "", "type", "question_type", "format"),
                "type", "question_type", "format");
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        if ("single".equals(normalized) || "multi".equals(normalized)) return "mcq";
        if ("shortanswer".equals(normalized) || "fill".equals(normalized)) {
            return "short_answer";
        }
        if ("match".equals(normalized)) return "matching";
        if (normalized.isEmpty() && meta != null
                && (meta.optJSONArray("pairs") != null
                || meta.optJSONObject("correct_pairs") != null)) {
            return "matching";
        }
        return normalized;
    }

    private static MatchingDefinition matchingDefinition(JSONObject question) {
        JSONObject meta = question == null ? null : question.optJSONObject("meta");
        if (meta == null) meta = new JSONObject();
        JSONArray pairs = meta.optJSONArray("pairs");
        if (pairs == null && question != null) pairs = question.optJSONArray("pairs");
        if (pairs == null) pairs = new JSONArray();
        JSONObject correctPairs = meta.optJSONObject("correct_pairs");
        if (correctPairs == null && question != null) {
            correctPairs = question.optJSONObject("correct_pairs");
        }
        if (correctPairs == null) correctPairs = new JSONObject();

        JSONArray choices = SafeJson.array(question, "choices", "options");
        Map<String, String> choiceTextById = new LinkedHashMap<>();
        for (int index = 0; index < choices.length(); index++) {
            JSONObject choice = choices.optJSONObject(index);
            if (choice == null) continue;
            String id = SafeJson.string(choice, "", "id");
            if (!id.isEmpty()) {
                choiceTextById.put(id, SafeJson.string(choice, id, "text", "label"));
            }
        }

        List<String> correctLeftIds = new ArrayList<>();
        List<String> correctRightIds = new ArrayList<>();
        Iterator<String> keys = correctPairs.keys();
        while (keys.hasNext()) {
            String leftId = keys.next();
            String rightId = correctPairs.optString(leftId, "").trim();
            if (leftId == null || leftId.trim().isEmpty() || rightId.isEmpty()) continue;
            correctLeftIds.add(leftId.trim());
            correctRightIds.add(rightId);
        }

        int pairCount = Math.max(pairs.length(), correctLeftIds.size());
        if (pairCount == 0) pairCount = choices.length() / 2;
        List<MatchingItem> leftItems = new ArrayList<>();
        Map<String, MatchingItem> rightItemsById = new LinkedHashMap<>();
        for (int index = 0; index < pairCount; index++) {
            JSONObject pair = pairs.optJSONObject(index);
            if (pair == null) pair = new JSONObject();

            String canonicalLeftId = "L" + (index + 1);
            String explicitLeftId = pairSideId(pair, "left");
            String leftId;
            if (!explicitLeftId.isEmpty() && correctPairs.has(explicitLeftId)) {
                leftId = explicitLeftId;
            } else if (correctPairs.has(canonicalLeftId)) {
                leftId = canonicalLeftId;
            } else if (index < correctLeftIds.size()) {
                leftId = correctLeftIds.get(index);
            } else {
                leftId = canonicalLeftId;
            }

            String rightId = correctPairs.optString(leftId, "").trim();
            if (rightId.isEmpty()) rightId = pairSideId(pair, "right");
            if (rightId.isEmpty() && index < correctRightIds.size()) {
                rightId = correctRightIds.get(index);
            }
            if (rightId.isEmpty()) rightId = "R" + (index + 1);

            String leftText = pairSideText(pair, "left");
            String rightText = pairSideText(pair, "right");
            if (leftText.isEmpty()) leftText = choiceTextById.get(leftId);
            if (rightText.isEmpty()) rightText = choiceTextById.get(rightId);
            if (leftText == null || leftText.trim().isEmpty()) {
                leftText = choiceTextAt(choices, index * 2, leftId);
            }
            if (rightText == null || rightText.trim().isEmpty()) {
                rightText = choiceTextAt(choices, index * 2 + 1, rightId);
            }

            leftItems.add(new MatchingItem(leftId, leftText));
            if (!rightItemsById.containsKey(rightId)) {
                rightItemsById.put(rightId, new MatchingItem(rightId, rightText));
            }
        }
        return new MatchingDefinition(leftItems,
                new ArrayList<>(rightItemsById.values()), !correctLeftIds.isEmpty());
    }

    private static String pairSideId(JSONObject pair, String side) {
        String direct = SafeJson.string(pair, "", side + "_id", side + "Id");
        if (!direct.isEmpty()) return direct;
        JSONObject nested = pair == null ? null : pair.optJSONObject(side);
        return SafeJson.string(nested, "", "id", "value");
    }

    private static String pairSideText(JSONObject pair, String side) {
        String direct = SafeJson.string(pair, "", side + "_text", side + "Text");
        if (!direct.isEmpty()) return direct;
        if (pair == null) return "";
        Object raw = pair.opt(side);
        if (raw instanceof JSONObject) {
            return SafeJson.string((JSONObject) raw, "", "text", "label", "name");
        }
        if (raw == null || raw == JSONObject.NULL) return "";
        return String.valueOf(raw).trim();
    }

    private static String choiceTextAt(JSONArray choices, int index, String fallback) {
        JSONObject choice = choices == null ? null : choices.optJSONObject(index);
        return SafeJson.string(choice, fallback, "text", "label");
    }

    private void confirmSubmit() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.submit_exam)
                .setMessage(R.string.lesson_exercise_submit_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.submit_exam, (dialog, which) -> submitAnswers())
                .show();
    }

    private void submitAnswers() {
        if (submitting || attemptId == null || attemptId.isEmpty()) return;
        submitting = true;
        setLoading(true);
        List<AnswerSubmission> answers = collectAnswers();
        submitAnswerAt(answers, 0);
    }

    private List<AnswerSubmission> collectAnswers() {
        List<AnswerSubmission> result = new ArrayList<>();
        for (Map.Entry<String, View> entry : answerViews.entrySet()) {
            JSONObject answer = new JSONObject();
            try {
                MatchingAnswer matchingAnswer = matchingAnswers.get(entry.getKey());
                if (matchingAnswer != null) {
                    answer = matchingAnswer.toPayload();
                } else if (entry.getValue() instanceof RadioGroup) {
                    RadioGroup group = (RadioGroup) entry.getValue();
                    int checked = group.getCheckedRadioButtonId();
                    RadioButton button = checked == -1 ? null : group.findViewById(checked);
                    if (button != null && button.getTag() != null) {
                        answer.put("selected_choice_id", String.valueOf(button.getTag()));
                    }
                } else if (entry.getValue() instanceof EditText) {
                    answer.put("text", ((EditText) entry.getValue()).getText().toString().trim());
                }
            } catch (Exception exception) {
                AppLogger.error(this, "LessonExerciseActivity",
                        "Không thể gom câu trả lời", exception);
            }
            result.add(new AnswerSubmission(entry.getKey(), answer));
        }
        return result;
    }

    private void submitAnswerAt(List<AnswerSubmission> answers, int index) {
        if (index >= answers.size()) {
            finalizeAttempt();
            return;
        }
        AnswerSubmission submission = answers.get(index);
        exerciseRepository.submitAnswer(attemptId, submission.questionId, submission.answer,
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        submitAnswerAt(answers, index + 1);
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        submitting = false;
                        setLoading(false);
                        handleApiError(error);
                    }
                });
    }

    private void finalizeAttempt() {
        exerciseRepository.finalizeAttempt(attemptId, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                double score = SafeJson.decimal(data, 0, "score", "total_score", "totalScore");
                courseRepository.markExerciseCompleted(lessonId, score,
                        new ApiCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean completed) {
                                if (!isUsable()) return;
                                showResult(data, true);
                            }

                            @Override
                            public void onError(ApiError error) {
                                if (!isUsable()) return;
                                showResult(data, false);
                            }
                        });
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                submitting = false;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void showResult(JSONObject data, boolean progressSaved) {
        submitting = false;
        setLoading(false);
        if (timer != null) timer.cancel();
        double score = SafeJson.decimal(data, 0, "score", "total_score", "totalScore");
        questionsContainer.setVisibility(View.GONE);
        submitButton.setVisibility(View.GONE);
        timerText.setVisibility(View.GONE);
        showStatus(getString(progressSaved ? R.string.lesson_exercise_result_saved
                : R.string.lesson_exercise_result_not_saved, score));
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(durationSeconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000L;
                timerText.setText(getString(R.string.exam_timer_format,
                        seconds / 60, seconds % 60));
            }

            @Override
            public void onFinish() {
                timerText.setText(R.string.time_up);
                submitAnswers();
            }
        }.start();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        startButton.setEnabled(!loading);
        submitButton.setEnabled(!loading);
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    private String value(String key) {
        String value = getIntent() == null ? null : getIntent().getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }

    private static final class AnswerSubmission {
        private final String questionId;
        private final JSONObject answer;

        private AnswerSubmission(String questionId, JSONObject answer) {
            this.questionId = questionId;
            this.answer = answer;
        }
    }

    private static final class MatchingDefinition {
        private final List<MatchingItem> leftItems;
        private final List<MatchingItem> rightItems;
        private final boolean scorable;

        private MatchingDefinition(List<MatchingItem> leftItems,
                                   List<MatchingItem> rightItems,
                                   boolean scorable) {
            this.leftItems = leftItems;
            this.rightItems = rightItems;
            this.scorable = scorable;
        }
    }

    private static final class MatchingItem {
        private final String id;
        private final String text;

        private MatchingItem(String id, String text) {
            this.id = id == null ? "" : id;
            this.text = text == null ? this.id : text;
        }
    }

    private static final class MatchingRow {
        private final String leftId;
        private final Spinner rightSelector;
        private final List<MatchingItem> rightItems;

        private MatchingRow(String leftId, Spinner rightSelector,
                            List<MatchingItem> rightItems) {
            this.leftId = leftId;
            this.rightSelector = rightSelector;
            this.rightItems = rightItems;
        }
    }

    private static final class MatchingAnswer {
        private final List<MatchingRow> rows;

        private MatchingAnswer(List<MatchingRow> rows) {
            this.rows = rows;
        }

        private JSONObject toPayload() throws Exception {
            JSONArray selectedPairs = new JSONArray();
            for (MatchingRow row : rows) {
                int selectedPosition = row.rightSelector.getSelectedItemPosition();
                if (selectedPosition <= 0 || selectedPosition > row.rightItems.size()) continue;
                MatchingItem right = row.rightItems.get(selectedPosition - 1);
                JSONObject pair = new JSONObject();
                pair.put("left_id", row.leftId);
                pair.put("right_id", right.id);
                selectedPairs.put(pair);
            }
            JSONObject answer = new JSONObject();
            answer.put("pairs", selectedPairs);
            return answer;
        }
    }
}
