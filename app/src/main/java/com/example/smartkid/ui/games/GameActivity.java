package com.example.smartkid.ui.games;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.R;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.core.SafeJson;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.GameRepository;
import com.example.smartkid.ui.common.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Chơi quiz, đố vui và ghép từ từ dữ liệu thật do giáo viên tạo. */
public class GameActivity extends BaseActivity {
    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_GAME_TITLE = "game_title";

    private String gameId;
    private String sessionId;
    private String gameType = "quiz";
    private long startedAt;
    private JSONArray questions = new JSONArray();
    private final List<View> answerViews = new ArrayList<>();
    private MaterialToolbar toolbar;
    private ProgressBar progressBar;
    private TextView descriptionText;
    private TextView statusText;
    private LinearLayout gameContainer;
    private Button startButton;
    private Button finishButton;
    private Button leaderboardButton;
    private GameRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_game);
            gameId = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_GAME_ID);
            if (gameId == null || gameId.trim().isEmpty()) {
                showErrorDialog("Không tìm thấy mã trò chơi");
                finish();
                return;
            }
            repository = new GameRepository(this);
            bindViews();
            toolbar.setNavigationOnClickListener(view -> finish());
            String title = getIntent().getStringExtra(EXTRA_GAME_TITLE);
            toolbar.setTitle(title == null ? getString(R.string.games) : title);
            startButton.setOnClickListener(view -> startSafely());
            finishButton.setOnClickListener(view -> confirmFinish());
            leaderboardButton.setOnClickListener(view -> loadLeaderboard());
            loadDetail();
        } catch (Exception exception) {
            AppLogger.error(this, "GameActivity", "Không thể tạo trò chơi", exception);
            showErrorDialog("Không thể mở trò chơi");
        }
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarGame);
        progressBar = findViewById(R.id.progressGame);
        descriptionText = findViewById(R.id.textGameDescription);
        statusText = findViewById(R.id.textGameStatus);
        gameContainer = findViewById(R.id.containerGame);
        startButton = findViewById(R.id.buttonStartGame);
        finishButton = findViewById(R.id.buttonFinishGame);
        leaderboardButton = findViewById(R.id.buttonGameLeaderboard);
        if (toolbar == null || progressBar == null || descriptionText == null
                || statusText == null || gameContainer == null || startButton == null
                || finishButton == null || leaderboardButton == null) {
            throw new IllegalStateException("Giao diện trò chơi thiếu thành phần bắt buộc");
        }
    }

    private void loadDetail() {
        setLoading(true);
        repository.loadDetail(gameId, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                setLoading(false);
                toolbar.setTitle(SafeJson.string(data, "Trò chơi", "title"));
                gameType = SafeJson.string(data, "quiz", "game_type");
                int count = SafeJson.integer(data, 0, "question_count");
                String difficulty = SafeJson.string(data, "", "difficulty");
                String difficultySuffix = difficulty.isEmpty() ? ""
                        : getString(R.string.difficulty_suffix, difficulty);
                String questionCount = getResources().getQuantityString(
                        R.plurals.question_count_short, count, count);
                descriptionText.setText(getString(R.string.game_info_format,
                        SafeJson.string(data, "", "description"), questionCount,
                        difficultySuffix));
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
        setLoading(true);
        repository.start(gameId, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                setLoading(false);
                sessionId = SafeJson.string(data, "", "session_id");
                questions = SafeJson.array(data, "questions");
                if (sessionId.isEmpty() || questions.length() == 0) {
                    showStatus("Server không trả về phiên chơi hoặc câu hỏi");
                    return;
                }
                startedAt = SystemClock.elapsedRealtime();
                renderGame();
                startButton.setVisibility(View.GONE);
                finishButton.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void renderGame() {
        gameContainer.removeAllViews();
        answerViews.clear();
        if ("word_match".equals(gameType)) renderWordMatch();
        else renderQuiz();
    }

    private void renderQuiz() {
        for (int index = 0; index < questions.length(); index++) {
            JSONObject question = questions.optJSONObject(index);
            if (question == null) continue;
            LinearLayout card = createCard(index,
                    SafeJson.string(question, "Câu hỏi", "question", "text", "prompt"));
            RadioGroup group = new RadioGroup(this);
            group.setOrientation(RadioGroup.VERTICAL);
            JSONArray options = SafeJson.array(question, "options", "choices");
            for (int optionIndex = 0; optionIndex < options.length(); optionIndex++) {
                Object raw = options.opt(optionIndex);
                String label;
                if (raw instanceof JSONObject) label = SafeJson.string((JSONObject) raw, "Đáp án", "text");
                else label = String.valueOf(raw);
                RadioButton button = new RadioButton(this);
                button.setId(View.generateViewId());
                button.setTag(optionIndex);
                button.setText(label);
                button.setTextColor(getColor(R.color.smartkid_text));
                button.setPadding(0, dp(4), 0, dp(4));
                group.addView(button);
            }
            card.addView(group);
            gameContainer.addView(card);
            answerViews.add(group);
        }
    }

    private void renderWordMatch() {
        List<String> rightValues = new ArrayList<>();
        for (int index = 0; index < questions.length(); index++) {
            JSONObject pair = questions.optJSONObject(index);
            if (pair != null) rightValues.add(SafeJson.string(pair, "", "right"));
        }
        for (int index = 0; index < questions.length(); index++) {
            JSONObject pair = questions.optJSONObject(index);
            if (pair == null) continue;
            LinearLayout card = createCard(index,
                    SafeJson.string(pair, "Từ", "left"));
            Spinner spinner = new Spinner(this);
            List<String> choices = new ArrayList<>();
            choices.add("Chọn từ tương ứng");
            choices.addAll(rightValues);
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, choices);
            spinner.setAdapter(spinnerAdapter);
            card.addView(spinner);
            gameContainer.addView(card);
            answerViews.add(spinner);
        }
    }

    private LinearLayout createCard(int index, String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundResource(R.drawable.bg_feature_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(10));
        card.setLayoutParams(params);
        TextView prompt = new TextView(this);
        prompt.setText(getString(R.string.numbered_question, index + 1, title));
        prompt.setTextColor(getColor(R.color.smartkid_text));
        prompt.setTextSize(16);
        prompt.setTypeface(prompt.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(prompt);
        return card;
    }

    private void confirmFinish() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.finish_game)
                    .setMessage("Hoàn thành và gửi điểm lên server?")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.finish_game, (dialog, which) -> finishSafely())
                    .show();
        } catch (Exception exception) {
            AppLogger.error(this, "GameActivity", "Không thể xác nhận", exception);
            showErrorDialog("Không thể chuẩn bị gửi kết quả");
        }
    }

    private void finishSafely() {
        try {
            int score = 0;
            JSONArray answers = new JSONArray();
            for (int index = 0; index < questions.length() && index < answerViews.size(); index++) {
                JSONObject question = questions.optJSONObject(index);
                if (question == null) continue;
                JSONObject answer = new JSONObject();
                answer.put("question_id", SafeJson.string(question, String.valueOf(index), "id"));
                View view = answerViews.get(index);
                boolean correct = false;
                if (view instanceof RadioGroup) {
                    int checkedId = ((RadioGroup) view).getCheckedRadioButtonId();
                    RadioButton button = checkedId == -1 ? null : view.findViewById(checkedId);
                    int selected = button == null || !(button.getTag() instanceof Integer)
                            ? -1 : (Integer) button.getTag();
                    int expected = SafeJson.integer(question, -2, "correct");
                    correct = selected >= 0 && selected == expected;
                    answer.put("selected", selected);
                } else if (view instanceof Spinner) {
                    String selected = ((Spinner) view).getSelectedItemPosition() <= 0 ? ""
                            : String.valueOf(((Spinner) view).getSelectedItem());
                    String expected = SafeJson.string(question, "", "right");
                    correct = !selected.isEmpty() && selected.equals(expected);
                    answer.put("selected", selected);
                }
                answer.put("is_correct", correct);
                if (correct) score += 10;
                answers.put(answer);
            }
            int elapsed = (int) Math.max(0, (SystemClock.elapsedRealtime() - startedAt) / 1000L);
            setLoading(true);
            final int finalScore = score;
            repository.submit(gameId, sessionId, score, elapsed, answers,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject result) {
                            if (!isUsable()) return;
                            setLoading(false);
                            showResult(result, finalScore);
                        }

                        @Override
                        public void onError(ApiError error) {
                            if (!isUsable()) return;
                            setLoading(false);
                            handleApiError(error);
                        }
                    });
        } catch (Exception exception) {
            AppLogger.error(this, "GameActivity", "Không thể hoàn tất", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị kết quả trò chơi");
        }
    }

    private void showResult(JSONObject result, int fallbackScore) {
        int score = SafeJson.integer(result, fallbackScore, "score");
        int max = SafeJson.integer(result, questions.length() * 10, "max_score");
        int percent = SafeJson.integer(result, max > 0 ? score * 100 / max : 0, "percentage");
        int rank = SafeJson.integer(result, 0, "rank");
        gameContainer.setVisibility(View.GONE);
        finishButton.setVisibility(View.GONE);
        leaderboardButton.setVisibility(View.VISIBLE);
        showStatus("Điểm: " + score + "/" + max + " • " + percent + "%"
                + (rank > 0 ? " • Hạng " + rank : ""));
    }

    private void loadLeaderboard() {
        setLoading(true);
        repository.leaderboard(gameId, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                setLoading(false);
                JSONArray rows = SafeJson.array(data, "leaderboard");
                StringBuilder text = new StringBuilder();
                for (int index = 0; index < rows.length(); index++) {
                    JSONObject row = rows.optJSONObject(index);
                    if (row == null) continue;
                    text.append(SafeJson.integer(row, index + 1, "rank")).append(". ")
                            .append(SafeJson.string(row, "Học viên", "player_name"))
                            .append(" — ").append(SafeJson.integer(row, 0, "score")).append('\n');
                }
                if (text.length() == 0) text.append("Chưa có dữ liệu xếp hạng");
                new AlertDialog.Builder(GameActivity.this).setTitle(R.string.ranking)
                        .setMessage(text.toString().trim()).setPositiveButton("Đã hiểu", null).show();
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
        startButton.setEnabled(!loading);
        finishButton.setEnabled(!loading);
        leaderboardButton.setEnabled(!loading);
    }

    private void showStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
