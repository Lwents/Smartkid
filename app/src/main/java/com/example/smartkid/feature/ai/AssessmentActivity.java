package com.example.smartkid.feature.ai;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Bài đánh giá đầu vào động lấy toàn bộ câu hỏi từ server. */
public class AssessmentActivity extends BaseActivity {
    private StudentFeatureRepository repository;
    private LinearLayout questionContainer;
    private ProgressBar progressBar;
    private TextView statusText;
    private View submitButton;
    private String courseId;
    private JSONArray questions = new JSONArray();
    private final List<RadioGroup> answerGroups = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.ai_activity_assessment);
            courseId = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(AppConstants.EXTRA_COURSE_ID));
            if (courseId.isEmpty()) {
                showErrorDialog("Không tìm thấy khóa học cần đánh giá");
                finish();
                return;
            }
            repository = new StudentFeatureRepository(this);
            bindViews();
            MaterialToolbar toolbar = findViewById(R.id.toolbarAssessment);
            if (toolbar == null) throw new IllegalStateException("Thiếu thanh điều hướng đánh giá");
            toolbar.setNavigationOnClickListener(view -> finish());
            String courseTitle = getIntent().getStringExtra(AppConstants.EXTRA_COURSE_TITLE);
            if (courseTitle != null && !courseTitle.trim().isEmpty()) {
                toolbar.setSubtitle(courseTitle.trim());
            }
            submitButton.setOnClickListener(view -> submitSafely());
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "AssessmentActivity", "Không thể tạo đánh giá", exception);
            showErrorDialog("Không thể mở đánh giá đầu vào");
        }
    }

    private void bindViews() {
        questionContainer = findViewById(R.id.containerAssessmentQuestions);
        progressBar = findViewById(R.id.progressAssessment);
        statusText = findViewById(R.id.textAssessmentStatus);
        submitButton = findViewById(R.id.buttonSubmitAssessment);
        if (questionContainer == null || progressBar == null || statusText == null
                || submitButton == null) {
            throw new IllegalStateException("Giao diện đánh giá thiếu thành phần bắt buộc");
        }
    }

    private void loadSafely() {
        setLoading(true);
        repository.startAssessment(courseId, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                try {
                    questions = SafeJson.array(data, "questions");
                    renderQuestions();
                    setLoading(false);
                    showStatus(questions.length() == 0
                            ? "Khóa học chưa có nội dung để tạo đánh giá"
                            : "Có " + questions.length() + " câu hỏi từ server");
                } catch (Exception exception) {
                    AppLogger.error(AssessmentActivity.this, "AssessmentActivity",
                            "Không thể đọc câu hỏi đánh giá", exception);
                    setLoading(false);
                    showStatus("Dữ liệu đánh giá từ server không hợp lệ");
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

    private void renderQuestions() {
        questionContainer.removeAllViews();
        answerGroups.clear();
        for (int index = 0; index < questions.length(); index++) {
            JSONObject question = questions.optJSONObject(index);
            if (question == null) continue;
            TextView title = new TextView(this);
            title.setText(getString(R.string.numbered_question, index + 1,
                    SafeJson.string(question, "Câu hỏi", "text")));
            title.setTextSize(16);
            title.setTextColor(getColor(R.color.smartkid_text));
            title.setPadding(0, index == 0 ? 4 : 24, 0, 8);
            questionContainer.addView(title);

            RadioGroup group = new RadioGroup(this);
            group.setOrientation(RadioGroup.VERTICAL);
            JSONArray choices = SafeJson.array(question, "choices");
            for (int choiceIndex = 0; choiceIndex < choices.length(); choiceIndex++) {
                RadioButton button = new RadioButton(this);
                button.setId(View.generateViewId());
                button.setTag(choiceIndex);
                button.setText(choices.optString(choiceIndex, "Lựa chọn " + (choiceIndex + 1)));
                button.setTextColor(getColor(R.color.smartkid_text));
                button.setPadding(4, 6, 4, 6);
                group.addView(button);
            }
            answerGroups.add(group);
            questionContainer.addView(group);
        }
        submitButton.setEnabled(!answerGroups.isEmpty());
    }

    private void submitSafely() {
        try {
            JSONArray answers = new JSONArray();
            for (int index = 0; index < answerGroups.size(); index++) {
                RadioGroup group = answerGroups.get(index);
                int checkedId = group.getCheckedRadioButtonId();
                if (checkedId == View.NO_ID) {
                    showStatus("Bạn chưa trả lời câu " + (index + 1));
                    return;
                }
                RadioButton checked = group.findViewById(checkedId);
                Object tag = checked == null ? null : checked.getTag();
                int selected = tag instanceof Number ? ((Number) tag).intValue() : 0;
                JSONObject question = questions.optJSONObject(index);
                JSONObject answer = new JSONObject();
                answer.put("question_id", SafeJson.string(question,
                        String.valueOf(index + 1), "id"));
                answer.put("choice", selected);
                if (question != null && !question.isNull("correct_index")) {
                    answer.put("correct_index", question.optInt("correct_index", -1));
                } else {
                    answer.put("correct_index", JSONObject.NULL);
                }
                answers.put(answer);
            }
            setLoading(true);
            repository.submitAssessment(courseId, answers, new ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject data) {
                    if (!isUsable()) return;
                    setLoading(false);
                    showResult(data == null ? new JSONObject() : data);
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "AssessmentActivity", "Không thể nộp đánh giá", exception);
            setLoading(false);
            showStatus("Không thể chuẩn bị kết quả đánh giá");
        }
    }

    private void showResult(JSONObject data) {
        String message = "Trình độ: " + SafeJson.string(data, "Đang cập nhật", "level_text")
                + "\nĐiểm: " + SafeJson.decimal(data, 0, "score")
                + "/" + SafeJson.decimal(data, 10, "max_score")
                + "\n\n" + SafeJson.string(data, "", "recommendation");
        new AlertDialog.Builder(this).setTitle("Kết quả đánh giá")
                .setMessage(message).setCancelable(false)
                .setPositiveButton("Hoàn tất", (dialog, which) -> finish()).show();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!loading && !answerGroups.isEmpty());
    }

    private void showStatus(String message) {
        statusText.setText(message == null ? getString(R.string.unknown_error) : message);
        statusText.setVisibility(View.VISIBLE);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
