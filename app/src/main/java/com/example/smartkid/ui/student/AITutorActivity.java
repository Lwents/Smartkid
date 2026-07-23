package com.example.smartkid.ui.student;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.core.AppConstants;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.ui.common.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

/** Trò chuyện với AI Tutor qua API thật; không sinh câu trả lời mẫu trên thiết bị. */
public class AITutorActivity extends BaseActivity {
    private TextView conversationText;
    private TextView statusText;
    private TextInputEditText messageInput;
    private Button sendButton;
    private Button clearButton;
    private ProgressBar progressBar;
    private StudentFeatureRepository repository;
    private String lessonId;
    private String lessonTitle;
    private final StringBuilder conversation = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_ai_tutor);
            repository = new StudentFeatureRepository(this);
            lessonId = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(AppConstants.EXTRA_LESSON_ID));
            lessonTitle = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(AppConstants.EXTRA_LESSON_TITLE));
            bindViews();
            MaterialToolbar toolbar = findViewById(R.id.toolbarAiTutor);
            if (toolbar == null) throw new IllegalStateException("Thiếu thanh điều hướng AI Tutor");
            toolbar.setNavigationOnClickListener(view -> finish());
            if (!lessonTitle.isEmpty()) toolbar.setSubtitle(lessonTitle);
            sendButton.setOnClickListener(view -> sendSafely());
            clearButton.setOnClickListener(view -> clearSafely());
            conversation.append("AI Tutor chỉ hiển thị câu trả lời do server AI cung cấp.\n");
            bindConversation();
        } catch (Exception exception) {
            AppLogger.error(this, "AITutorActivity", "Không thể tạo AI Tutor", exception);
            showErrorDialog("Không thể mở AI Tutor");
        }
    }

    private void bindViews() {
        conversationText = findViewById(R.id.textAiConversation);
        statusText = findViewById(R.id.textAiStatus);
        messageInput = findViewById(R.id.inputAiMessage);
        sendButton = findViewById(R.id.buttonAiSend);
        clearButton = findViewById(R.id.buttonAiClear);
        progressBar = findViewById(R.id.progressAiTutor);
        if (conversationText == null || statusText == null || messageInput == null
                || sendButton == null || clearButton == null || progressBar == null) {
            throw new IllegalStateException("Giao diện AI Tutor thiếu thành phần bắt buộc");
        }
    }

    private void sendSafely() {
        try {
            String message = textOf(messageInput);
            if (message.isEmpty()) {
                showStatus("Bạn hãy nhập câu hỏi trước khi gửi");
                return;
            }
            if (message.length() > 1000) {
                showStatus("Câu hỏi tối đa 1000 ký tự");
                return;
            }
            appendLine("Bạn", message);
            messageInput.setText("");
            setLoading(true);
            repository.chatWithTutor(message, lessonId, lessonTitle,
                    new ApiCallback<String>() {
                        @Override
                        public void onSuccess(String answer) {
                            if (!isUsable()) return;
                            setLoading(false);
                            appendLine("AI Tutor", answer);
                            showStatus("Đã nhận câu trả lời từ server");
                        }

                        @Override
                        public void onError(ApiError error) {
                            if (!isUsable()) return;
                            setLoading(false);
                            showStatus(error == null ? getString(R.string.unknown_error)
                                    : error.getMessage());
                        }
                    });
        } catch (Exception exception) {
            AppLogger.error(this, "AITutorActivity", "Không thể gửi câu hỏi", exception);
            setLoading(false);
            showStatus("Không thể gửi câu hỏi tới server");
        }
    }

    private void clearSafely() {
        try {
            setLoading(true);
            repository.clearTutorHistory(lessonId, new ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean ignored) {
                    if (!isUsable()) return;
                    setLoading(false);
                    conversation.setLength(0);
                    conversation.append("Lịch sử hội thoại đã được xóa trên server.\n");
                    bindConversation();
                    showStatus("Đã xóa hội thoại");
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "AITutorActivity", "Không thể xóa hội thoại", exception);
            setLoading(false);
            showStatus("Không thể xóa hội thoại");
        }
    }

    private void appendLine(String speaker, String value) {
        if (conversation.length() > 0) conversation.append("\n");
        conversation.append(speaker).append(": ").append(safe(value)).append("\n");
        bindConversation();
    }

    private void bindConversation() {
        conversationText.setText(conversation.toString());
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!loading);
        clearButton.setEnabled(!loading);
        messageInput.setEnabled(!loading);
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
}
