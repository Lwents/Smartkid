package com.example.smartkid.ui.student;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Hỏi đáp theo bài học, đọc và cập nhật trực tiếp trên PostgreSQL qua API. */
public class LessonDiscussionActivity extends BaseActivity {
    private StudentFeatureRepository repository;
    private FeatureItemAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyText;
    private TextView statusText;
    private TextInputEditText questionInput;
    private View sendButton;
    private String lessonId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_lesson_discussion);
            lessonId = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(AppConstants.EXTRA_LESSON_ID));
            if (lessonId.isEmpty()) {
                showErrorDialog("Không tìm thấy mã bài học");
                finish();
                return;
            }
            repository = new StudentFeatureRepository(this);
            bindViews();
            MaterialToolbar toolbar = findViewById(R.id.toolbarLessonDiscussion);
            if (toolbar == null) throw new IllegalStateException("Thiếu thanh điều hướng hỏi đáp");
            toolbar.setNavigationOnClickListener(view -> finish());
            String title = getIntent().getStringExtra(AppConstants.EXTRA_LESSON_TITLE);
            if (title != null && !title.trim().isEmpty()) toolbar.setSubtitle(title.trim());
            ListView list = findViewById(R.id.listLessonQuestions);
            if (list == null) throw new IllegalStateException("Thiếu danh sách hỏi đáp");
            adapter = new FeatureItemAdapter(this);
            list.setAdapter(adapter);
            list.setEmptyView(emptyText);
            list.setOnItemClickListener((parent, row, position, id) ->
                    showQuestion(adapter.getItem(position)));
            sendButton.setOnClickListener(view -> createQuestionSafely());
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonDiscussionActivity", "Không thể tạo hỏi đáp", exception);
            showErrorDialog("Không thể mở hỏi đáp bài học");
        }
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressLessonDiscussion);
        emptyText = findViewById(R.id.textLessonDiscussionEmpty);
        statusText = findViewById(R.id.textLessonDiscussionStatus);
        questionInput = findViewById(R.id.inputLessonQuestion);
        sendButton = findViewById(R.id.buttonSendLessonQuestion);
        if (progressBar == null || emptyText == null || statusText == null
                || questionInput == null || sendButton == null) {
            throw new IllegalStateException("Giao diện hỏi đáp thiếu thành phần bắt buộc");
        }
    }

    private void loadSafely() {
        try {
            setLoading(true);
            repository.loadLessonQuestions(lessonId, new ApiCallback<List<FeatureItem>>() {
                @Override
                public void onSuccess(List<FeatureItem> data) {
                    if (!isUsable()) return;
                    setLoading(false);
                    adapter.setItems(data == null ? new ArrayList<>() : data);
                    showStatus(data == null || data.isEmpty()
                            ? "Chưa có câu hỏi nào cho bài học này" : "Đã tải hỏi đáp từ server");
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "LessonDiscussionActivity", "Không thể tải hỏi đáp", exception);
            setLoading(false);
            showStatus("Không thể tải hỏi đáp");
        }
    }

    private void createQuestionSafely() {
        String content = textOf(questionInput);
        if (content.isEmpty()) {
            showStatus("Bạn hãy nhập nội dung câu hỏi");
            return;
        }
        setLoading(true);
        repository.createLessonQuestion(lessonId, content, refreshCallback(
                "Đã gửi câu hỏi tới giáo viên", () -> questionInput.setText("")));
    }

    private void showQuestion(FeatureItem item) {
        if (item == null) return;
        try {
            JSONObject source = item.getSource();
            String message = item.getDetail() + formatReplies(SafeJson.array(source, "replies"));
            new AlertDialog.Builder(this)
                    .setTitle(item.getTitle())
                    .setMessage(message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton("Thao tác", (dialog, which) -> showActions(item))
                    .show();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonDiscussionActivity", "Không thể hiện câu hỏi", exception);
            showErrorDialog("Không thể đọc nội dung hỏi đáp");
        }
    }

    private String formatReplies(JSONArray replies) {
        if (replies == null || replies.length() == 0) return "\n\nChưa có phản hồi.";
        StringBuilder text = new StringBuilder("\n\nPhản hồi:");
        for (int index = 0; index < replies.length(); index++) {
            JSONObject reply = replies.optJSONObject(index);
            if (reply == null) continue;
            text.append("\n\n• ")
                    .append(SafeJson.string(reply, "Người dùng", "user"));
            if (SafeJson.bool(reply, false, "is_teacher")) text.append(" (Giáo viên)");
            text.append(": ").append(SafeJson.string(reply, "", "content"));
            int likes = SafeJson.integer(reply, 0, "reactions_count");
            if (likes > 0) text.append("  ♥ ").append(likes);
        }
        return text.toString();
    }

    private void showActions(FeatureItem item) {
        JSONObject source = item.getSource();
        boolean owner = SafeJson.bool(source, false, "is_owner");
        boolean reacted = SafeJson.bool(source, false, "reacted");
        List<String> labels = new ArrayList<>();
        labels.add("Trả lời");
        labels.add(reacted ? "Bỏ thích" : "Thích");
        if (owner) {
            labels.add("Sửa câu hỏi");
            labels.add("Xóa câu hỏi");
        } else {
            labels.add("Báo cáo nội dung");
        }
        String[] actions = labels.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Chọn thao tác")
                .setItems(actions, (dialog, which) -> performAction(item, actions[which]))
                .show();
    }

    private void performAction(FeatureItem item, String action) {
        if ("Trả lời".equals(action)) {
            showTextDialog("Nhập phản hồi", "Gửi", "", value ->
                    repository.replyLessonQuestion(item.getId(), value,
                            refreshCallback("Đã gửi phản hồi", null)));
        } else if ("Thích".equals(action) || "Bỏ thích".equals(action)) {
            setLoading(true);
            repository.reactLessonQuestion(item.getId(),
                    refreshCallback("Đã cập nhật lượt thích", null));
        } else if ("Sửa câu hỏi".equals(action)) {
            showTextDialog("Sửa câu hỏi", R.string.save, item.getDetail(), value ->
                    repository.editLessonQuestion(item.getId(), value,
                            refreshCallback("Đã sửa câu hỏi", null)));
        } else if ("Xóa câu hỏi".equals(action)) {
            confirmDelete(item);
        } else if ("Báo cáo nội dung".equals(action)) {
            showTextDialog("Chi tiết báo cáo", "Gửi báo cáo", "", value ->
                    repository.reportLessonQuestion(item.getId(), value,
                            refreshCallback("Đã gửi báo cáo tới quản trị viên", null)));
        }
    }

    private void confirmDelete(FeatureItem item) {
        new AlertDialog.Builder(this).setTitle("Xóa câu hỏi")
                .setMessage("Câu hỏi và các phản hồi liên quan sẽ bị xóa trên server.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Xóa", (dialog, which) -> {
                    setLoading(true);
                    repository.deleteLessonQuestion(item.getId(),
                            refreshCallback("Đã xóa câu hỏi", null));
                }).show();
    }

    private void showTextDialog(String title, Object positiveLabel, String initial,
                                TextAction action) {
        try {
            EditText input = new EditText(this);
            input.setText(initial == null ? "" : initial);
            input.setSelection(input.length());
            input.setMaxLines(5);
            input.setPadding(40, 24, 40, 24);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title)
                    .setView(input).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(positiveLabel instanceof Integer
                                    ? getString((Integer) positiveLabel) : String.valueOf(positiveLabel),
                            null).create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String value = input.getText() == null ? ""
                                : input.getText().toString().trim();
                        if (value.isEmpty()) {
                            input.setError("Nội dung không được để trống");
                            return;
                        }
                        dialog.dismiss();
                        setLoading(true);
                        try { action.run(value); }
                        catch (Exception exception) {
                            AppLogger.error(this, "LessonDiscussionActivity",
                                    "Không thể thực hiện thao tác hỏi đáp", exception);
                            setLoading(false);
                            showStatus("Không thể chuẩn bị dữ liệu hỏi đáp");
                        }
                    }));
            dialog.show();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonDiscussionActivity", "Không thể mở hộp nhập", exception);
            showErrorDialog("Không thể mở biểu mẫu nhập liệu");
        }
    }

    private ApiCallback<Boolean> refreshCallback(String message, Runnable beforeRefresh) {
        return new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean ignored) {
                if (!isUsable()) return;
                try {
                    if (beforeRefresh != null) beforeRefresh.run();
                    showShortMessage(message);
                    loadSafely();
                } catch (Exception exception) {
                    AppLogger.error(LessonDiscussionActivity.this,
                            "LessonDiscussionActivity", "Không thể làm mới hỏi đáp", exception);
                    setLoading(false);
                }
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        };
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!loading);
        questionInput.setEnabled(!loading);
    }

    private void showStatus(String message) {
        statusText.setText(safe(message));
        statusText.setVisibility(View.VISIBLE);
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private interface TextAction { void run(String value); }
}
